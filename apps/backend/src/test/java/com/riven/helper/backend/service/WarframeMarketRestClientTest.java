package com.riven.helper.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.V2ResponseDTO;
import com.riven.helper.backend.dto.WeaponDTO;
import com.riven.helper.backend.ratelimit.RedisRateLimiter;

/**
 * Runs the real client against a mock server, with bodies shaped like the
 * samples in docs/warframe-market-api.md.
 */
class WarframeMarketRestClientTest {

	private static final String BASE_URL = "https://api.warframe.market";

	private final RedisRateLimiter limiter = mock(RedisRateLimiter.class);

	private MockRestServiceServer server;

	private WarframeMarketRestClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder restClient = WarframeMarketClientConfiguration.restClient(limiter);
		server = MockRestServiceServer.bindTo(restClient).build();
		client = WarframeMarketClientConfiguration.client(restClient);
	}

	@Test
	void sendsTheRequiredHeadersAndReadsVersions() {
		server.expect(requestTo(BASE_URL + "/v2/versions"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.USER_AGENT,
						"RivenHelper/0.0.1 (+https://github.com/j-ameswong/riven-helper)"))
				.andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
				.andExpect(header("Platform", "pc"))
				.andExpect(header("Language", "en"))
				.andRespond(withSuccess("""
						{
						  "apiVersion": "0.25.0",
						  "data": { "id": "a", "collections": { "rivens": "MjAyNi0xMC0yMQ==" },
						            "updatedAt": "2026-10-25T12:30:28Z" },
						  "error": null
						}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.getVersions().data().collections().rivens()).isEqualTo("MjAyNi0xMC0yMQ==");
		server.verify();
	}

	@Test
	void readsWeapons() {
		server.expect(requestTo(BASE_URL + "/v2/riven/weapons"))
				.andRespond(withSuccess("""
						{
						  "apiVersion": "0.25.0",
						  "data": [ { "id": "a", "slug": "kulstar", "gameRef": "/Lotus/Kulstar", "group": "secondary",
						              "rivenType": "pistol", "disposition": 1.3, "reqMasteryRank": 5, "i18n": {} } ],
						  "error": null
						}
						""", MediaType.APPLICATION_JSON));

		V2ResponseDTO<List<WeaponDTO>> response = client.getWeapons();

		assertThat(response.data()).extracting(WeaponDTO::slug).containsExactly("kulstar");
		server.verify();
	}

	@Test
	void readsAttributes() {
		server.expect(requestTo(BASE_URL + "/v2/riven/attributes"))
				.andRespond(withSuccess("""
						{
						  "apiVersion": "0.25.0",
						  "data": [ { "id": "a", "slug": "punch_through", "gameRef": "WeaponPunctureDepthMod",
						              "group": "default", "prefix": "Lexi", "suffix": "Nok", "positiveOnly": true,
						              "i18n": {} } ],
						  "error": null
						}
						""", MediaType.APPLICATION_JSON));

		V2ResponseDTO<List<AttributeDTO>> response = client.getAttributes();

		assertThat(response.data()).extracting(AttributeDTO::slug).containsExactly("punch_through");
		server.verify();
	}

	@Test
	void readsAuctionsAsRawJson() {
		server.expect(requestTo(BASE_URL + "/v1/auctions"))
				.andRespond(withSuccess("""
						{ "payload": { "auctions": [ { "id": "a", "item": { "type": "lich" } } ] } }
						""", MediaType.APPLICATION_JSON));

		assertThat(client.getAuctions().payload().auctions()).singleElement()
				.satisfies(auction -> assertThat(auction.path("item").path("type").asString()).isEqualTo("lich"));
		server.verify();
	}

	@Test
	void waitsForASlotBeforeTheRequest() throws InterruptedException {
		server.expect(requestTo(BASE_URL + "/v2/versions"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		client.getVersions();

		verify(limiter).acquire();
		verify(limiter, never()).pause(any());
		server.verify();
	}

	@Test
	void pausesForRetryAfterAndThrowsOnA429() {
		server.expect(requestTo(BASE_URL + "/v2/versions"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "30"));

		assertThatThrownBy(client::getVersions).isInstanceOf(HttpClientErrorException.TooManyRequests.class);

		verify(limiter).pause(Duration.ofSeconds(30));
	}

	@Test
	void pausesForTheDefaultWhenA429HasNoRetryAfter() {
		server.expect(requestTo(BASE_URL + "/v2/versions"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(client::getVersions).isInstanceOf(HttpClientErrorException.TooManyRequests.class);

		verify(limiter).pause(Duration.ofSeconds(120));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "soon", "Wed, 21 Oct 2026 07:28:00 GMT" })
	void pausesForTheDefaultWhenRetryAfterIsNotANumber(String retryAfter) {
		server.expect(requestTo(BASE_URL + "/v2/versions"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, retryAfter));

		assertThatThrownBy(client::getVersions).isInstanceOf(HttpClientErrorException.TooManyRequests.class);

		verify(limiter).pause(Duration.ofSeconds(120));
	}

	@Test
	void doesNotPauseOnOtherErrors() {
		server.expect(requestTo(BASE_URL + "/v2/versions"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(client::getVersions).isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);

		verify(limiter, never()).pause(any());
	}

	@Test
	void keepsTheInterruptFlagWhenInterruptedWhileWaiting() throws InterruptedException {
		doThrow(new InterruptedException()).when(limiter).acquire();

		assertThatThrownBy(client::getVersions).isInstanceOf(ResourceAccessException.class);

		assertThat(Thread.interrupted()).isTrue();
		server.verify();
	}

}
