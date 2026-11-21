package com.riven.helper.backend.domain;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One stat of a {@link RivenAuction}'s riven. The database schema is owned by
 * Liquibase (see db/changelog/006-immutable-riven-fields.yaml); this class only
 * maps to it.
 */
@Entity
@Table(name = "riven_attributes")
@IdClass(RivenAttribute.Key.class)
public class RivenAttribute {

	public record Key(String auctionId, long attributeId) implements Serializable {
	}

	@Id
	private String auctionId;

	@Id
	private long attributeId;

	// As the game shows it at the listed mod rank, sign included
	private double value;

	// Whether the stat helps the weapon, not the sign of value
	private boolean positive;

	public String getAuctionId() {
		return auctionId;
	}

	public void setAuctionId(String auctionId) {
		this.auctionId = auctionId;
	}

	public long getAttributeId() {
		return attributeId;
	}

	public void setAttributeId(long attributeId) {
		this.attributeId = attributeId;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public boolean isPositive() {
		return positive;
	}

	public void setPositive(boolean positive) {
		this.positive = positive;
	}

}
