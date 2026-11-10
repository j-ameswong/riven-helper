package com.riven.helper.backend.mapper;

import org.springframework.stereotype.Component;

import com.riven.helper.backend.domain.RivenAuction;
import com.riven.helper.backend.dto.RivenAuctionDTO;

@Component
public class RivenAuctionMapper {

	/**
	 * @param weaponId the id of the weapon {@code dto}'s {@code weapon_url_name}
	 *        names, which the DTO alone can't provide
	 */
	public RivenAuction toEntity(RivenAuctionDTO dto, long weaponId) {
		RivenAuction auction = new RivenAuction();
		auction.setAuctionId(dto.id());
		auction.setWeaponId(weaponId);
		auction.setOwnerId(dto.owner().id());
		auction.setCreatedAt(dto.created());
		auction.setLastUpdatedAt(dto.updated());
		return auction;
	}

}
