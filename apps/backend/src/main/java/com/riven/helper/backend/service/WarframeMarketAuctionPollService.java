package com.riven.helper.backend.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.riven.helper.backend.dto.V1AuctionsDTO;
import com.riven.helper.backend.service.RivenAuctionReader.Manifest;
import com.riven.helper.backend.service.RivenAuctionReader.Riven;

import tools.jackson.databind.JsonNode;

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

	private final WarframeMarketRivenSyncService syncService;

	private final WeaponService weaponService;

	private final AttributeService attributeService;

	private final RivenAuctionService auctionService;

	private final RivenAuctionReader reader;

	WarframeMarketAuctionPollService(WarframeMarketRestClient client, WarframeMarketRivenSyncService syncService,
			WeaponService weaponService, AttributeService attributeService, RivenAuctionService auctionService,
			RivenAuctionReader reader) {
		this.client = client;
		this.syncService = syncService;
		this.weaponService = weaponService;
		this.attributeService = attributeService;
		this.auctionService = auctionService;
		this.reader = reader;
	}

	/**
	 * @param rivens riven auctions read from the feed, new or not
	 * @param gap true if the newest existing auction is older than
     * the oldest from the latest polled auction
	 */
	public record PollResult(int rivens, boolean gap) {
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
		Manifest manifest = manifest(rivenNodes);
		List<Riven> rivens = rivenNodes.stream().flatMap(node -> reader.read(node, manifest).stream()).toList();
		if (rivens.isEmpty()) {
			log.warn("/v1/auctions returned no riven auctions");
			return new PollResult(0, false);
		}

		OffsetDateTime oldest = rivens.stream().map(r -> r.auction().getCreatedAt())
				.min(Comparator.naturalOrder()).orElseThrow();
		Optional<OffsetDateTime> newestKnown = auctionService.findNewestCreatedAt();
		boolean gap = newestKnown.filter(oldest::isAfter).isPresent();
		if (gap) {
			log.warn("Possible gap in the riven archive: auctions created between {} and {} were missed",
					newestKnown.get(), oldest);
		}

		rivens.forEach(riven -> auctionService.archive(riven.auction(), riven.attributes(), riven.snapshot()));
		log.debug("Polled {} riven auctions", rivens.size());
		return new PollResult(rivens.size(), gap);
	}

	// A weapon or attribute newer than the hourly manifest sync would otherwise lose
	// its first listings for good, as they only stay in the feed for ~10 minutes
	private Manifest manifest(List<JsonNode> rivenNodes) {
		Manifest manifest = loadManifest();
		if (rivenNodes.stream().allMatch(manifest::knows)) {
			return manifest;
		}
		try {
			syncService.sync(false);
		}
		catch (RuntimeException ex) {
			log.warn("Manifest sync for unknown weapons or attributes failed", ex);
			return manifest;
		}
		return loadManifest();
	}

	private Manifest loadManifest() {
		return new Manifest(weaponService.findIdsBySlug(), attributeService.findIdsBySlug());
	}

}
