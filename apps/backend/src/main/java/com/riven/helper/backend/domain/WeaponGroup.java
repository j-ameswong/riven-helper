package com.riven.helper.backend.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.EnumeratedValue;

/**
 * Weapon slot or category ({@code group} in /v2/riven/weapons). See
 * docs/warframe-market-api.md for field meanings.
 */
public enum WeaponGroup {

	PRIMARY("primary"),
	SECONDARY("secondary"),
	MELEE("melee"),
	ARCHGUN("archgun"),
	SENTINEL("sentinel"),
	ZAW("zaw"),
	KITGUN("kitgun");

	// Lowercase value used by the warframe.market API, in JSON and in the
	// text column. Allowed values are also listed in ck_weapons_weapon_group
	// (Liquibase); EnumCheckConstraintsTest fails if they drift apart.
	@EnumeratedValue
	private final String value;

	WeaponGroup(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

}
