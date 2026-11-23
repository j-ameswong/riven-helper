package com.riven.helper.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.riven.helper.backend.auction.ListingCount;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Crawls a fake /v1/auctions/search that filters, sorts and caps at 100 the way
 * docs/warframe-market-api.md describes the real one.
 */
class RivenBookCrawlerTest {

	private static final String[] POLARITIES = { "madurai", "vazarin", "naramon" };

	private final List<JsonNode> book = new ArrayList<>();

	private final Set<String> seen = new HashSet<>();

	private int requests;

	@Test
	void readsASmallBookInOneRequest() {
		givenRandomBook(94);

		assertThat(crawl()).isTrue();
		assertThat(seen).hasSize(94);
		assertThat(requests).isOne();
	}

	@Test
	void readsUpToTwoPagesWithAPairOfRequests() {
		givenRandomBook(180);

		assertThat(crawl()).isTrue();
		assertThat(seen).hasSize(180);
		assertThat(requests).isEqualTo(2);
	}

	@Test
	void splitsABookTooBigForOnePair() {
		givenRandomBook(3000);

		assertThat(crawl()).isTrue();
		assertThat(seen).hasSize(3000);
	}

	@Test
	void splitsWhenThePagesOnlyMeetAtATiedPrice() {
		// Two full pages that share the cutoff price could hide auctions between them
		for (int i = 0; i < 250; i++) {
			add(8 + i % 9, POLARITIES[0], 0, 100);
		}

		assertThat(crawl()).isTrue();
		assertThat(seen).hasSize(250);
		assertThat(requests).isGreaterThan(2);
	}

	@Test
	void readsPureAuctionsWithNoBuyout() {
		for (int i = 0; i < 250; i++) {
			add(8 + i % 9, POLARITIES[0], 0, null);
		}

		assertThat(crawl()).isTrue();
		assertThat(seen).hasSize(250);
	}

	@Test
	void readsMasteryLevelsTheGameDoesNotHave() {
		// Mid-priced, so only the mastery rank split can reach them
		givenRandomBook(300);
		add(7, POLARITIES[0], 0, 1000);
		add(17, POLARITIES[0], 0, 1000);

		assertThat(crawl()).isTrue();
		assertThat(seen).hasSize(302);
	}

	@Test
	void reportsABucketNoSplitCanSeparate() {
		for (int i = 0; i < 250; i++) {
			add(12, POLARITIES[0], 0, 100);
		}
		givenRandomBook(50);

		assertThat(crawl()).isFalse();
		assertThat(seen).hasSizeGreaterThanOrEqualTo(50);
	}

	@Test
	void readsAPlannedBookInFewerRequests() {
		givenRandomBook(3000);
		crawl();
		int unplanned = requests;
		seen.clear();
		requests = 0;

		assertThat(crawl(RivenBookPlanner.plan(counts()))).isTrue();
		assertThat(seen).hasSize(3000);
		assertThat(requests).isLessThan(unplanned).isLessThanOrEqualTo(3000 / 75);
	}

	@Test
	void splitsAPlannedBucketThatGrew() {
		givenRandomBook(3000);
		List<RivenBucket> plan = RivenBookPlanner.plan(counts());
		for (int i = 0; i < 200; i++) {
			add(12, POLARITIES[i % 3], 50 + i % 10, 1000);
		}

		assertThat(crawl(plan)).isTrue();
		assertThat(seen).hasSize(3200);
	}

	private boolean crawl() {
		return crawl(List.of(RivenBucket.ALL));
	}

	private boolean crawl(List<RivenBucket> plan) {
		return RivenBookCrawler.crawl(Map.of("weapon_url_name", "rubico"), plan, this::search);
	}

	// One count per auction, as the planner sums them whether grouped or not
	private List<ListingCount> counts() {
		return book.stream()
				.map(a -> new ListingCount((short) a.path("item").path("mastery_level").asInt(),
						a.path("item").path("re_rolls").asInt(), a.path("item").path("polarity").asString(), 1))
				.toList();
	}

	private List<JsonNode> search(Map<String, String> params) {
		requests++;
		assertThat(params).containsEntry("weapon_url_name", "rubico");
		Comparator<JsonNode> byPrice = Comparator.comparingLong(a -> a.path("buyout_price").asLong(Long.MAX_VALUE));
		List<JsonNode> page = book.stream()
				.filter(a -> inRange(a.path("item").path("mastery_level").asInt(), params, "mastery_rank"))
				.filter(a -> inRange(a.path("item").path("re_rolls").asInt(), params, "re_rolls"))
				.filter(a -> !params.containsKey("polarity")
						|| params.get("polarity").equals(a.path("item").path("polarity").asString()))
				.sorted(params.get("sort_by").equals("price_asc") ? byPrice : byPrice.reversed())
				.limit(100)
				.toList();
		page.forEach(a -> seen.add(a.path("id").asString()));
		return page;
	}

	private static boolean inRange(int value, Map<String, String> params, String filter) {
		return value >= Integer.parseInt(params.getOrDefault(filter + "_min", "0"))
				&& value <= Integer.parseInt(params.getOrDefault(filter + "_max", "100000"));
	}

	private void givenRandomBook(int size) {
		Random random = new Random(42);
		for (int i = 0; i < size; i++) {
			add(8 + random.nextInt(9), POLARITIES[random.nextInt(3)], random.nextInt(300),
					random.nextInt(10) == 0 ? null : 1 + random.nextInt(2000));
		}
	}

	private void add(int masteryLevel, String polarity, int reRolls, @Nullable Integer buyoutPrice) {
		book.add(JsonMapper.shared().readTree("""
				{ "id": "a%d", "buyout_price": %s,
				  "item": { "mastery_level": %d, "polarity": "%s", "re_rolls": %d } }
				""".formatted(book.size(), buyoutPrice, masteryLevel, polarity, reRolls)));
	}

}
