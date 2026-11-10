package com.riven.helper.backend.repository;

import java.time.OffsetDateTime;
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
	 * forward. In one statement, unlike {@code save}, so concurrent writers can't
	 * collide, and a stale read never moves it backwards.
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO riven_auctions (auction_id, weapon_id, owner_id, created_at, last_updated_at)
			VALUES (:#{#a.auctionId}, :#{#a.weaponId}, :#{#a.ownerId}, :#{#a.createdAt}, :#{#a.lastUpdatedAt})
			ON CONFLICT (auction_id) DO UPDATE
			SET last_updated_at = GREATEST(riven_auctions.last_updated_at, excluded.last_updated_at)
			""")
	void upsert(@Param("a") RivenAuction auction);

	@Query("SELECT max(a.createdAt) FROM RivenAuction a")
	Optional<OffsetDateTime> findNewestCreatedAt();

}
