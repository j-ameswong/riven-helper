package com.riven.helper.backend.service;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.riven.helper.backend.ratelimit.RedisRateLimiter;

/**
 * Builds {@link WarframeMarketRestClient}. See "Request headers" in
 * docs/warframe-market-api.md.
 */
@Configuration(proxyBeanMethods = false)
class WarframeMarketClientConfiguration {

	private static final String BASE_URL = "https://api.warframe.market";

	private static final String USER_AGENT = "RivenHelper/0.0.1 (+https://github.com/j-ameswong/riven-helper)";

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	@Bean
	WarframeMarketRestClient warframeMarketRestClient(RedisRateLimiter generalRateLimiter) {
		return client(restClient(generalRateLimiter));
	}

	static RestClient.Builder restClient(RedisRateLimiter generalRateLimiter) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);

		return RestClient.builder()
				.baseUrl(BASE_URL)
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
				.defaultHeader("Platform", "pc")
				.defaultHeader("Language", "en")
				.requestInterceptor(new RateLimitInterceptor(generalRateLimiter));
	}

	static WarframeMarketRestClient client(RestClient.Builder restClient) {
		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient.build()))
				.build()
				.createClient(WarframeMarketRestClient.class);
	}

}
