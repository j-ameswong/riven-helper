package com.riven.helper.backend.manifest;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.riven.helper.backend.warframemarket.RivenType;
import com.riven.helper.backend.warframemarket.WeaponGroup;

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

	// For JPA
	protected Weapon() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final Weapon weapon = new Weapon();

		private Builder() {
		}

		public Builder weaponId(@Nullable Long weaponId) {
			weapon.weaponId = weaponId;
			return this;
		}

		public Builder apiWeaponId(String apiWeaponId) {
			weapon.apiWeaponId = apiWeaponId;
			return this;
		}

		public Builder slug(String slug) {
			weapon.slug = slug;
			return this;
		}

		public Builder gameRef(String gameRef) {
			weapon.gameRef = gameRef;
			return this;
		}

		public Builder weaponGroup(WeaponGroup weaponGroup) {
			weapon.weaponGroup = weaponGroup;
			return this;
		}

		public Builder rivenType(RivenType rivenType) {
			weapon.rivenType = rivenType;
			return this;
		}

		public Builder reqMasteryRank(short reqMasteryRank) {
			weapon.reqMasteryRank = reqMasteryRank;
			return this;
		}

		public Builder name(String name) {
			weapon.name = name;
			return this;
		}

		public Weapon build() {
			return weapon;
		}

	}

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
