package com.riven.helper.backend.ingest;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import com.riven.helper.backend.auction.ListingCount;
import com.riven.helper.backend.ingest.RivenBucket.Range;

/**
 * Plans the buckets a crawl reads from the listings already archived for the
 * weapon, so most requests come back nearly full. See docs/riven-crawl.md.
 */
final class RivenBookPlanner {

	// Planned sizes that one request (up to 94) and a price_asc/price_desc pair (up
	// to ~198) read in full, less ~10% for listings added since
	static final int ONE_REQUEST = 85;

	static final int PAIR = 180;

	private record Plan(int requests, List<RivenBucket> buckets) {

		static final Plan NONE = new Plan(0, List.of());

		static Plan of(RivenBucket bucket, long size) {
			return new Plan(size <= ONE_REQUEST ? 1 : 2, List.of(bucket));
		}

		Plan plus(Plan other) {
			return new Plan(requests + other.requests, Stream.concat(buckets.stream(), other.buckets.stream()).toList());
		}

	}

	private RivenBookPlanner() {
	}

	/**
	 * Buckets that together cover every possible riven of a weapon, cut where the
	 * archived listings need the fewest requests.
	 *
	 * @param book the weapon's live listings, counted by mastery level, re-rolls
	 *        and polarity; empty plans the whole book as one bucket
	 */
	static List<RivenBucket> plan(List<ListingCount> book) {
		return plan(RivenBucket.ALL, book, 0).buckets();
	}

	// Cuts into mastery ranges, then re-roll ranges, then polarities. A bucket still
	// too big after that is left for the crawler to split.
	private static Plan plan(RivenBucket bucket, List<ListingCount> book, int level) {
		long size = book.stream().mapToLong(ListingCount::count).sum();
		if (size <= PAIR || level > 2) {
			return Plan.of(bucket, size);
		}
		if (level == 2) {
			return RivenBucket.POLARITIES.stream()
					.map(polarity -> plan(bucket.withPolarity(polarity),
							book.stream().filter(listing -> listing.polarity().equals(polarity)).toList(), 3))
					.reduce(Plan.NONE, Plan::plus);
		}
		boolean mastery = level == 0;
		return ranges(book, level, mastery ? ListingCount::masteryLevel : ListingCount::reRolls,
				mastery ? bucket.mastery() : bucket.reRolls(), mastery ? bucket::withMastery : bucket::withReRolls);
	}

	/**
	 * The cheapest cut of {@code whole} into contiguous ranges, cutting only at
	 * values in the book. A range too big for a pair is planned one level down, but
	 * only a mastery range may then span several values: cutting re-rolls finer
	 * costs no more and keeps the search small.
	 */
	private static Plan ranges(List<ListingCount> book, int level, ToIntFunction<ListingCount> filter, Range whole,
			Function<Range, RivenBucket> narrow) {
		int[] values = book.stream().mapToInt(filter).distinct().sorted().toArray();
		long[] sizes = new long[values.length];
		book.forEach(listing -> sizes[Arrays.binarySearch(values, filter.applyAsInt(listing))] += listing.count());
		// best[end] is the cheapest plan for values[0 .. end - 1]
		Plan[] best = new Plan[values.length + 1];
		best[0] = Plan.NONE;
		for (int end = 1; end <= values.length; end++) {
			long size = 0;
			for (int start = end - 1; start >= 0; start--) {
				size += sizes[start];
				if (size > PAIR && level > 0 && start < end - 1) {
					break;
				}
				int lowest = values[start];
				int highest = values[end - 1];
				RivenBucket part = narrow.apply(new Range(start == 0 ? whole.min() : lowest,
						end == values.length ? whole.max() : values[end] - 1));
				Plan partPlan = size <= PAIR ? Plan.of(part, size) : plan(part, book.stream().filter(listing -> {
					int value = filter.applyAsInt(listing);
					return lowest <= value && value <= highest;
				}).toList(), level + 1);
				if (best[end] == null || best[start].requests() + partPlan.requests() < best[end].requests()) {
					best[end] = best[start].plus(partPlan);
				}
			}
		}
		return best[values.length];
	}

}
