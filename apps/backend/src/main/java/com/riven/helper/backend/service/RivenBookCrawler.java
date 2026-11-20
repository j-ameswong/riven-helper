package com.riven.helper.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.riven.helper.backend.service.RivenBucket.Range;

import tools.jackson.databind.JsonNode;

/**
 * Reads every riven auction in a weapon's planned buckets from
 * /v1/auctions/search, though a search returns at most 100. See
 * docs/riven-crawl.md.
 */
final class RivenBookCrawler {

	private static final Logger log = LoggerFactory.getLogger(RivenBookCrawler.class);

	// A capped page can hold slightly fewer than 100 auctions
	private static final int TRUNCATED = 95;

	// Tried in this order on a bucket bigger than planned. Each covers one filter's
	// whole range, so every riven matches exactly one part of each split.
	private static final List<List<RivenBucket>> SPLITS = List.of(
			// Open-ended, as sellers can list a mastery level the game doesn't have, e.g. 7
			cut(IntStream.rangeClosed(9, 16).toArray()).stream().map(RivenBucket.ALL::withMastery).toList(),
			RivenBucket.POLARITIES.stream().map(RivenBucket.ALL::withPolarity).toList(),
			cut(1, 4, 11, 26, 61, 151).stream().map(RivenBucket.ALL::withReRolls).toList());

	private final Map<String, String> weapon;

	private final Function<Map<String, String>, List<JsonNode>> search;

	private RivenBookCrawler(Map<String, String> weapon, Function<Map<String, String>, List<JsonNode>> search) {
		this.weapon = weapon;
		this.search = search;
	}

	/**
	 * Calls {@code search} until every auction in {@code plan} has been on a page
	 * it returned.
	 *
	 * @param weapon the filters every search shares
	 * @param plan buckets that together cover every riven, see {@link RivenBookPlanner}
	 * @param search runs one search with the given query parameters
	 * @return false if some auctions were missed, because a bucket still
	 *         overflowed after every split
	 */
	static boolean crawl(Map<String, String> weapon, List<RivenBucket> plan,
			Function<Map<String, String>, List<JsonNode>> search) {
		RivenBookCrawler crawler = new RivenBookCrawler(weapon, search);
		boolean complete = true;
		for (RivenBucket bucket : plan) {
			complete &= crawler.crawl(bucket, 0);
		}
		return complete;
	}

	private boolean crawl(RivenBucket bucket, int depth) {
		List<JsonNode> cheapest = search(bucket, "price_asc");
		if (cheapest.size() < TRUNCATED) {
			return true;
		}
		List<JsonNode> dearest = search(bucket, "price_desc");
		// Strictly, as auctions tied at the cutoff price come in no known order
		if (dearest.size() < TRUNCATED || lowestPrice(dearest) < highestPrice(cheapest)) {
			return true;
		}
		for (int level = depth; level < SPLITS.size(); level++) {
			List<RivenBucket> parts = SPLITS.get(level).stream()
					.flatMap(split -> bucket.intersect(split).stream())
					.toList();
			// A single part is the bucket itself, which this split can't separate
			if (parts.size() > 1) {
				boolean complete = true;
				for (RivenBucket part : parts) {
					complete &= crawl(part, level + 1);
				}
				return complete;
			}
		}
		log.warn("Riven auctions missed: more than two pages match {} {} after every split", weapon, bucket.filters());
		return false;
	}

	private List<JsonNode> search(RivenBucket bucket, String sortBy) {
		Map<String, String> params = new HashMap<>(weapon);
		params.putAll(bucket.filters());
		params.put("sort_by", sortBy);
		return search.apply(params);
	}

	private static long lowestPrice(List<JsonNode> page) {
		return page.stream().mapToLong(RivenBookCrawler::price).min().orElseThrow();
	}

	private static long highestPrice(List<JsonNode> page) {
		return page.stream().mapToLong(RivenBookCrawler::price).max().orElseThrow();
	}

	// A pure auction has no buyout and sorts as the most expensive
	private static long price(JsonNode auction) {
		return auction.path("buyout_price").asLong(Long.MAX_VALUE);
	}

	// The ranges from each lower bound up to the next, open-ended at both ends
	private static List<Range> cut(int... lowerBounds) {
		List<Range> ranges = new ArrayList<>();
		int min = Integer.MIN_VALUE;
		for (int bound : lowerBounds) {
			ranges.add(new Range(min, bound - 1));
			min = bound;
		}
		ranges.add(new Range(min, Integer.MAX_VALUE));
		return ranges;
	}

}
