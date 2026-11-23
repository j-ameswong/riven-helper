package com.riven.helper.backend.manifest;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.riven.helper.backend.warframemarket.AttributeGroup;
import com.riven.helper.backend.warframemarket.RivenType;
import com.riven.helper.backend.warframemarket.Unit;

/**
 * A riven stat, synced from /v2/riven/attributes. The database schema is owned
 * by Liquibase (see db/changelog/001-weapons-and-attributes.yaml); this class
 * only maps to it.
 */
@Entity
@Table(name = "attributes")
public class Attribute {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long attributeId;

	private String apiAttributeId;

	private String slug;

	private String gameRef;

	@Enumerated(EnumType.STRING)
	private AttributeGroup attributeGroup;

	// Empty string for attributes that only roll as a negative.
	private String prefix;

	private String suffix;

	// Null on stats such as punch_through, range, channeling_damage, parry_angle.
	@Enumerated(EnumType.STRING)
	private @Nullable Unit unit;

	private String name;

	// Null or empty means every riven type can roll it.
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Enumerated(EnumType.STRING)
	private @Nullable List<RivenType> exclusiveTo;

	private boolean positiveOnly;

	private boolean negativeOnly;

	// A positive number is the bad side, e.g. recoil.
	private boolean positiveIsNegative;

	// For JPA
	protected Attribute() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final Attribute attribute = new Attribute();

		private Builder() {
		}

		public Builder attributeId(@Nullable Long attributeId) {
			attribute.attributeId = attributeId;
			return this;
		}

		public Builder apiAttributeId(String apiAttributeId) {
			attribute.apiAttributeId = apiAttributeId;
			return this;
		}

		public Builder slug(String slug) {
			attribute.slug = slug;
			return this;
		}

		public Builder gameRef(String gameRef) {
			attribute.gameRef = gameRef;
			return this;
		}

		public Builder attributeGroup(AttributeGroup attributeGroup) {
			attribute.attributeGroup = attributeGroup;
			return this;
		}

		public Builder prefix(String prefix) {
			attribute.prefix = prefix;
			return this;
		}

		public Builder suffix(String suffix) {
			attribute.suffix = suffix;
			return this;
		}

		public Builder unit(@Nullable Unit unit) {
			attribute.unit = unit;
			return this;
		}

		public Builder name(String name) {
			attribute.name = name;
			return this;
		}

		public Builder exclusiveTo(@Nullable List<RivenType> exclusiveTo) {
			attribute.exclusiveTo = exclusiveTo;
			return this;
		}

		public Builder positiveOnly(boolean positiveOnly) {
			attribute.positiveOnly = positiveOnly;
			return this;
		}

		public Builder negativeOnly(boolean negativeOnly) {
			attribute.negativeOnly = negativeOnly;
			return this;
		}

		public Builder positiveIsNegative(boolean positiveIsNegative) {
			attribute.positiveIsNegative = positiveIsNegative;
			return this;
		}

		public Attribute build() {
			return attribute;
		}

	}

	public @Nullable Long getAttributeId() {
		return attributeId;
	}

	public void setAttributeId(@Nullable Long attributeId) {
		this.attributeId = attributeId;
	}

	public String getApiAttributeId() {
		return apiAttributeId;
	}

	public void setApiAttributeId(String apiAttributeId) {
		this.apiAttributeId = apiAttributeId;
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

	public AttributeGroup getAttributeGroup() {
		return attributeGroup;
	}

	public void setAttributeGroup(AttributeGroup attributeGroup) {
		this.attributeGroup = attributeGroup;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getSuffix() {
		return suffix;
	}

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}

	public @Nullable Unit getUnit() {
		return unit;
	}

	public void setUnit(@Nullable Unit unit) {
		this.unit = unit;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public @Nullable List<RivenType> getExclusiveTo() {
		return exclusiveTo;
	}

	public void setExclusiveTo(@Nullable List<RivenType> exclusiveTo) {
		this.exclusiveTo = exclusiveTo;
	}

	public boolean isPositiveOnly() {
		return positiveOnly;
	}

	public void setPositiveOnly(boolean positiveOnly) {
		this.positiveOnly = positiveOnly;
	}

	public boolean isNegativeOnly() {
		return negativeOnly;
	}

	public void setNegativeOnly(boolean negativeOnly) {
		this.negativeOnly = negativeOnly;
	}

	public boolean isPositiveIsNegative() {
		return positiveIsNegative;
	}

	public void setPositiveIsNegative(boolean positiveIsNegative) {
		this.positiveIsNegative = positiveIsNegative;
	}

}
