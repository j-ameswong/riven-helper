package com.riven.helper.backend.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.EnumeratedValue;

/**
 * Unit of an attribute value ({@code unit} in /v2/riven/attributes). Absent on
 * a handful of stats such as {@code punch_through} and {@code range}.
 * See docs/warframe-market-api.md for field meanings.
 */
public enum Unit {

	PERCENT("percent"),
	MULTIPLY("multiply"),
	SECONDS("seconds");

	// Lowercase value used by the warframe.market API, in JSON and in the
	// text column. Allowed values are also listed in ck_attributes_unit
	// (Liquibase); EnumCheckConstraintsTest fails if they drift apart.
	@EnumeratedValue
	private final String value;

	Unit(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

}
