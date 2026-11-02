package com.riven.helper.backend.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The {@code data} of /v2/versions. {@code apps} (mobile app versions) is
 * dropped as this project has no use for it. See docs/warframe-market-api.md.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VersionsDTO(String id, Collections collections, Instant updatedAt) {

	/**
	 * A hash per manifest collection that changes whenever the collection does.
	 * Treat each as opaque: compare it with the cached value, don't decode it.
	 * {@code rivens} covers both /v2/riven/weapons and /v2/riven/attributes.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Collections(
			String items,
			String rivens,
			String liches,
			String sisters,
			String missions,
			String npcs,
			String locations) {
	}

}
