package com.riven.helper.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

/**
 * A weapon that can take a riven, synced from /v2/riven/weapons. The database
 * schema is owned by Liquibase (see db/changelog/001-weapons-and-attributes.yaml);
 * this class only maps to it.
 */
@Entity
@Table(name = "weapons")
public class Weapon {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long weaponId;

	private String apiWeaponId;

	private String slug;

	private String gameRef;

	@Enumerated(EnumType.STRING)
	private WeaponGroup weaponGroup;

	@Enumerated(EnumType.STRING)
	private RivenType rivenType;

	private short reqMasteryRank;

	private String name;

	public @Nullable Long getWeaponId() {
		return weaponId;
	}

	public void setWeaponId(@Nullable Long weaponId) {
		this.weaponId = weaponId;
	}

	public String getApiWeaponId() {
		return apiWeaponId;
	}

	public void setApiWeaponId(String apiWeaponId) {
		this.apiWeaponId = apiWeaponId;
	}

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getGameRef() {
		return gameRef;
	}

	public void setGameRef(String gameRef) {
		this.gameRef = gameRef;
	}

	public WeaponGroup getWeaponGroup() {
		return weaponGroup;
	}

	public void setWeaponGroup(WeaponGroup weaponGroup) {
		this.weaponGroup = weaponGroup;
	}

	public RivenType getRivenType() {
		return rivenType;
	}

	public void setRivenType(RivenType rivenType) {
		this.rivenType = rivenType;
	}

	public short getReqMasteryRank() {
		return reqMasteryRank;
	}

	public void setReqMasteryRank(short reqMasteryRank) {
		this.reqMasteryRank = reqMasteryRank;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
