package com.riven.helper.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;

/**
 * The envelope every v2 response comes in. See "Response envelopes" in
 * docs/warframe-market-api.md.
 *
 * @param data an object or an array depending on the endpoint, e.g.
 *        {@code V2ResponseDTO<List<WeaponDTO>>} for /v2/riven/weapons
 * @param error null on success
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record V2ResponseDTO<T>(String apiVersion, T data, JsonNode error) {

	public V2ResponseDTO {
		// Jackson reads "error": null into a NullNode
		if (error != null && error.isNull()) {
			error = null;
		}
	}

}
