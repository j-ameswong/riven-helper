package com.riven.helper.backend.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import tools.jackson.databind.JsonNode;

/**
 * The envelope every v2 response comes in. See "Response envelopes" in
 * docs/warframe-market-api.md.
 *
 * @param data an object or an array depending on the endpoint, e.g.
 *        {@code V2ResponseDTO<List<WeaponDTO>>} for /v2/riven/weapons. Null
 *        if the API sent an error; list elements are never null.
 * @param error null on success
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record V2ResponseDTO<T>(
		@Nullable String apiVersion,
		@JsonSetter(contentNulls = Nulls.FAIL) @Nullable T data,
		@Nullable JsonNode error) {

	public V2ResponseDTO {
		// Jackson reads "error": null into a NullNode
		if (error != null && error.isNull()) {
			error = null;
		}
	}

	/**
	 * The {@code data} of {@code response}, or an exception if the API sent an
	 * error or nothing. Static because an empty body makes the client return null.
	 */
	public static <T> T requireData(@Nullable V2ResponseDTO<T> response) {
		T data = response == null ? null : response.data();
		if (response == null || response.error() != null || data == null) {
			throw new IllegalStateException("warframe.market returned no data: " + response);
		}
		return data;
	}

}
