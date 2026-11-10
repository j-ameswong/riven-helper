package com.riven.helper.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Weapon;
import com.riven.helper.backend.domain.WeaponGroup;
import com.riven.helper.backend.dto.I18nDTO;
import com.riven.helper.backend.dto.WeaponDTO;
import com.riven.helper.backend.repository.WeaponRepository;

/** Owns every write to the weapons table. */
@Service
public class WeaponService {

	private final WeaponRepository weaponRepository;

	WeaponService(WeaponRepository weaponRepository) {
		this.weaponRepository = weaponRepository;
	}

	/** Inserts new weapons and updates existing ones in place, matched on the API id. */
	@Transactional
	public void upsertAll(List<WeaponDTO> dtos) {
		Map<String, Weapon> existing = weaponRepository.findAll().stream()
				.collect(Collectors.toMap(Weapon::getApiWeaponId, Function.identity()));
		weaponRepository.saveAll(dtos.stream().map(dto -> {
			Weapon weapon = existing.getOrDefault(dto.id(), new Weapon());
			weapon.setApiWeaponId(dto.id());
			weapon.setSlug(dto.slug());
			weapon.setGameRef(dto.gameRef());
			weapon.setWeaponGroup(dto.group());
			// The API calls archgun rivens rifle; in game they're their own type
			weapon.setRivenType(dto.group() == WeaponGroup.ARCHGUN ? RivenType.ARCHGUN : dto.rivenType());
			weapon.setReqMasteryRank((short) dto.reqMasteryRank());
			weapon.setName(I18nDTO.englishName(dto.i18n(), dto.slug()));
			return weapon;
		}).toList());
	}

	/** Every weapon's id, keyed by its slug (an auction's {@code weapon_url_name}). */
	@Transactional(readOnly = true)
	public Map<String, Long> findIdsBySlug() {
		return weaponRepository.findAll().stream()
				.collect(Collectors.toMap(Weapon::getSlug, weapon -> Objects.requireNonNull(weapon.getWeaponId())));
	}

}
