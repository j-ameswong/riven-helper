package com.riven.helper.backend.ingest;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.riven.helper.backend.auction.RivenSnapshot;
import com.riven.helper.backend.warframemarket.MapperConfiguration;
import com.riven.helper.backend.warframemarket.RivenAuctionDTO;

import tools.jackson.databind.JsonNode;

@Mapper(config = MapperConfiguration.class)
public interface RivenSnapshotMapper {

	/**
	 * @param raw the auction object as sent, which {@code dto} was read from; it's
	 *        stored whole
	 */
	@Mapping(target = "auctionId", source = "dto.id")
	@Mapping(target = "updatedAt", source = "dto.updated")
	@Mapping(target = "startingPrice", source = "dto.startingPrice")
	@Mapping(target = "buyoutPrice", source = "dto.buyoutPrice")
	@Mapping(target = "directSell", source = "dto.isDirectSell")
	@Mapping(target = "raw", source = "raw", qualifiedByName = "json")
	RivenSnapshot toEntity(RivenAuctionDTO dto, JsonNode raw);

	@Named("json")
	default String json(JsonNode raw) {
		return raw.toString();
	}

}
