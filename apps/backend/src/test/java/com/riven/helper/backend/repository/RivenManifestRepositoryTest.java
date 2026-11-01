package com.riven.helper.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.riven.helper.backend.TestcontainersConfiguration;
import com.riven.helper.backend.domain.Attribute;
import com.riven.helper.backend.domain.AttributeGroup;
import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Unit;
import com.riven.helper.backend.domain.Weapon;
import com.riven.helper.backend.domain.WeaponGroup;

/**
 * Persists sample rows against the real Liquibase schema (via Testcontainers)
 * to prove the entities map to it: snake_case column names, lowercase enum
 * values accepted by the CHECK constraints, and the exclusive_to text[] array.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RivenManifestRepositoryTest {

	@Autowired
	TestEntityManager entityManager;

	@Autowired
	WeaponRepository weaponRepository;

	@Autowired
	AttributeRepository attributeRepository;

	@Test
	void weaponRoundTripsAllColumns() {
		Weapon weapon = sampleWeapon("kulstar", WeaponGroup.SECONDARY, RivenType.PISTOL);
		weapon.setGameRef("/Lotus/Weapons/Grineer/Pistols/GrnTorpedoPistol/GrnTorpedoPistol");
		weapon.setReqMasteryRank((short) 5);
		weapon.setName("Kulstar");

		weaponRepository.saveAndFlush(weapon);
		entityManager.clear();

		Weapon bySlug = weaponRepository.findBySlug("kulstar").orElseThrow();
		assertThat(weaponRepository.findByApiWeaponId(weapon.getApiWeaponId())).hasValueSatisfying(
				found -> assertThat(found.getWeaponId()).isEqualTo(bySlug.getWeaponId()));

		assertThat(bySlug.getWeaponId()).isNotNull();
		assertThat(bySlug.getApiWeaponId()).isEqualTo(weapon.getApiWeaponId());
		assertThat(bySlug.getSlug()).isEqualTo("kulstar");
		assertThat(bySlug.getGameRef()).isEqualTo("/Lotus/Weapons/Grineer/Pistols/GrnTorpedoPistol/GrnTorpedoPistol");
		assertThat(bySlug.getWeaponGroup()).isEqualTo(WeaponGroup.SECONDARY);
		assertThat(bySlug.getRivenType()).isEqualTo(RivenType.PISTOL);
		assertThat(bySlug.getReqMasteryRank()).isEqualTo((short) 5);
		assertThat(bySlug.getName()).isEqualTo("Kulstar");
	}

	@Test
	void archgunWeaponRoundTrips() {
		weaponRepository.saveAndFlush(sampleWeapon("stugun", WeaponGroup.ARCHGUN, RivenType.ARCHGUN));
		entityManager.clear();

		Weapon saved = weaponRepository.findBySlug("stugun").orElseThrow();
		assertThat(saved.getWeaponGroup()).isEqualTo(WeaponGroup.ARCHGUN);
		assertThat(saved.getRivenType()).isEqualTo(RivenType.ARCHGUN);
	}

	@ParameterizedTest
	@CsvSource({
			"ARCHGUN, RIFLE",
			"PRIMARY, ARCHGUN",
	})
	void weaponWithMismatchedArchgunRivenTypeIsRejected(WeaponGroup weaponGroup, RivenType rivenType) {
		Weapon weapon = sampleWeapon("mismatched-archgun", weaponGroup, rivenType);

		assertViolatesConstraint("ck_weapons_archgun_riven_type", () -> weaponRepository.saveAndFlush(weapon));
	}

	@Test
	void attributeWithExclusiveToRoundTrips() {
		Attribute attribute = sampleAttribute("base_damage_/_melee_damage");
		attribute.setGameRef("WeaponDamage");
		attribute.setAttributeGroup(AttributeGroup.MELEE);
		attribute.setPrefix("Crisis");
		attribute.setSuffix("Bane");
		attribute.setUnit(Unit.MULTIPLY);
		attribute.setName("Melee Damage");
		attribute.setExclusiveTo(List.of(RivenType.MELEE, RivenType.ZAW));
		attribute.setPositiveIsNegative(true);

		attributeRepository.saveAndFlush(attribute);
		entityManager.clear();

		Attribute bySlug = attributeRepository.findBySlug("base_damage_/_melee_damage").orElseThrow();
		assertThat(attributeRepository.findByApiAttributeId(attribute.getApiAttributeId())).hasValueSatisfying(
				found -> assertThat(found.getAttributeId()).isEqualTo(bySlug.getAttributeId()));

		assertThat(bySlug.getAttributeId()).isNotNull();
		assertThat(bySlug.getApiAttributeId()).isEqualTo(attribute.getApiAttributeId());
		assertThat(bySlug.getSlug()).isEqualTo("base_damage_/_melee_damage");
		assertThat(bySlug.getGameRef()).isEqualTo("WeaponDamage");
		assertThat(bySlug.getAttributeGroup()).isEqualTo(AttributeGroup.MELEE);
		assertThat(bySlug.getPrefix()).isEqualTo("Crisis");
		assertThat(bySlug.getSuffix()).isEqualTo("Bane");
		assertThat(bySlug.getUnit()).isEqualTo(Unit.MULTIPLY);
		assertThat(bySlug.getName()).isEqualTo("Melee Damage");
		assertThat(bySlug.getExclusiveTo()).containsExactly(RivenType.MELEE, RivenType.ZAW);
		assertThat(bySlug.isPositiveOnly()).isFalse();
		assertThat(bySlug.isNegativeOnly()).isFalse();
		assertThat(bySlug.isPositiveIsNegative()).isTrue();
	}

	@Test
	void attributeWithoutExclusiveToRoundTrips() {
		Attribute attribute = sampleAttribute("headshot_damage");
		attribute.setPositiveOnly(true);

		attributeRepository.saveAndFlush(attribute);
		entityManager.clear();

		Attribute saved = attributeRepository.findBySlug("headshot_damage").orElseThrow();
		assertThat(saved.getExclusiveTo()).isNull();
		assertThat(saved.isPositiveOnly()).isTrue();
		assertThat(saved.isNegativeOnly()).isFalse();
		assertThat(saved.isPositiveIsNegative()).isFalse();
	}

	@Test
	void attributeWithEmptyExclusiveToRoundTrips() {
		Attribute attribute = sampleAttribute("headshot_damage");
		attribute.setExclusiveTo(List.of());

		attributeRepository.saveAndFlush(attribute);
		entityManager.clear();

		Attribute saved = attributeRepository.findBySlug("headshot_damage").orElseThrow();
		assertThat(saved.getExclusiveTo()).isEmpty();
	}

	@Test
	void attributeThatIsBothPositiveAndNegativeOnlyIsRejected() {
		Attribute attribute = sampleAttribute("headshot_damage");
		attribute.setPositiveOnly(true);
		attribute.setNegativeOnly(true);

		assertViolatesConstraint("ck_attributes_positive_or_negative_only",
				() -> attributeRepository.saveAndFlush(attribute));
	}

	private static Weapon sampleWeapon(String slug, WeaponGroup weaponGroup, RivenType rivenType) {
		Weapon weapon = new Weapon();
		weapon.setApiWeaponId("api-" + slug);
		weapon.setSlug(slug);
		weapon.setGameRef("/Lotus/Weapons/" + slug);
		weapon.setWeaponGroup(weaponGroup);
		weapon.setRivenType(rivenType);
		weapon.setReqMasteryRank((short) 11);
		weapon.setName(slug);
		return weapon;
	}

	private static Attribute sampleAttribute(String slug) {
		Attribute attribute = new Attribute();
		attribute.setApiAttributeId("api-" + slug);
		attribute.setSlug(slug);
		attribute.setGameRef("WeaponDamagePercent");
		attribute.setAttributeGroup(AttributeGroup.DEFAULT);
		attribute.setPrefix("Mach");
		attribute.setSuffix("Turret");
		attribute.setUnit(Unit.PERCENT);
		attribute.setName(slug);
		return attribute;
	}

	private static void assertViolatesConstraint(String constraintName, ThrowingCallable save) {
		assertThatThrownBy(save)
				.isInstanceOf(DataIntegrityViolationException.class)
				.cause()
				.isInstanceOfSatisfying(ConstraintViolationException.class,
						cause -> assertThat(cause.getConstraintName()).isEqualTo(constraintName));
	}

}
