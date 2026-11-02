package com.riven.helper.backend.dto;

/**
 * One language's entry in a v2 manifest's {@code i18n} map. {@code icon} and
 * {@code thumb} are relative to {@code https://warframe.market/static/assets/}.
 */
public record I18nDTO(String name, String icon, String thumb) {
}
