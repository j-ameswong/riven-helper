package com.riven.helper.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import tools.jackson.databind.JsonNode;

/**
 * The v1 envelope of /v1/auctions and /v1/auctions/search. Auctions stay raw
 * JSON because lich and sister items are mixed in with their own shapes, and
 * the archive keeps each auction as sent; read rivens with
 * {@link RivenAuctionDTO}. See docs/warframe-market-api.md.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record V1AuctionsDTO(@JsonSetter(nulls = Nulls.FAIL) Payload payload) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Payload(@JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL) List<JsonNode> auctions) {
	}

}
