package com.riven.helper.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.riven.helper.backend.domain.Attribute;
import com.riven.helper.backend.domain.AttributeGroup;
import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Unit;
import com.riven.helper.backend.domain.Weapon;
import com.riven.helper.backend.domain.WeaponGroup;
import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.I18nDTO;
import com.riven.helper.backend.dto.WeaponDTO;

class ManifestMapperTest {

	private final WeaponMapper weaponMapper = new WeaponMapper();

	private final AttributeMapper attributeMapper = new AttributeMapper();

	@Test
	void mapsAWeaponWithItsEnglishName() {
		Weapon weapon = weaponMapper.toEntity(new WeaponDTO("api-kulstar", "kulstar", "/Lotus/Kulstar",
				WeaponGroup.SECONDARY, RivenType.PISTOL, 5, Map.of("en", new I18nDTO("Kulstar", null, null))));

		assertThat(weapon.getWeaponId()).isNull();
		assertThat(weapon.getApiWeaponId()).isEqualTo("api-kulstar");
		assertThat(weapon.getRivenType()).isEqualTo(RivenType.PISTOL);
		assertThat(weapon.getReqMasteryRank()).isEqualTo((short) 5);
		assertThat(weapon.getName()).isEqualTo("Kulstar");
	}

	@Test
	void remapsArchgunsToTheirOwnRivenType() {
		Weapon weapon = weaponMapper.toEntity(new WeaponDTO("api-stugun", "stugun", "/Lotus/Stugun",
				WeaponGroup.ARCHGUN, RivenType.RIFLE, 0, Map.of("en", new I18nDTO("Stugun", null, null))));

		assertThat(weapon.getRivenType()).isEqualTo(RivenType.ARCHGUN);
	}

	@Test
	void rejectsAWeaponWithNoEnglishName() {
		assertThatThrownBy(() -> weaponMapper.toEntity(new WeaponDTO("api-x", "x", "/Lotus/x",
				WeaponGroup.PRIMARY, RivenType.RIFLE, 0, Map.of("fr", new I18nDTO("X", null, null)))))
				.hasMessageContaining("No English name for x");
	}

	@Test
	void mapsAnAttribute() {
		Attribute attribute = attributeMapper.toEntity(new AttributeDTO("api-recoil", "recoil",
				"WeaponRecoilReductionMod", AttributeGroup.DEFAULT, "Zeti", "Mag", Unit.PERCENT,
				List.of(RivenType.RIFLE), false, false, true, Map.of("en", new I18nDTO("Weapon Recoil", null, null))));

		assertThat(attribute.getAttributeId()).isNull();
		assertThat(attribute.getSlug()).isEqualTo("recoil");
		assertThat(attribute.getName()).isEqualTo("Weapon Recoil");
		assertThat(attribute.getExclusiveTo()).containsExactly(RivenType.RIFLE);
		assertThat(attribute.isPositiveIsNegative()).isTrue();
	}

}
