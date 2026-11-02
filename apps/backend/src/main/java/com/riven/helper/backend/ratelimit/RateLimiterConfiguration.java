package com.riven.helper.backend.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.riven.helper.backend.ratelimit.RateLimitProperties.Limit;

/**
 * The two warframe.market rate limiters.
 *
 * <p>An auction search is subject to both, so acquire
 * {@code auctionSearchRateLimiter} first and then {@code generalRateLimiter}:
 * that way the long search wait isn't spent holding a general slot.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimiterConfiguration {

	private static final String KEY_PREFIX = "rate-limit:warframe-market:";

	@Bean
	RedisRateLimiter generalRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
		return limiter(redis, "general", properties.general());
	}

	@Bean
	RedisRateLimiter auctionSearchRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
		return limiter(redis, "auction-search", properties.auctionSearch());
	}

	private static RedisRateLimiter limiter(StringRedisTemplate redis, String name, Limit limit) {
		return new RedisRateLimiter(redis, KEY_PREFIX + name, limit.interval(), limit.burst());
	}

}
