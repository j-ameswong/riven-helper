package com.riven.helper.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * A riven auction object from v1 (snake_case), with only the fields the archive
 * stores as columns; the rest is kept in the raw JSON. See "Auction object" in
 * docs/warframe-market-api.md.
 *
 * @param buyoutPrice null for a pure auction with no buyout
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RivenAuctionDTO(
		@JsonSetter(nulls = Nulls.FAIL) String id,
		int startingPrice,
		@Nullable Integer buyoutPrice,
		boolean isDirectSell,
		@JsonSetter(nulls = Nulls.FAIL) OffsetDateTime created,
		@JsonSetter(nulls = Nulls.FAIL) OffsetDateTime updated,
		@JsonSetter(nulls = Nulls.FAIL) Owner owner,
		@JsonSetter(nulls = Nulls.FAIL) Item item) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Owner(@JsonSetter(nulls = Nulls.FAIL) String id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Item(
			@JsonSetter(nulls = Nulls.FAIL) String weaponUrlName,
			@JsonSetter(nulls = Nulls.FAIL) String polarity,
			int modRank,
			int masteryLevel,
			int reRolls,
			@JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL) List<Attribute> attributes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Attribute(@JsonSetter(nulls = Nulls.FAIL) String urlName, double value, boolean positive) {
	}

}
