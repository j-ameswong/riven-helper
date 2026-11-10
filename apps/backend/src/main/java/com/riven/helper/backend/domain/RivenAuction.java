package com.riven.helper.backend.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

/**
 * A riven auction's identity and lifecycle, one row per API auction id. What
 * can change lives in {@link RivenSnapshot}. The database schema is owned by
 * Liquibase (see db/changelog/004-riven-auctions.yaml); this class only maps to it.
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

}
