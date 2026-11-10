package com.riven.helper.backend.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;
import org.jspecify.annotations.Nullable;

/**
 * One observed version of a {@link RivenAuction}, keyed on the auction's
 * {@code updated} timestamp. The database schema is owned by Liquibase (see
 * db/changelog/004-riven-auctions.yaml); this class only maps to it.
 */
@Entity
@Table(name = "riven_snapshots")
@IdClass(RivenSnapshot.Key.class)
public class RivenSnapshot {

	public record Key(String auctionId, OffsetDateTime updatedAt) implements Serializable {
	}

	@Id
	private String auctionId;

	@Id
	private OffsetDateTime updatedAt;

	private int startingPrice;

	// Null for a pure auction with no buyout
	private @Nullable Integer buyoutPrice;

	private boolean isDirectSell;

	private int reRolls;

	private short modRank;

	private short masteryLevel;

	private String polarity;

	// item.attributes as sent: [{url_name, value, positive}]
	@ColumnTransformer(write = "?::jsonb")
	private String attributes;

	// The whole auction object as sent, since history can't be refetched
	@ColumnTransformer(write = "?::jsonb")
	private String raw;

	public String getAuctionId() {
		return auctionId;
	}

	public void setAuctionId(String auctionId) {
		this.auctionId = auctionId;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public int getStartingPrice() {
		return startingPrice;
	}

	public void setStartingPrice(int startingPrice) {
		this.startingPrice = startingPrice;
	}

	public @Nullable Integer getBuyoutPrice() {
		return buyoutPrice;
	}

	public void setBuyoutPrice(@Nullable Integer buyoutPrice) {
		this.buyoutPrice = buyoutPrice;
	}

	public boolean isDirectSell() {
		return isDirectSell;
	}

	public void setDirectSell(boolean isDirectSell) {
		this.isDirectSell = isDirectSell;
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

	public String getAttributes() {
		return attributes;
	}

	public void setAttributes(String attributes) {
		this.attributes = attributes;
	}

	public String getRaw() {
		return raw;
	}

	public void setRaw(String raw) {
		this.raw = raw;
	}

}
