package com.riven.helper.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.dto.RivenAuctionDTO;

import tools.jackson.databind.JsonNode;

@Mapper(config = MapperConfiguration.class)
public interface RivenSnapshotMapper {

	/**
	 * @param raw the auction object as sent, which {@code dto} was read from; it's
	 *        stored whole, along with its {@code item.attributes}
	 */
	@Mapping(target = "auctionId", source = "dto.id")
	@Mapping(target = "updatedAt", source = "dto.updated")
	@Mapping(target = "startingPrice", source = "dto.startingPrice")
	@Mapping(target = "buyoutPrice", source = "dto.buyoutPrice")
	@Mapping(target = "directSell", source = "dto.isDirectSell")
	@Mapping(target = "reRolls", source = "dto.item.reRolls")
	@Mapping(target = "modRank", source = "dto.item.modRank")
	@Mapping(target = "masteryLevel", source = "dto.item.masteryLevel")
	@Mapping(target = "polarity", source = "dto.item.polarity")
	@Mapping(target = "attributes", source = "raw", qualifiedByName = "attributesJson")
	@Mapping(target = "raw", source = "raw", qualifiedByName = "json")
	RivenSnapshot toEntity(RivenAuctionDTO dto, JsonNode raw);

	@Named("attributesJson")
	default String attributesJson(JsonNode raw) {
		return raw.path("item").path("attributes").toString();
	}

	@Named("json")
	default String json(JsonNode raw) {
		return raw.toString();
	}

}
