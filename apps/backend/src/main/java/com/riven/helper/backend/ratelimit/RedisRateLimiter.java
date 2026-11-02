package com.riven.helper.backend.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * A rate limiter shared by every thread and process that uses the same Redis
 * key. It lets {@code burst} requests through at once, then one per
 * {@code interval}, which is a token bucket of size {@code burst} refilled
 * every {@code interval}. With a burst of 1 it just spaces requests out.
 *
 * <p>The state is a single key holding the theoretical arrival time (GCRA):
 * when the bucket would next be full again, in Redis server milliseconds.
 * Each call reserves a slot atomically in a Lua script and is told how long to
 * wait for it, so callers queue up fairly instead of polling. Using Redis's
 * clock keeps instances with skewed clocks in agreement. The key expires once
 * the bucket is full.
 */
public class RedisRateLimiter {

	// KEYS[1]: arrival-time key. ARGV[1]: interval ms. ARGV[2]: burst tolerance ms,
	// how far the arrival time may run ahead of now before callers have to wait.
	private static final RedisScript<Long> RESERVE = RedisScript.of("""
			local time = redis.call('TIME')
			local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
			local arrival = math.max(tonumber(redis.call('GET', KEYS[1]) or now), now)
			local wait = math.max(arrival - tonumber(ARGV[2]) - now, 0)
			local nextArrival = arrival + tonumber(ARGV[1])
			redis.call('SET', KEYS[1], string.format('%d', nextArrival), 'PX', string.format('%d', nextArrival - now))
			return wait
			""", Long.class);

	// KEYS[1]: arrival-time key. ARGV[1]: pause ms. ARGV[2]: burst tolerance ms.
	// Moves the arrival time so the next slot opens after the pause with the
	// bucket empty, unless it is already later than that.
	private static final RedisScript<Long> PAUSE = RedisScript.of("""
			local time = redis.call('TIME')
			local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
			local arrival = tonumber(redis.call('GET', KEYS[1]) or now)
			local pausedArrival = now + tonumber(ARGV[1]) + tonumber(ARGV[2])
			if pausedArrival > arrival then
			  redis.call('SET', KEYS[1], string.format('%d', pausedArrival), 'PX', string.format('%d', pausedArrival - now))
			end
			return 0
			""", Long.class);

	private final StringRedisTemplate redis;

	private final List<String> keys;

	private final String intervalMs;

	private final String burstToleranceMs;

	public RedisRateLimiter(StringRedisTemplate redis, String key, Duration interval, int burst) {
		if (!interval.isPositive()) {
			throw new IllegalArgumentException("interval must be positive, was " + interval);
		}
		if (burst < 1) {
			throw new IllegalArgumentException("burst must be at least 1, was " + burst);
		}
		this.redis = redis;
		this.keys = List.of(key);
		this.intervalMs = Long.toString(interval.toMillis());
		this.burstToleranceMs = Long.toString(interval.toMillis() * (burst - 1));
	}

	/**
	 * Reserves the next slot and blocks until it opens.
	 */
	public void acquire() throws InterruptedException {
		Duration wait = reserve();
		if (wait.isPositive()) {
			Thread.sleep(wait);
		}
	}

	/**
	 * Reserves the next slot without blocking. The slot is taken whether or not
	 * the caller goes on to use it.
	 *
	 * @return how long to wait before sending the request, zero if it can go now
	 */
	public Duration reserve() {
		return Duration.ofMillis(redis.execute(RESERVE, keys, intervalMs, burstToleranceMs));
	}

	/**
	 * Holds back every caller for {@code duration}, e.g. a {@code Retry-After}
	 * from a {@code 429}. Afterwards the bucket starts empty, so requests resume
	 * at one per interval rather than with a fresh burst. Slots already reserved
	 * are not affected.
	 */
	public void pause(Duration duration) {
		redis.execute(PAUSE, keys, Long.toString(duration.toMillis()), burstToleranceMs);
	}

}
