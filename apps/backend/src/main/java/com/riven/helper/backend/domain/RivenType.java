package com.riven.helper.backend.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.EnumeratedValue;

/**
 * The riven type a weapon takes ({@code rivenType} in /v2/riven/weapons) and the
 * riven types an attribute can roll as ({@code exclusiveTo} in /v2/riven/attributes).
 * Not the same as {@link WeaponGroup}: archguns are remapped to {@code archgun}
 * on import, and sentinel weapons map to several types.
 * See docs/warframe-market-api.md for field meanings.
 */
public enum RivenType {

	RIFLE("rifle"),
	SHOTGUN("shotgun"),
	PISTOL("pistol"),
	MELEE("melee"),
	ZAW("zaw"),
	KITGUN("kitgun"),
	ARCHGUN("archgun");

	// Lowercase value used by the warframe.market API, in JSON and in the
	// text and text[] columns. Allowed values are also listed in
	// ck_weapons_riven_type and ck_attributes_exclusive_to (Liquibase);
	// EnumCheckConstraintsTest fails if they drift apart.
	@EnumeratedValue
	private final String value;

	RivenType(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

}
