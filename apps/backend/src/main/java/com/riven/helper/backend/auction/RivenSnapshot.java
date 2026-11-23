package com.riven.helper.backend.auction;

import java.io.Serializable;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;
import org.jspecify.annotations.Nullable;

/**
 * One observed price of a {@link RivenAuction}, keyed on the auction's
 * {@code updated} timestamp. The database schema is owned by Liquibase (see
 * db/changelog/004-riven-auctions.yaml and 006-immutable-riven-fields.yaml);
 * this class only maps to it.
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

	// The whole auction object as sent, since history can't be refetched
	@ColumnTransformer(write = "?::jsonb")
	private String raw;

	// For JPA
	protected RivenSnapshot() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final RivenSnapshot rivenSnapshot = new RivenSnapshot();

		private Builder() {
		}

		public Builder auctionId(String auctionId) {
			rivenSnapshot.auctionId = auctionId;
			return this;
		}

		public Builder updatedAt(OffsetDateTime updatedAt) {
			rivenSnapshot.updatedAt = updatedAt;
			return this;
		}

		public Builder startingPrice(int startingPrice) {
			rivenSnapshot.startingPrice = startingPrice;
			return this;
		}

		public Builder buyoutPrice(@Nullable Integer buyoutPrice) {
			rivenSnapshot.buyoutPrice = buyoutPrice;
			return this;
		}

		public Builder directSell(boolean isDirectSell) {
			rivenSnapshot.isDirectSell = isDirectSell;
			return this;
		}

		public Builder raw(String raw) {
			rivenSnapshot.raw = raw;
			return this;
		}

		public RivenSnapshot build() {
			return rivenSnapshot;
		}

	}

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

	public String getRaw() {
		return raw;
	}

	public void setRaw(String raw) {
		this.raw = raw;
	}

}
