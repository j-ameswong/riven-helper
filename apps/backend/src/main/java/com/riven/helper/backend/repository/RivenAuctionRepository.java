package com.riven.helper.backend.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.riven.helper.backend.domain.RivenAuction;

// Repository, not JpaRepository: save() would skip the upsert rules below
public interface RivenAuctionRepository extends Repository<RivenAuction, String> {

	Optional<RivenAuction> findById(String id);

	/**
	 * Inserts the auction, or moves an existing one's {@code last_updated_at}
	 * forward and clears its {@code removed_at}, as it was just seen. The riven
	 * can't be edited, so an existing one's riven fields are kept. In one
	 * statement, unlike {@code save}, so concurrent writers can't collide, and a
	 * stale read never moves it backwards.
	 *
	 * @return 0 if the auction was already stored as seen, so nothing was written
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO riven_auctions (auction_id, weapon_id, owner_id, created_at, last_updated_at,
				re_rolls, mod_rank, mastery_level, polarity)
			VALUES (:#{#a.auctionId}, :#{#a.weaponId}, :#{#a.ownerId}, :#{#a.createdAt}, :#{#a.lastUpdatedAt},
				:#{#a.reRolls}, :#{#a.modRank}, :#{#a.masteryLevel}, :#{#a.polarity})
			ON CONFLICT (auction_id) DO UPDATE
			SET last_updated_at = GREATEST(riven_auctions.last_updated_at, excluded.last_updated_at),
				removed_at = NULL
			-- A stale read still counts as a change, so its version gets a snapshot
			WHERE riven_auctions.last_updated_at <> excluded.last_updated_at OR riven_auctions.removed_at IS NOT NULL
			""")
	int upsert(@Param("a") RivenAuction auction);

	/**
	 * Marks the weapon's auctions that a crawl started at {@code crawlStartedAt}
	 * didn't return as removed. Auctions created since then are left alone, as
	 * the crawl may have read their page before they existed.
	 *
	 * @param seen the ids the crawl returned; must not be empty
	 * @return how many auctions were marked
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			UPDATE riven_auctions SET removed_at = :crawlStartedAt
			WHERE weapon_id = :weaponId AND removed_at IS NULL AND created_at < :crawlStartedAt
				AND auction_id NOT IN (:seen)
			""")
	int markRemoved(@Param("weaponId") long weaponId, @Param("seen") Collection<String> seen,
			@Param("crawlStartedAt") OffsetDateTime crawlStartedAt);

	@Query("SELECT max(a.createdAt) FROM RivenAuction a")
	Optional<OffsetDateTime> findNewestCreatedAt();

	/** Counts the weapon's live auctions by their riven's search filters. */
	@Query("""
			SELECT new com.riven.helper.backend.repository.ListingCount(a.masteryLevel, a.reRolls, a.polarity, count(a))
			FROM RivenAuction a
			WHERE a.weaponId = :weaponId AND a.removedAt IS NULL
			GROUP BY a.masteryLevel, a.reRolls, a.polarity
			""")
	List<ListingCount> countLive(@Param("weaponId") long weaponId);

}
