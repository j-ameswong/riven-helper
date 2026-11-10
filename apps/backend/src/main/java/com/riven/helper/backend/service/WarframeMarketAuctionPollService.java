package com.riven.helper.backend.service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.riven.helper.backend.dto.RivenAuctionDTO;
import com.riven.helper.backend.dto.V1AuctionsDTO;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Archives new riven auctions from /v1/auctions. That feed usually holds the ~10
 * minutes of newest listings, repeats are absorbed, and a detected update within
 * that window adds a riven_snapshot. Later edits, sales and removals need the
 * per-weapon crawl.
 */
@Service
public class WarframeMarketAuctionPollService {

	private static final Logger log = LoggerFactory.getLogger(WarframeMarketAuctionPollService.class);

	private final WarframeMarketRestClient client;

	private final JdbcClient jdbc;

	private final TransactionTemplate transaction;

	private final JsonMapper mapper;

	WarframeMarketAuctionPollService(WarframeMarketRestClient client, JdbcClient jdbc,
			TransactionTemplate transaction, JsonMapper mapper) {
		this.client = client;
		this.jdbc = jdbc;
		this.transaction = transaction;
		this.mapper = mapper;
	}

	/**
	 * @param rivens riven auctions read from the feed, new or not
	 * @param gap true if the newest existing auction is older than
     * the oldest from the latest polled auction
	 */
	public record PollResult(int rivens, boolean gap) {
	}

	private record Riven(RivenAuctionDTO auction, JsonNode raw) {
	}

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	void scheduledPoll() {
		poll();
	}

	public synchronized PollResult poll() {
		V1AuctionsDTO response = client.getAuctions();
		if (response == null) {
			throw new IllegalStateException("warframe.market returned an empty body for /v1/auctions");
		}
		List<Riven> rivens = response.payload().auctions().stream()
				.filter(node -> "riven".equals(node.path("item").path("type").asString()))
				.flatMap(node -> read(node).stream())
				.toList();
		if (rivens.isEmpty()) {
			log.warn("/v1/auctions returned no riven auctions");
			return new PollResult(0, false);
		}

		OffsetDateTime oldest = rivens.stream().map(r -> r.auction().created())
				.min(Comparator.naturalOrder()).orElseThrow();
		Optional<OffsetDateTime> newestKnown = jdbc.sql("SELECT max(created_at) FROM riven_auctions")
				.query(OffsetDateTime.class).optional();
		boolean gap = newestKnown.filter(oldest::isAfter).isPresent();
		if (gap) {
			log.warn("Possible gap in the riven archive: auctions created between {} and {} were missed",
					oldest, newestKnown.get());
		}

		transaction.executeWithoutResult(status -> rivens.forEach(this::save));
		log.debug("Polled {} riven auctions", rivens.size());
		return new PollResult(rivens.size(), gap);
	}

	// Skip and return empty if a node is malformed
	private Optional<Riven> read(JsonNode node) {
		try {
			return Optional.of(new Riven(mapper.treeToValue(node, RivenAuctionDTO.class), node));
		}
		catch (JacksonException ex) {
			log.warn("Skipping malformed riven auction {}: {}", node.path("id"), ex.getOriginalMessage());
			return Optional.empty();
		}
	}

	private void save(Riven riven) {
		RivenAuctionDTO auction = riven.auction();
		RivenAuctionDTO.Item item = auction.item();
		jdbc.sql("""
				INSERT INTO riven_auctions (auction_id, weapon_slug, owner_id, created_at, last_updated_at)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (auction_id) DO UPDATE
				SET last_updated_at = GREATEST(riven_auctions.last_updated_at, excluded.last_updated_at)
				""")
				.params(auction.id(), item.weaponUrlName(), auction.owner().id(), auction.created(), auction.updated())
				.update();
		jdbc.sql("""
				INSERT INTO riven_snapshots (auction_id, updated_at, starting_price, buyout_price, is_direct_sell,
					re_rolls, mod_rank, mastery_level, polarity, attributes, raw)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
				ON CONFLICT (auction_id, updated_at) DO NOTHING
				""")
				// Arrays.asList as NullAway rejects nullable vararg buyoutPrice
				.params(Arrays.asList(auction.id(), auction.updated(), auction.startingPrice(), auction.buyoutPrice(),
						auction.isDirectSell(), item.reRolls(), item.modRank(), item.masteryLevel(), item.polarity(),
						riven.raw().path("item").path("attributes").toString(), riven.raw().toString()))
				.update();
	}

}
