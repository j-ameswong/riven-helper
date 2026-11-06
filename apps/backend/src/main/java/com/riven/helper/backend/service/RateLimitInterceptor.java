package com.riven.helper.backend.service;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.riven.helper.backend.ratelimit.RedisRateLimiter;

/**
 * Waits for a rate limit slot before every request, and holds back later
 * requests after a {@code 429}. The {@code 429} itself is returned as usual, so
 * the caller sees an exception and decides whether to try again.
 */
class RateLimitInterceptor implements ClientHttpRequestInterceptor {

	// The value auction search sends; used when a 429 has no usable Retry-After
	private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(120);

	private final RedisRateLimiter limiter;

	RateLimitInterceptor(RedisRateLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		try {
			limiter.acquire();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Interrupted while waiting for a rate limit slot");
		}
		ClientHttpResponse response = execution.execute(request, body);
		if (response.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
			limiter.pause(retryAfter(response.getHeaders()));
		}
		return response;
	}

	private static Duration retryAfter(HttpHeaders headers) {
		try {
			return Duration.ofSeconds(Long.parseLong(headers.getFirst(HttpHeaders.RETRY_AFTER)));
		}
		catch (NumberFormatException ex) {
			return DEFAULT_RETRY_AFTER;
		}
	}

}
