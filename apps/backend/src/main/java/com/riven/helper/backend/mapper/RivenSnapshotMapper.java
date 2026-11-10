package com.riven.helper.backend.mapper;

import org.springframework.stereotype.Component;

import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.dto.RivenAuctionDTO;

import tools.jackson.databind.JsonNode;

@Component
public class RivenSnapshotMapper {

	/**
	 * @param raw the auction object as sent, which {@code dto} was read from; it's
	 *        stored whole, along with its {@code item.attributes}
	 */
	public RivenSnapshot toEntity(RivenAuctionDTO dto, JsonNode raw) {
		RivenAuctionDTO.Item item = dto.item();
		RivenSnapshot snapshot = new RivenSnapshot();
		snapshot.setAuctionId(dto.id());
		snapshot.setUpdatedAt(dto.updated());
		snapshot.setStartingPrice(dto.startingPrice());
		snapshot.setBuyoutPrice(dto.buyoutPrice());
		snapshot.setDirectSell(dto.isDirectSell());
		snapshot.setReRolls(item.reRolls());
		snapshot.setModRank((short) item.modRank());
		snapshot.setMasteryLevel((short) item.masteryLevel());
		snapshot.setPolarity(item.polarity());
		snapshot.setAttributes(raw.path("item").path("attributes").toString());
		snapshot.setRaw(raw.toString());
		return snapshot;
	}

}
