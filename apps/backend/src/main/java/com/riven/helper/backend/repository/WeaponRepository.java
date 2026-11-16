package com.riven.helper.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.riven.helper.backend.domain.Weapon;

public interface WeaponRepository extends JpaRepository<Weapon, Long> {

	Optional<Weapon> findByApiWeaponId(String apiWeaponId);

	Optional<Weapon> findBySlug(String slug);

	/** The weapon whose riven crawl is most overdue, never-crawled weapons first. */
	@Query(nativeQuery = true, value = "SELECT * FROM weapons ORDER BY last_crawled_at NULLS FIRST, weapon_id LIMIT 1")
	Optional<Weapon> findNextToCrawl();

	@Modifying
	@Query(nativeQuery = true, value = "UPDATE weapons SET last_crawled_at = now() WHERE weapon_id = :weaponId")
	void markCrawled(@Param("weaponId") long weaponId);

}
