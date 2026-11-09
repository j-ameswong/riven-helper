package com.riven.helper.backend.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * The {@code data} of /v2/versions. {@code apps} (mobile app versions) is
 * dropped as this project has no use for it. See docs/warframe-market-api.md.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VersionsDTO(
		@Nullable String id,
		@JsonSetter(nulls = Nulls.FAIL) Collections collections,
		@Nullable Instant updatedAt) {

	/**
	 * A hash per manifest collection that changes whenever the collection does.
	 * Treat each as opaque: compare it with the cached value, don't decode it.
	 * {@code rivens} covers both /v2/riven/weapons and /v2/riven/attributes.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Collections(
			@Nullable String items,
			@JsonSetter(nulls = Nulls.FAIL) String rivens,
			@Nullable String liches,
			@Nullable String sisters,
			@Nullable String missions,
			@Nullable String npcs,
			@Nullable String locations) {
	}

}
