package com.riven.helper.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.riven.helper.backend.domain.AttributeGroup;
import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Unit;
import com.riven.helper.backend.domain.WeaponGroup;

import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads payloads shaped like the samples in docs/warframe-market-api.md with
 * Jackson's defaults, which are stricter than Spring Boot's mapper.
 */
class RivenManifestDTOTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void readsWeaponAndIgnoresDisposition() {
		WeaponDTO weapon = mapper.readValue("""
				{
				  "id": "5c5ca81696e8d2003834fdcc",
				  "slug": "kulstar",
				  "gameRef": "/Lotus/Weapons/Grineer/Pistols/GrnTorpedoPistol/GrnTorpedoPistol",
				  "group": "secondary",
				  "rivenType": "pistol",
				  "disposition": 1.3,
				  "reqMasteryRank": 5,
				  "i18n": { "en": { "name": "Kulstar", "icon": "items/images/en/kulstar.png", "thumb": "items/images/en/thumbs/kulstar.128x128.png" } }
				}
				""", WeaponDTO.class);

		assertThat(weapon).isEqualTo(new WeaponDTO(
				"5c5ca81696e8d2003834fdcc",
				"kulstar",
				"/Lotus/Weapons/Grineer/Pistols/GrnTorpedoPistol/GrnTorpedoPistol",
				WeaponGroup.SECONDARY,
				RivenType.PISTOL,
				5,
				Map.of("en", new I18nDTO("Kulstar", "items/images/en/kulstar.png",
						"items/images/en/thumbs/kulstar.128x128.png"))));
	}

	@Test
	void readsAttributeWithEveryOptionalField() {
		AttributeDTO attribute = mapper.readValue("""
				{
				  "id": "5c5ca81a96e8d2003834fe7a",
				  "slug": "recoil",
				  "gameRef": "WeaponRecoilReductionMod",
				  "group": "default",
				  "prefix": "Zeti",
				  "suffix": "Mag",
				  "unit": "percent",
				  "exclusiveTo": ["rifle", "archgun"],
				  "positiveOnly": true,
				  "negativeOnly": true,
				  "positiveIsNegative": true,
				  "i18n": { "en": { "name": "Weapon Recoil", "icon": "icon.png", "thumb": "thumb.png" } }
				}
				""", AttributeDTO.class);

		assertThat(attribute.group()).isEqualTo(AttributeGroup.DEFAULT);
		assertThat(attribute.unit()).isEqualTo(Unit.PERCENT);
		assertThat(attribute.exclusiveTo()).containsExactly(RivenType.RIFLE, RivenType.ARCHGUN);
		assertThat(attribute.positiveOnly()).isTrue();
		assertThat(attribute.negativeOnly()).isTrue();
		assertThat(attribute.positiveIsNegative()).isTrue();
		assertThat(attribute.i18n().get("en").name()).isEqualTo("Weapon Recoil");
	}

	@Test
	void readsAttributeWithOptionalFieldsLeftOut() {
		AttributeDTO attribute = mapper.readValue("""
				{
				  "id": "5c5ca81a96e8d2003834fe60",
				  "slug": "range",
				  "gameRef": "WeaponMeleeRangeIncMod",
				  "group": "melee",
				  "prefix": "Locti",
				  "suffix": "Tor",
				  "i18n": { "en": { "name": "Range", "icon": "icon.png", "thumb": "thumb.png" } }
				}
				""", AttributeDTO.class);

		assertThat(attribute.group()).isEqualTo(AttributeGroup.MELEE);
		assertThat(attribute.unit()).isNull();
		assertThat(attribute.exclusiveTo()).isNull();
		assertThat(attribute.positiveOnly()).isFalse();
		assertThat(attribute.negativeOnly()).isFalse();
		assertThat(attribute.positiveIsNegative()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// slug missing
			"""
			{ "id": "a", "gameRef": "g", "group": "primary", "rivenType": "rifle", "reqMasteryRank": 0,
			  "i18n": { "en": { "name": "A" } } }
			""",
			// slug explicitly null
			"""
			{ "id": "a", "slug": null, "gameRef": "g", "group": "primary", "rivenType": "rifle",
			  "reqMasteryRank": 0, "i18n": { "en": { "name": "A" } } }
			""",
			// null i18n entry
			"""
			{ "id": "a", "slug": "a", "gameRef": "g", "group": "primary", "rivenType": "rifle",
			  "reqMasteryRank": 0, "i18n": { "en": null } }
			""",
			// name missing
			"""
			{ "id": "a", "slug": "a", "gameRef": "g", "group": "primary", "rivenType": "rifle",
			  "reqMasteryRank": 0, "i18n": { "en": {} } }
			""",
	})
	void rejectsWeaponWithANullRequiredField(String json) {
		assertThatThrownBy(() -> mapper.readValue(json, WeaponDTO.class)).isInstanceOf(InvalidNullException.class);
	}

	@Test
	void rejectsAttributeWithANullExclusiveToEntry() {
		assertThatThrownBy(() -> mapper.readValue("""
				{ "id": "a", "slug": "a", "gameRef": "g", "group": "default", "prefix": "", "suffix": "s",
				  "exclusiveTo": [null], "i18n": { "en": { "name": "A" } } }
				""", AttributeDTO.class)).isInstanceOf(InvalidNullException.class);
	}

}
