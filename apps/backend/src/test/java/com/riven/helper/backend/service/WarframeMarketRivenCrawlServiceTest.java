package com.riven.helper.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.riven.helper.backend.TestcontainersConfiguration;
import com.riven.helper.backend.dto.V1AuctionsDTO;
import com.riven.helper.backend.service.WarframeMarketRivenCrawlService.CrawlResult;

import tools.jackson.databind.json.JsonMapper;

/** Runs the crawl against the real schema with a mocked API. */
@SpringBootTest(properties = "riven-helper.scheduling.enabled=false")
@Import(TestcontainersConfiguration.class)
class WarframeMarketRivenCrawlServiceTest {

	@MockitoBean
	WarframeMarketRestClient client;

	@Autowired
	WarframeMarketRivenCrawlService crawlService;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JsonMapper mapper;

	// Makes rubico the only weapon due, whatever other test classes left behind
	@BeforeEach
	void givenRubicoIsNext() {
		jdbc.sql("""
				INSERT INTO weapons (api_weapon_id, slug, game_ref, weapon_group, riven_type, req_mastery_rank, name)
				VALUES ('api-rubico', 'rubico', '/Lotus/x', 'primary', 'rifle', 4, 'rubico')
				ON CONFLICT DO NOTHING
				""").update();
		jdbc.sql("UPDATE weapons SET last_crawled_at = CASE WHEN slug = 'rubico' THEN NULL ELSE now() END").update();
	}

	@AfterEach
	void clearTables() {
		jdbc.sql("DELETE FROM riven_snapshots").update();
		jdbc.sql("DELETE FROM riven_auctions").update();
		jdbc.sql("UPDATE weapons SET last_crawled_at = NULL").update();
	}

	@Test
	void archivesTheBookAndSendsTheWeaponToTheBackOfTheQueue() {
		givenBook(riven("a1", "2026-11-10T10:00"), riven("a2", "2026-11-10T10:00"));

		assertThat(crawlService.crawlNext()).contains(new CrawlResult("rubico", 2, 0, true));

		assertThat(count("riven_snapshots")).isEqualTo(2);
		assertThat(count("weapons WHERE slug = 'rubico' AND last_crawled_at IS NOT NULL")).isOne();
	}

	@Test
	void marksAuctionsMissingFromTheBookAsRemovedAndRestoresThemWhenSeenAgain() {
		givenBook(riven("a1", "2026-11-10T10:00"), riven("a2", "2026-11-10T10:00"));
		crawlService.crawlNext();

		givenRubicoIsNext();
		givenBook(riven("a1", "2026-11-10T10:00"));
		assertThat(crawlService.crawlNext()).map(CrawlResult::removed).contains(1);
		assertThat(removed()).isEqualTo(Optional.of("a2"));

		givenRubicoIsNext();
		givenBook(riven("a1", "2026-11-10T10:00"), riven("a2", "2026-11-10T10:00"));
		crawlService.crawlNext();
		assertThat(removed()).isEmpty();
	}

	@Test
	void keepsAuctionsCreatedAfterTheCrawlStarted() {
		givenBook(riven("a1", "2026-11-10T10:00"), riven("new", OffsetDateTime.now().plusMinutes(1).toString()));
		crawlService.crawlNext();

		givenRubicoIsNext();
		givenBook(riven("a1", "2026-11-10T10:00"));
		crawlService.crawlNext();

		assertThat(removed()).isEmpty();
	}

	@Test
	void marksNothingRemovedWhenTheBookComesBackEmpty() {
		givenBook(riven("a1", "2026-11-10T10:00"));
		crawlService.crawlNext();

		givenRubicoIsNext();
		givenBook();
		crawlService.crawlNext();

		assertThat(removed()).isEmpty();
	}

	@Test
	void marksNothingRemovedButMovesOnWhenARequestFails() {
		givenBook(riven("a1", "2026-11-10T10:00"));
		crawlService.crawlNext();

		givenRubicoIsNext();
		when(client.searchRivenAuctions(anyMap())).thenThrow(new IllegalStateException("429"));
		assertThat(crawlService.crawlNext()).contains(new CrawlResult("rubico", 0, 0, false));

		assertThat(removed()).isEmpty();
		assertThat(count("weapons WHERE slug = 'rubico' AND last_crawled_at IS NOT NULL")).isOne();
	}

	private void givenBook(String... auctions) {
		when(client.searchRivenAuctions(anyMap())).thenReturn(mapper.readValue("""
				{ "payload": { "auctions": [ %s ] } }
				""".formatted(String.join(",", auctions)), V1AuctionsDTO.class));
	}

	private Optional<String> removed() {
		return jdbc.sql("SELECT auction_id FROM riven_auctions WHERE removed_at IS NOT NULL").query(String.class)
				.optional();
	}

	private long count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

	private static String riven(String id, String created) {
		return """
				{
				  "id": "%s", "starting_price": 100, "buyout_price": 200, "is_direct_sell": false,
				  "created": "%s", "updated": "%s",
				  "owner": { "id": "owner-%s" },
				  "item": {
				    "type": "riven", "weapon_url_name": "rubico", "polarity": "madurai",
				    "mod_rank": 0, "mastery_level": 12, "re_rolls": 3,
				    "attributes": [ { "url_name": "zoom", "value": -58.3, "positive": false } ]
				  }
				}
				""".formatted(id, timestamp(created), timestamp(created), id);
	}

	private static String timestamp(String time) {
		return time.length() == 16 ? time + ":00.000+00:00" : time;
	}

}
