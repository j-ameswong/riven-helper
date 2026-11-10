package com.riven.helper.backend.mapper;

import org.springframework.stereotype.Component;

import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Weapon;
import com.riven.helper.backend.domain.WeaponGroup;
import com.riven.helper.backend.dto.I18nDTO;
import com.riven.helper.backend.dto.WeaponDTO;

@Component
public class WeaponMapper {

	/** A new, unsaved weapon; {@code weaponId} is left null. */
	public Weapon toEntity(WeaponDTO dto) {
		Weapon weapon = new Weapon();
		weapon.setApiWeaponId(dto.id());
		weapon.setSlug(dto.slug());
		weapon.setGameRef(dto.gameRef());
		weapon.setWeaponGroup(dto.group());
		// The API calls archgun rivens rifle; in game they're their own type
		weapon.setRivenType(dto.group() == WeaponGroup.ARCHGUN ? RivenType.ARCHGUN : dto.rivenType());
		weapon.setReqMasteryRank((short) dto.reqMasteryRank());
		weapon.setName(I18nDTO.englishName(dto.i18n(), dto.slug()));
		return weapon;
	}

}
