package com.riven.helper.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.riven.helper.backend.domain.RivenSnapshot;

// Repository, not JpaRepository: save() would skip the upsert rules below
public interface RivenSnapshotRepository extends Repository<RivenSnapshot, RivenSnapshot.Key> {

	Optional<RivenSnapshot> findById(RivenSnapshot.Key id);

	/** Inserts the snapshot unless that version of the auction is already stored. */
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO riven_snapshots (auction_id, updated_at, starting_price, buyout_price, is_direct_sell,
				re_rolls, mod_rank, mastery_level, polarity, attributes, raw)
			VALUES (:#{#s.auctionId}, :#{#s.updatedAt}, :#{#s.startingPrice}, CAST(:#{#s.buyoutPrice} AS integer),
				:#{#s.directSell}, :#{#s.reRolls}, :#{#s.modRank}, :#{#s.masteryLevel}, :#{#s.polarity},
				CAST(:#{#s.attributes} AS jsonb), CAST(:#{#s.raw} AS jsonb))
			ON CONFLICT (auction_id, updated_at) DO NOTHING
			""")
	void insertIfAbsent(@Param("s") RivenSnapshot snapshot);

}
