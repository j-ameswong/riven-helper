package com.riven.helper.backend.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.WeaponGroup;

/**
 * An element of {@code data} in /v2/riven/weapons, and the {@code data} of
 * /v2/riven/weapon/{slug}. See docs/warframe-market-api.md for field meanings.
 *
 * <p>{@code rivenType} is as the API sends it, so archguns arrive as
 * {@code rifle}; the remap to {@link RivenType#ARCHGUN} happens on import.
 * {@code disposition} is dropped because dispositions are gone from the game.
 *
 * @param i18n keyed by language code; only the requested {@code Language} is present
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeaponDTO(
		String id,
		String slug,
		String gameRef,
		WeaponGroup group,
		RivenType rivenType,
		int reqMasteryRank,
		Map<String, I18nDTO> i18n) {
}
