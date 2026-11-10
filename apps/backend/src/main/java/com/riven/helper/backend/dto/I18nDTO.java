package com.riven.helper.backend.dto;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * One language's entry in a v2 manifest's {@code i18n} map. {@code icon} and
 * {@code thumb} are relative to {@code https://warframe.market/static/assets/}.
 */
public record I18nDTO(@JsonSetter(nulls = Nulls.FAIL) String name, @Nullable String icon, @Nullable String thumb) {

	/** The English name in {@code i18n}, or an exception naming {@code slug}. */
	public static String englishName(Map<String, I18nDTO> i18n, String slug) {
		I18nDTO english = i18n.get("en");
		if (english == null) {
			throw new IllegalStateException("No English name for " + slug);
		}
		return english.name();
	}

}
