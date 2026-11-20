package com.riven.helper.backend.service;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Riven search filters within one weapon: inclusive mastery and re-roll ranges,
 * open-ended at {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE}, and one
 * polarity, or any if null. See docs/riven-crawl.md.
 */
record RivenBucket(Range mastery, Range reRolls, @Nullable String polarity) {

	static final List<String> POLARITIES = List.of("madurai", "vazarin", "naramon");

	static final RivenBucket ALL = new RivenBucket(Range.ANY, Range.ANY, null);

	record Range(int min, int max) {

		static final Range ANY = new Range(MIN_VALUE, MAX_VALUE);

		Optional<Range> intersect(Range other) {
			Range both = new Range(Math.max(min, other.min), Math.min(max, other.max));
			return both.min <= both.max ? Optional.of(both) : Optional.empty();
		}

	}

	RivenBucket withMastery(Range range) {
		return new RivenBucket(range, reRolls, polarity);
	}

	RivenBucket withReRolls(Range range) {
		return new RivenBucket(mastery, range, polarity);
	}

	RivenBucket withPolarity(String polarity) {
		return new RivenBucket(mastery, reRolls, polarity);
	}

	/** The rivens in both buckets, or empty if no riven can be. */
	Optional<RivenBucket> intersect(RivenBucket other) {
		if (polarity != null && other.polarity != null && !polarity.equals(other.polarity)) {
			return Optional.empty();
		}
		String both = polarity != null ? polarity : other.polarity;
		return mastery.intersect(other.mastery)
				.flatMap(masteryBoth -> reRolls.intersect(other.reRolls)
						.map(reRollsBoth -> new RivenBucket(masteryBoth, reRollsBoth, both)));
	}

	/** The search query parameters, leaving out open ends. */
	Map<String, String> filters() {
		Map<String, String> filters = new HashMap<>();
		putBound(filters, "mastery_rank_min", mastery.min);
		putBound(filters, "mastery_rank_max", mastery.max);
		putBound(filters, "re_rolls_min", reRolls.min);
		putBound(filters, "re_rolls_max", reRolls.max);
		if (polarity != null) {
			filters.put("polarity", polarity);
		}
		return filters;
	}

	private static void putBound(Map<String, String> filters, String name, int bound) {
		if (bound != MIN_VALUE && bound != MAX_VALUE) {
			filters.put(name, Integer.toString(bound));
		}
	}

}
