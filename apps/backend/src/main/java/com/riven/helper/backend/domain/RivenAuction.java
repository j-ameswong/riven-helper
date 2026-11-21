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
