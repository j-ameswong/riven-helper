package com.riven.helper.backend.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.EnumeratedValue;

/**
 * Riven stat group ({@code group} in /v2/riven/attributes). See
 * docs/warframe-market-api.md for field meanings.
 */
public enum AttributeGroup {

	DEFAULT("default"),
	MELEE("melee");

	// Lowercase value used by the warframe.market API, in JSON and in the
	// text column. Allowed values are also listed in ck_attributes_attribute_group
	// (Liquibase); EnumCheckConstraintsTest fails if they drift apart.
	@EnumeratedValue
	private final String value;

	AttributeGroup(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

}
