package com.riven.helper.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.riven.helper.backend.domain.Weapon;

public interface WeaponRepository extends JpaRepository<Weapon, Long> {

	Optional<Weapon> findByApiWeaponId(String apiWeaponId);

	Optional<Weapon> findBySlug(String slug);

}
