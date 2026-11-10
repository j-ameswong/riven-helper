package com.riven.helper.backend.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

	private final JsonMapper mapper;

	private final WarframeMarketRivenSyncService syncService;

	private final WeaponService weaponService;

	private final RivenAuctionService auctionService;

	WarframeMarketAuctionPollService(WarframeMarketRestClient client, JsonMapper mapper,
			WarframeMarketRivenSyncService syncService, WeaponService weaponService,
			RivenAuctionService auctionService) {
		this.client = client;
		this.mapper = mapper;
		this.syncService = syncService;
		this.weaponService = weaponService;
		this.auctionService = auctionService;
	}

	/**
	 * @param rivens riven auctions read from the feed, new or not
	 * @param gap true if the newest existing auction is older than
     * the oldest from the latest polled auction
	 */
	public record PollResult(int rivens, boolean gap) {
	}

	private record Riven(RivenAuctionDTO auction, long weaponId, JsonNode raw) {
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
		List<JsonNode> rivenNodes = response.payload().auctions().stream()
				.filter(node -> "riven".equals(node.path("item").path("type").asString()))
				.toList();
		Map<String, Long> weaponIds = weaponIds(rivenNodes);
		List<Riven> rivens = rivenNodes.stream().flatMap(node -> read(node, weaponIds).stream()).toList();
		if (rivens.isEmpty()) {
			log.warn("/v1/auctions returned no riven auctions");
			return new PollResult(0, false);
		}

		OffsetDateTime oldest = rivens.stream().map(r -> r.auction().created())
				.min(Comparator.naturalOrder()).orElseThrow();
		Optional<OffsetDateTime> newestKnown = auctionService.findNewestCreatedAt();
		boolean gap = newestKnown.filter(oldest::isAfter).isPresent();
		if (gap) {
			log.warn("Possible gap in the riven archive: auctions created between {} and {} were missed",
					newestKnown.get(), oldest);
		}

		rivens.forEach(riven -> auctionService.archive(riven.auction(), riven.weaponId(), riven.raw()));
		log.debug("Polled {} riven auctions", rivens.size());
		return new PollResult(rivens.size(), gap);
	}

	// A weapon newer than the hourly manifest sync would otherwise lose its first
	// listings for good, as they only stay in the feed for ~10 minutes
	private Map<String, Long> weaponIds(List<JsonNode> rivenNodes) {
		Map<String, Long> weaponIds = weaponService.findIdsBySlug();
		if (rivenNodes.stream().allMatch(node -> weaponIds.containsKey(weaponSlug(node)))) {
			return weaponIds;
		}
		try {
			syncService.sync(false);
		}
		catch (RuntimeException ex) {
			log.warn("Manifest sync for unknown weapons failed", ex);
			return weaponIds;
		}
		return weaponService.findIdsBySlug();
	}

	private static String weaponSlug(JsonNode node) {
		return node.path("item").path("weapon_url_name").asString();
	}

	// Skip and return empty if a node is malformed or its weapon isn't synced
	private Optional<Riven> read(JsonNode node, Map<String, Long> weaponIds) {
		RivenAuctionDTO auction;
		try {
			auction = mapper.treeToValue(node, RivenAuctionDTO.class);
		}
		catch (JacksonException ex) {
			log.warn("Skipping malformed riven auction {}: {}", node.path("id"), ex.getOriginalMessage());
			return Optional.empty();
		}
		Long weaponId = weaponIds.get(auction.item().weaponUrlName());
		if (weaponId == null) {
			log.warn("Skipping riven auction {}: unknown weapon {}", auction.id(), auction.item().weaponUrlName());
			return Optional.empty();
		}
		return Optional.of(new Riven(auction, weaponId, node));
	}

}
