package com.riven.helper.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
	RivenAuction toEntity(RivenAuctionDTO dto, long weaponId);

}
