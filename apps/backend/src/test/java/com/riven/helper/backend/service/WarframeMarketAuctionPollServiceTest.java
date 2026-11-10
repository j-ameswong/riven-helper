package com.riven.helper.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.riven.helper.backend.TestcontainersConfiguration;
import com.riven.helper.backend.dto.V1AuctionsDTO;

import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the poll against the real schema with a mocked API, using auctions
 * shaped like "Auction object" in docs/warframe-market-api.md.
 */
@SpringBootTest(properties = "riven-helper.scheduling.enabled=false")
@Import(TestcontainersConfiguration.class)
class WarframeMarketAuctionPollServiceTest {

	@MockitoBean
	WarframeMarketRestClient client;

	@Autowired
	WarframeMarketAuctionPollService pollService;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JsonMapper mapper;

	@BeforeEach
	void clearTables() {
		jdbc.sql("DELETE FROM riven_snapshots").update();
		jdbc.sql("DELETE FROM riven_auctions").update();
	}

	@Test
	void archivesRivensAndSkipsOtherAuctions() {
		givenFeed(riven("a1", "10:00", "10:00", "200"), """
				{ "id": "lich1", "item": { "type": "lich", "weapon_url_name": "kuva_bramma" } }
				""");

		assertThat(pollService.poll()).isEqualTo(new WarframeMarketAuctionPollService.PollResult(1, false));

		assertThat(jdbc.sql("SELECT weapon_slug, owner_id FROM riven_auctions WHERE auction_id = 'a1'")
				.query().singleRow()).isEqualTo(Map.of("weapon_slug", "rubico", "owner_id", "owner-a1"));
		assertThat(jdbc.sql("""
				SELECT buyout_price, polarity, attributes->0->>'url_name' AS first_stat, raw->'owner'->>'ingame_name' AS seller
				FROM riven_snapshots WHERE auction_id = 'a1'
				""").query().singleRow())
				.isEqualTo(Map.of("buyout_price", 200, "polarity", "madurai", "first_stat", "critical_chance",
						"seller", "Seller"));
		assertThat(count("riven_auctions")).isOne();
	}

	@Test
	void repeatedPollAddsNothingAndAnEditAddsASnapshot() {
		givenFeed(riven("a1", "10:00", "10:00", "200"));
		pollService.poll();
		pollService.poll();
		assertThat(count("riven_snapshots")).isOne();

		givenFeed(riven("a1", "10:00", "10:05", "150"));
		pollService.poll();

		assertThat(count("riven_snapshots")).isEqualTo(2);
		assertThat(jdbc.sql("SELECT last_updated_at FROM riven_auctions").query(OffsetDateTime.class).single())
				.isEqualTo(time("10:05"));
	}

	@Test
	void keepsPureAuctionsWithNoBuyout() {
		givenFeed(riven("a1", "10:00", "10:00", "null"));
		pollService.poll();

		assertThat(count("riven_snapshots WHERE buyout_price IS NULL")).isOne();
	}

	@Test
	void skipsAMalformedRivenButKeepsTheRest() {
		givenFeed(riven("a1", "10:00", "10:00", "200"), """
				{ "id": "bad", "item": { "type": "riven" } }
				""");

		assertThat(pollService.poll().rivens()).isOne();
		assertThat(count("riven_auctions")).isOne();
	}

	@Test
	void flagsAGapOnlyWhenTheFeedNoLongerOverlapsTheArchive() {
		givenFeed(riven("a1", "10:00", "10:00", "200"));
		assertThat(pollService.poll().gap()).isFalse();

		givenFeed(riven("a2", "10:20", "10:20", "200"), riven("a1", "10:00", "10:00", "200"));
		assertThat(pollService.poll().gap()).isFalse();

		givenFeed(riven("a3", "10:40", "10:40", "200"));
		assertThat(pollService.poll().gap()).isTrue();
	}

	private void givenFeed(String... auctions) {
		when(client.getAuctions()).thenReturn(mapper.readValue("""
				{ "payload": { "auctions": [ %s ] } }
				""".formatted(String.join(",", auctions)), V1AuctionsDTO.class));
	}

	private long count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

	private static OffsetDateTime time(String hhmm) {
		return OffsetDateTime.parse("2026-11-10T" + hhmm + ":00.000+00:00");
	}

	private static String riven(String id, String created, String updated, String buyout) {
		return """
				{
				  "id": "%s", "starting_price": 100, "buyout_price": %s, "top_bid": null, "is_direct_sell": false,
				  "created": "%s", "updated": "%s", "visible": true,
				  "owner": { "id": "owner-%s", "ingame_name": "Seller", "status": "offline" },
				  "item": {
				    "type": "riven", "weapon_url_name": "rubico", "name": "crita-satisco", "polarity": "madurai",
				    "mod_rank": 0, "mastery_level": 12, "re_rolls": 3,
				    "attributes": [
				      { "url_name": "critical_chance", "value": 120.5, "positive": true },
				      { "url_name": "zoom", "value": -58.3, "positive": false }
				    ]
				  }
				}
				""".formatted(id, buyout, time(created), time(updated), id);
	}

}
