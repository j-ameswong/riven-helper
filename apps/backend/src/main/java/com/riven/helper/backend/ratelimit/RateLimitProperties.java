package com.riven.helper.backend.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limits for the warframe.market API, set in application.yaml. See
 * "Rate limits" in docs/warframe-market-api.md for how they were measured.
 *
 * @param general the limit every request is subject to
 * @param auctionSearch the stricter limit on /v1/auctions/search alone
 */
@ConfigurationProperties("riven-helper.rate-limits")
public record RateLimitProperties(Limit general, Limit auctionSearch) {

	/**
	 * Up to {@code burst} requests at once, then one per {@code interval}.
	 */
	public record Limit(Duration interval, int burst) {
	}

}
