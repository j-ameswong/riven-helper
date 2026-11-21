package com.riven.helper.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.riven.helper.backend.domain.RivenAttribute;
import com.riven.helper.backend.domain.RivenAuction;
import com.riven.helper.backend.dto.RivenAuctionDTO;

@Mapper(config = MapperConfiguration.class)
public interface RivenAuctionMapper {

	/**
	 * @param weaponId the id of the weapon {@code dto}'s {@code weapon_url_name}
	 *        names, which the DTO alone can't provide
	 */
	@Mapping(target = "auctionId", source = "dto.id")
	@Mapping(target = "weaponId", source = "weaponId")
	@Mapping(target = "ownerId", source = "dto.owner.id")
	@Mapping(target = "createdAt", source = "dto.created")
	@Mapping(target = "lastUpdatedAt", source = "dto.updated")
	@Mapping(target = "removedAt", ignore = true)
	@Mapping(target = "reRolls", source = "dto.item.reRolls")
	@Mapping(target = "modRank", source = "dto.item.modRank")
	@Mapping(target = "masteryLevel", source = "dto.item.masteryLevel")
	@Mapping(target = "polarity", source = "dto.item.polarity")
	RivenAuction toEntity(RivenAuctionDTO dto, long weaponId);

	/**
	 * @param attributeId the id of the attribute {@code dto}'s {@code url_name}
	 *        names, which the DTO alone can't provide
	 */
	@Mapping(target = "auctionId", source = "auctionId")
	@Mapping(target = "attributeId", source = "attributeId")
	@Mapping(target = "value", source = "dto.value")
	@Mapping(target = "positive", source = "dto.positive")
	RivenAttribute toEntity(String auctionId, long attributeId, RivenAuctionDTO.Attribute dto);

}
