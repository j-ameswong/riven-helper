package com.riven.helper.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.riven.helper.backend.domain.RivenAttribute;

// Repository, not JpaRepository: save() would skip the insert rule below
public interface RivenAttributeRepository extends Repository<RivenAttribute, RivenAttribute.Key> {

	List<RivenAttribute> findByAuctionId(String auctionId);

	/** Inserts the stat unless the auction's riven already has it, as a riven can't be edited. */
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO riven_attributes (auction_id, attribute_id, value, positive)
			VALUES (:#{#a.auctionId}, :#{#a.attributeId}, :#{#a.value}, :#{#a.positive})
			ON CONFLICT (auction_id, attribute_id) DO NOTHING
			""")
	void insertIfAbsent(@Param("a") RivenAttribute attribute);

}
