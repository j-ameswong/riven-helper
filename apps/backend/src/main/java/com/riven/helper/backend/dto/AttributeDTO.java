package com.riven.helper.backend.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.riven.helper.backend.domain.AttributeGroup;
import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Unit;

/**
 * An element of {@code data} in /v2/riven/attributes. See
 * docs/warframe-market-api.md for field meanings.
 *
 * <p>The API leaves out {@code unit}, {@code exclusiveTo} and the three flags
 * on most attributes rather than sending null or false, so a missing
 * {@code unit} or {@code exclusiveTo} reads as null and a missing flag as false
 * (spelled out because Jackson 3 otherwise rejects a missing primitive).
 *
 * @param exclusiveTo null means every riven type can roll it
 * @param i18n keyed by language code; only the requested {@code Language} is present
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttributeDTO(
		String id,
		String slug,
		String gameRef,
		AttributeGroup group,
		String prefix,
		String suffix,
		Unit unit,
		List<RivenType> exclusiveTo,
		@JsonSetter(nulls = Nulls.AS_EMPTY) boolean positiveOnly,
		@JsonSetter(nulls = Nulls.AS_EMPTY) boolean negativeOnly,
		@JsonSetter(nulls = Nulls.AS_EMPTY) boolean positiveIsNegative,
		Map<String, I18nDTO> i18n) {
}
