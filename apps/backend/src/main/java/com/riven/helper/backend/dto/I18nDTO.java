package com.riven.helper.backend.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * One language's entry in a v2 manifest's {@code i18n} map. {@code icon} and
 * {@code thumb} are relative to {@code https://warframe.market/static/assets/}.
 */
public record I18nDTO(@JsonSetter(nulls = Nulls.FAIL) String name, @Nullable String icon, @Nullable String thumb) {
}
