package com.riven.helper.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riven.helper.backend.domain.Weapon;
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
	public void upsertAll(List<Weapon> weapons) {
		Map<String, Weapon> existing = weaponRepository.findAll().stream()
				.collect(Collectors.toMap(Weapon::getApiWeaponId, Function.identity()));
		weapons.forEach(weapon -> {
			Weapon current = existing.get(weapon.getApiWeaponId());
			// An id makes saveAll merge onto the existing row instead of inserting
			weapon.setWeaponId(current == null ? null : current.getWeaponId());
		});
		weaponRepository.saveAll(weapons);
	}

	/** Every weapon's id, keyed by its slug (an auction's {@code weapon_url_name}). */
	@Transactional(readOnly = true)
	public Map<String, Long> findIdsBySlug() {
		return weaponRepository.findAll().stream()
				.collect(Collectors.toMap(Weapon::getSlug, weapon -> Objects.requireNonNull(weapon.getWeaponId())));
	}

}
