package com.riven.helper.backend.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

/**
 * A riven auction's identity, lifecycle and riven, one row per API auction id.
 * A listed riven can't be edited, so its fields live here and its stats in
 * {@link RivenAttribute}; its price, which can change, lives in
 * {@link RivenSnapshot}. The database schema is owned by Liquibase (see
 * db/changelog/004-riven-auctions.yaml and 006-immutable-riven-fields.yaml);
 * this class only maps to it.
 */
@Entity
@Table(name = "riven_auctions")
public class RivenAuction {

	@Id
	private String auctionId;

	private long weaponId;

	// Sellers relist the same riven under new auction ids
	private String ownerId;

	private OffsetDateTime createdAt;

	private OffsetDateTime lastUpdatedAt;

	// Set when a complete crawl of the weapon no longer returns it
	private @Nullable OffsetDateTime removedAt;

	private int reRolls;

	private short modRank;

	private short masteryLevel;

	private String polarity;

	// For JPA
	protected RivenAuction() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final RivenAuction rivenAuction = new RivenAuction();

		private Builder() {
		}

		public Builder auctionId(String auctionId) {
			rivenAuction.auctionId = auctionId;
			return this;
		}

		public Builder weaponId(long weaponId) {
			rivenAuction.weaponId = weaponId;
			return this;
		}

		public Builder ownerId(String ownerId) {
			rivenAuction.ownerId = ownerId;
			return this;
		}

		public Builder createdAt(OffsetDateTime createdAt) {
			rivenAuction.createdAt = createdAt;
			return this;
		}

		public Builder lastUpdatedAt(OffsetDateTime lastUpdatedAt) {
			rivenAuction.lastUpdatedAt = lastUpdatedAt;
			return this;
		}

		public Builder removedAt(@Nullable OffsetDateTime removedAt) {
			rivenAuction.removedAt = removedAt;
			return this;
		}

		public Builder reRolls(int reRolls) {
			rivenAuction.reRolls = reRolls;
			return this;
		}

		public Builder modRank(short modRank) {
			rivenAuction.modRank = modRank;
			return this;
		}

		public Builder masteryLevel(short masteryLevel) {
			rivenAuction.masteryLevel = masteryLevel;
			return this;
		}

		public Builder polarity(String polarity) {
			rivenAuction.polarity = polarity;
			return this;
		}

		public RivenAuction build() {
			return rivenAuction;
		}

	}

	public String getAuctionId() {
		return auctionId;
	}

	public void setAuctionId(String auctionId) {
		this.auctionId = auctionId;
	}

	public long getWeaponId() {
		return weaponId;
	}

	public void setWeaponId(long weaponId) {
		this.weaponId = weaponId;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public OffsetDateTime getLastUpdatedAt() {
		return lastUpdatedAt;
	}

	public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) {
		this.lastUpdatedAt = lastUpdatedAt;
	}

	public @Nullable OffsetDateTime getRemovedAt() {
		return removedAt;
	}

	public void setRemovedAt(@Nullable OffsetDateTime removedAt) {
		this.removedAt = removedAt;
	}

	public int getReRolls() {
		return reRolls;
	}

	public void setReRolls(int reRolls) {
		this.reRolls = reRolls;
	}

	public short getModRank() {
		return modRank;
	}

	public void setModRank(short modRank) {
		this.modRank = modRank;
	}

	public short getMasteryLevel() {
		return masteryLevel;
	}

	public void setMasteryLevel(short masteryLevel) {
		this.masteryLevel = masteryLevel;
	}

	public String getPolarity() {
		return polarity;
	}

	public void setPolarity(String polarity) {
		this.polarity = polarity;
	}

}
