package com.riven.helper.backend.service;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.riven.helper.backend.repository.ListingCount;

class RivenBookPlannerTest {

	@Test
	void plansOneBucketForABookAPairCanRead() {
		assertThat(RivenBookPlanner.plan(List.of())).containsExactly(RivenBucket.ALL);
		assertThat(RivenBookPlanner.plan(List.of(count(12, 0, "madurai", RivenBookPlanner.PAIR))))
				.containsExactly(RivenBucket.ALL);
	}

	@Test
	void coversEveryRivenExactlyOnceInBucketsAPairCanRead() {
		Random random = new Random(42);
		List<ListingCount> book = new ArrayList<>();
		for (int i = 0; i < 3000; i++) {
			book.add(count(8 + random.nextInt(9), random.nextInt(300), RivenBucket.POLARITIES.get(random.nextInt(3)), 1));
		}
		// Too big for one bucket even at a single mastery level and re-roll count
		RivenBucket.POLARITIES.forEach(polarity -> book.add(count(12, 0, polarity, 120)));

		List<RivenBucket> plan = RivenBookPlanner.plan(book);

		int[] masteries = IntStream.concat(IntStream.of(MIN_VALUE, MAX_VALUE), IntStream.rangeClosed(0, 20)).toArray();
		int[] reRolls = IntStream.concat(IntStream.of(MIN_VALUE, MAX_VALUE), IntStream.rangeClosed(0, 400)).toArray();
		for (int mastery : masteries) {
			for (int rolls : reRolls) {
				for (String polarity : RivenBucket.POLARITIES) {
					assertThat(plan).as("mastery %d, re-rolls %d, %s", mastery, rolls, polarity)
							.filteredOn(bucket -> contains(bucket, mastery, rolls, polarity))
							.hasSize(1);
				}
			}
		}
		assertThat(plan).allSatisfy(bucket -> assertThat(book.stream()
				.filter(listing -> contains(bucket, listing.masteryLevel(), listing.reRolls(), listing.polarity()))
				.mapToLong(ListingCount::count)
				.sum()).isLessThanOrEqualTo(RivenBookPlanner.PAIR));
	}

	private static ListingCount count(int mastery, int reRolls, String polarity, long count) {
		return new ListingCount((short) mastery, reRolls, polarity, count);
	}

	private static boolean contains(RivenBucket bucket, int mastery, int reRolls, String polarity) {
		return bucket.mastery().min() <= mastery && mastery <= bucket.mastery().max()
				&& bucket.reRolls().min() <= reRolls && reRolls <= bucket.reRolls().max()
				&& (bucket.polarity() == null || polarity.equals(bucket.polarity()));
	}

}
