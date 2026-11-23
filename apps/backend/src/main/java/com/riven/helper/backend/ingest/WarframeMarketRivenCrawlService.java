package com.riven.helper.backend.ingest;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.riven.helper.backend.auction.RivenAuctionService;
import com.riven.helper.backend.ingest.RivenAuctionReader.Manifest;
import com.riven.helper.backend.manifest.AttributeService;
import com.riven.helper.backend.manifest.Weapon;
import com.riven.helper.backend.manifest.WeaponService;
import com.riven.helper.backend.warframemarket.V1AuctionsDTO;
import com.riven.helper.backend.warframemarket.WarframeMarketRestClient;

import tools.jackson.databind.JsonNode;

/**
 * Seeds and refreshes riven auctions weapon by weapon from /v1/auctions/search,
 * which is what picks up price edits and removals. See docs/riven-crawl.md.
 */
@Service
public class WarframeMarketRivenCrawlService {

	private static final Logger log = LoggerFactory.getLogger(WarframeMarketRivenCrawlService.class);

	private final WarframeMarketRestClient client;

	private final WeaponService weaponService;

	private final AttributeService attributeService;

	private final RivenAuctionService auctionService;

	private final RivenAuctionReader reader;

	WarframeMarketRivenCrawlService(WarframeMarketRestClient client, WeaponService weaponService,
			AttributeService attributeService, RivenAuctionService auctionService, RivenAuctionReader reader) {
		this.client = client;
		this.weaponService = weaponService;
		this.attributeService = attributeService;
		this.auctionService = auctionService;
		this.reader = reader;
	}

	/**
	 * @param auctions distinct auctions the crawl returned
	 * @param requests searches sent, counting one that failed
	 * @param removed auctions marked removed, always 0 unless {@code complete}
	 * @param complete false if a request failed or a bucket overflowed, so some
	 *        auctions may have been missed
	 */
	public record CrawlResult(String weaponSlug, int auctions, int requests, int removed, boolean complete) {
	}

	// The search rate limiter does the pacing; the delay only idles an empty queue
	@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
	void scheduledCrawl() {
		crawlNext();
	}

	/** Crawls the most overdue weapon, if the manifest has synced any. */
	public synchronized Optional<CrawlResult> crawlNext() {
		return weaponService.findNextToCrawl().map(this::crawl);
	}

	private CrawlResult crawl(Weapon weapon) {
		long weaponId = Objects.requireNonNull(weapon.getWeaponId());
		Manifest manifest = new Manifest(Map.of(weapon.getSlug(), weaponId), attributeService.findIdsBySlug());
		OffsetDateTime startedAt = OffsetDateTime.now();
		Set<String> seen = new HashSet<>();
		AtomicInteger requests = new AtomicInteger();
		boolean complete = false;
		int removed = 0;
		try {
			List<RivenBucket> plan = RivenBookPlanner.plan(auctionService.countLive(weaponId));
			complete = RivenBookCrawler.crawl(Map.of("weapon_url_name", weapon.getSlug()), plan, filters -> {
				requests.incrementAndGet();
				List<JsonNode> page = search(filters);
				page.forEach(node -> {
					seen.add(node.path("id").asString());
					reader.read(node, manifest).ifPresent(
							riven -> auctionService.archive(riven.auction(), riven.attributes(), riven.snapshot()));
				});
				return page;
			});
			// An empty book is more likely an API fault than every listing gone
			if (complete && !seen.isEmpty()) {
				removed = auctionService.markRemoved(weaponId, seen, startedAt);
			}
		}
		catch (RuntimeException ex) {
			log.warn("Crawl of {} failed after {} auctions", weapon.getSlug(), seen.size(), ex);
		}
		finally {
			// Even after a failure, so one broken weapon can't block the queue
			weaponService.markCrawled(weaponId);
		}
		CrawlResult result = new CrawlResult(weapon.getSlug(), seen.size(), requests.get(), removed, complete);
		log.info("Crawled {}", result);
		return result;
	}

	private List<JsonNode> search(Map<String, String> filters) {
		V1AuctionsDTO response = client.searchRivenAuctions(filters);
		if (response == null) {
			throw new IllegalStateException("warframe.market returned an empty body for /v1/auctions/search");
		}
		return response.payload().auctions();
	}

}
