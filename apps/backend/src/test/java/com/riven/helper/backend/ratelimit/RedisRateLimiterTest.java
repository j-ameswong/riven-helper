package com.riven.helper.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.riven.helper.backend.TestcontainersConfiguration;

/**
 * Runs the limiter's scripts against a real Redis. Each test uses its own key,
 * and the waits are read from {@link RedisRateLimiter#reserve()} so nothing
 * sleeps for a full interval. Tolerances cover the few milliseconds of Redis
 * time that pass between calls.
 */
@DataRedisTest
@Import(TestcontainersConfiguration.class)
class RedisRateLimiterTest {

	private static final Duration TOLERANCE = Duration.ofMillis(200);

	@Autowired
	StringRedisTemplate redis;

	@Test
	void letsTheBurstThroughThenOnePerInterval() {
		RedisRateLimiter limiter = limiter(Duration.ofSeconds(9), 10);

		for (int i = 0; i < 10; i++) {
			assertThat(limiter.reserve()).as("request %d", i).isZero();
		}
		assertWaitIsAbout(limiter.reserve(), Duration.ofSeconds(9));
		assertWaitIsAbout(limiter.reserve(), Duration.ofSeconds(18));
	}

	@Test
	void withABurstOfOneSpacesRequestsOut() {
		RedisRateLimiter limiter = limiter(Duration.ofMillis(350), 1);

		assertThat(limiter.reserve()).isZero();
		assertWaitIsAbout(limiter.reserve(), Duration.ofMillis(350));
		assertWaitIsAbout(limiter.reserve(), Duration.ofMillis(700));
	}

	@Test
	void concurrentCallersGetOneSlotEach() throws Exception {
		RedisRateLimiter limiter = limiter(Duration.ofSeconds(1), 1);
		int callers = 20;

		List<Long> waitSlots;
		try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
			List<Callable<Duration>> reserves = IntStream.range(0, callers)
					.<Callable<Duration>>mapToObj(i -> limiter::reserve)
					.toList();
			List<Future<Duration>> futures = executor.invokeAll(reserves);
			waitSlots = futures.stream()
					.map(RedisRateLimiterTest::await)
					.map(wait -> Math.round(wait.toMillis() / 1000.0))
					.sorted()
					.toList();
		}

		assertThat(waitSlots).isEqualTo(IntStream.range(0, callers).mapToObj(Long::valueOf).toList());
	}

	@Test
	void pauseHoldsEveryoneBackAndEmptiesTheBucket() {
		RedisRateLimiter limiter = limiter(Duration.ofSeconds(9), 10);
		limiter.reserve();

		limiter.pause(Duration.ofSeconds(120));

		assertWaitIsAbout(limiter.reserve(), Duration.ofSeconds(120));
		assertWaitIsAbout(limiter.reserve(), Duration.ofSeconds(129));
	}

	@Test
	void pauseShorterThanTheQueueChangesNothing() {
		RedisRateLimiter limiter = limiter(Duration.ofSeconds(60), 1);
		limiter.reserve();
		limiter.reserve();
		limiter.reserve();

		limiter.pause(Duration.ofSeconds(5));

		assertWaitIsAbout(limiter.reserve(), Duration.ofSeconds(180));
	}

	@Test
	void keyExpiresOnceTheBucketIsFull() {
		String key = uniqueKey();
		RedisRateLimiter limiter = new RedisRateLimiter(redis, key, Duration.ofSeconds(9), 10);

		limiter.reserve();

		assertThat(redis.getExpire(key)).isBetween(8L, 9L);
	}

	@Test
	void acquireBlocksUntilTheSlotOpens() throws InterruptedException {
		RedisRateLimiter limiter = limiter(Duration.ofMillis(300), 1);
		limiter.acquire();

		long start = System.nanoTime();
		limiter.acquire();

		assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(250));
	}

	@Test
	void rejectsInvalidLimits() {
		assertThatThrownBy(() -> limiter(Duration.ZERO, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> limiter(Duration.ofSeconds(1), 0)).isInstanceOf(IllegalArgumentException.class);
	}

	private RedisRateLimiter limiter(Duration interval, int burst) {
		return new RedisRateLimiter(redis, uniqueKey(), interval, burst);
	}

	private static String uniqueKey() {
		return "rate-limit:test:" + UUID.randomUUID();
	}

	private static void assertWaitIsAbout(Duration wait, Duration expected) {
		assertThat(wait).isBetween(expected.minus(TOLERANCE), expected);
	}

	private static Duration await(Future<Duration> future) {
		try {
			return future.get();
		}
		catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

}
