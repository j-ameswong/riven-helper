package com.riven.helper.backend.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;

/**
 * Reads every riven auction matching a set of /v1/auctions/search filters, though
 * a search returns at most 100. See docs/riven-crawl.md.
 */
final class RivenBookCrawler {

	private static final Logger log = LoggerFactory.getLogger(RivenBookCrawler.class);

	// A capped page can hold slightly fewer than 100 auctions
	private static final int TRUNCATED = 95;

	// Tried in this order. Every riven matches exactly one value of each split.
	private static final List<List<Map<String, String>>> SPLITS = List.of(
			// Open-ended, as sellers can list a mastery level the game doesn't have, e.g. 7
			Stream.of(Stream.of(Map.of("mastery_rank_max", "8")),
					IntStream.rangeClosed(9, 15).mapToObj(rank -> range("mastery_rank", rank, rank)),
					Stream.of(Map.of("mastery_rank_min", "16")))
					.flatMap(Function.identity()).toList(),
			Stream.of("madurai", "vazarin", "naramon").map(polarity -> Map.of("polarity", polarity)).toList(),
			List.of(range("re_rolls", 0, 0), range("re_rolls", 1, 3),
					range("re_rolls", 4, 10), range("re_rolls", 11, 25),
					range("re_rolls", 26, 60), range("re_rolls", 61, 150),
					Map.of("re_rolls_min", "151")));

	private final Function<Map<String, String>, List<JsonNode>> search;

	private RivenBookCrawler(Function<Map<String, String>, List<JsonNode>> search) {
		this.search = search;
	}

	/**
	 * Calls {@code search} until every auction matching {@code filters} has been
	 * on a page it returned.
	 *
	 * @param search runs one search with the given query parameters
	 * @return false if some auctions were missed, because a bucket still
	 *         overflowed after every split
	 */
	static boolean crawl(Map<String, String> filters, Function<Map<String, String>, List<JsonNode>> search) {
		return new RivenBookCrawler(search).crawl(filters, 0);
	}

	private boolean crawl(Map<String, String> filters, int depth) {
		List<JsonNode> cheapest = search(filters, "price_asc");
		if (cheapest.size() < TRUNCATED) {
			return true;
		}
		List<JsonNode> dearest = search(filters, "price_desc");
		// Strictly, as auctions tied at the cutoff price come in no known order
		if (dearest.size() < TRUNCATED || lowestPrice(dearest) < highestPrice(cheapest)) {
			return true;
		}
		if (depth == SPLITS.size()) {
			log.warn("Riven auctions missed: more than two pages match {} after every split", filters);
			return false;
		}
		boolean complete = true;
		for (Map<String, String> split : SPLITS.get(depth)) {
			Map<String, String> bucket = new HashMap<>(filters);
			bucket.putAll(split);
			complete &= crawl(bucket, depth + 1);
		}
		return complete;
	}

	private List<JsonNode> search(Map<String, String> filters, String sortBy) {
		Map<String, String> params = new HashMap<>(filters);
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

	private static Map<String, String> range(String filter, int min, int max) {
		return Map.of(filter + "_min", Integer.toString(min), filter + "_max", Integer.toString(max));
	}

}
