package com.riven.helper.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.riven.helper.backend.domain.RivenAuction;
import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.dto.RivenAuctionDTO;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Maps an auction shaped like "Auction object" in docs/warframe-market-api.md. */
class RivenMapperTest {

	private static final JsonNode RAW = JsonMapper.builder().build().readTree("""
			{
			  "id": "a1", "starting_price": 100, "buyout_price": null, "is_direct_sell": false,
			  "created": "2026-11-10T10:00:00.000+00:00", "updated": "2026-11-10T10:05:00.000+00:00",
			  "owner": { "id": "owner-1", "ingame_name": "Seller" },
			  "item": {
			    "type": "riven", "weapon_url_name": "rubico", "polarity": "madurai",
			    "mod_rank": 8, "mastery_level": 12, "re_rolls": 3,
			    "attributes": [ { "url_name": "zoom", "value": -58.3, "positive": false } ]
			  }
			}
			""");

	private static final RivenAuctionDTO DTO = JsonMapper.builder().build().treeToValue(RAW, RivenAuctionDTO.class);

	@Test
	void mapsTheAuctionWithTheResolvedWeaponId() {
		RivenAuction auction = new RivenAuctionMapper().toEntity(DTO, 42);

		assertThat(auction.getAuctionId()).isEqualTo("a1");
		assertThat(auction.getWeaponId()).isEqualTo(42);
		assertThat(auction.getOwnerId()).isEqualTo("owner-1");
		assertThat(auction.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-11-10T10:00:00Z"));
		assertThat(auction.getLastUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-11-10T10:05:00Z"));
		assertThat(auction.getRemovedAt()).isNull();
	}

	@Test
	void mapsTheSnapshotKeepingTheRawJson() {
		RivenSnapshot snapshot = new RivenSnapshotMapper().toEntity(DTO, RAW);

		assertThat(snapshot.getAuctionId()).isEqualTo("a1");
		assertThat(snapshot.getUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-11-10T10:05:00Z"));
		assertThat(snapshot.getStartingPrice()).isEqualTo(100);
		assertThat(snapshot.getBuyoutPrice()).isNull();
		assertThat(snapshot.getModRank()).isEqualTo((short) 8);
		assertThat(snapshot.getMasteryLevel()).isEqualTo((short) 12);
		assertThat(snapshot.getReRolls()).isEqualTo(3);
		assertThat(snapshot.getPolarity()).isEqualTo("madurai");
		assertThat(snapshot.getAttributes()).isEqualTo(RAW.path("item").path("attributes").toString());
		assertThat(snapshot.getRaw()).isEqualTo(RAW.toString());
	}

}
