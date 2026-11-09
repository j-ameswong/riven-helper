package com.riven.helper.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.riven.helper.backend.TestcontainersConfiguration;
import com.riven.helper.backend.domain.AttributeGroup;
import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Unit;
import com.riven.helper.backend.domain.Weapon;
import com.riven.helper.backend.domain.WeaponGroup;
import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.I18nDTO;
import com.riven.helper.backend.dto.V2ResponseDTO;
import com.riven.helper.backend.dto.VersionsDTO;
import com.riven.helper.backend.dto.WeaponDTO;
import com.riven.helper.backend.repository.AttributeRepository;
import com.riven.helper.backend.repository.WeaponRepository;

/**
 * Runs the sync against the real schema with a mocked API. Not transactional,
 * so the service's own transaction commits or rolls back as it would in
 * production; tables are cleared before each test instead.
 */
@SpringBootTest(properties = "riven-helper.scheduling.enabled=false")
@Import(TestcontainersConfiguration.class)
class WarframeMarketRivenSyncServiceTest {

	@MockitoBean
	WarframeMarketRestClient client;

	@Autowired
	WarframeMarketRivenSyncService syncService;

	@Autowired
	WeaponRepository weaponRepository;

	@Autowired
	AttributeRepository attributeRepository;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clearTables() {
		jdbc.sql("DELETE FROM sync_state").update();
		jdbc.sql("DELETE FROM weapons").update();
		jdbc.sql("DELETE FROM attributes").update();
	}

	@Test
	void newHashUpsertsManifestsAndStoresHash() {
		givenApi("hash-a", List.of(weapon("kulstar", "Kulstar", WeaponGroup.SECONDARY, RivenType.PISTOL),
				weapon("stugun", "Stugun", WeaponGroup.ARCHGUN, RivenType.RIFLE)), List.of(attribute()));

		assertThat(syncService.sync(false)).isEqualTo(new WarframeMarketRivenSyncService.SyncResult("hash-a", true));

		assertThat(weaponRepository.findBySlug("kulstar").orElseThrow().getName()).isEqualTo("Kulstar");
		assertThat(weaponRepository.findBySlug("stugun").orElseThrow().getRivenType()).isEqualTo(RivenType.ARCHGUN);
		assertThat(attributeRepository.findBySlug("recoil").orElseThrow().getExclusiveTo())
				.containsExactly(RivenType.RIFLE, RivenType.ARCHGUN);
		assertThat(storedHash()).isEqualTo("hash-a");
	}

	@Test
	void unchangedHashFetchesNothing() {
		givenApi("hash-a", List.of(weapon("kulstar", "Kulstar", WeaponGroup.SECONDARY, RivenType.PISTOL)),
				List.of(attribute()));
		syncService.sync(false);
		clearInvocations(client);

		assertThat(syncService.sync(false).synced()).isFalse();
		verify(client, never()).getWeapons();
		verify(client, never()).getAttributes();
	}

	@Test
	void forceSyncsEvenWithUnchangedHash() {
		givenApi("hash-a", List.of(weapon("kulstar", "Kulstar", WeaponGroup.SECONDARY, RivenType.PISTOL)),
				List.of(attribute()));
		syncService.sync(false);

		assertThat(syncService.sync(true).synced()).isTrue();
	}

	@Test
	void changedManifestUpdatesRowsInPlace() {
		givenApi("hash-a", List.of(weapon("kulstar", "Kulstar", WeaponGroup.SECONDARY, RivenType.PISTOL)),
				List.of(attribute()));
		syncService.sync(false);
		Long weaponId = weaponRepository.findBySlug("kulstar").orElseThrow().getWeaponId();

		givenApi("hash-b", List.of(weapon("kulstar", "Kulstar Prime", WeaponGroup.SECONDARY, RivenType.PISTOL)),
				List.of(attribute()));
		syncService.sync(false);

		Weapon updated = weaponRepository.findBySlug("kulstar").orElseThrow();
		assertThat(updated.getWeaponId()).isEqualTo(weaponId);
		assertThat(updated.getName()).isEqualTo("Kulstar Prime");
		assertThat(weaponRepository.count()).isOne();
		assertThat(attributeRepository.count()).isOne();
		assertThat(storedHash()).isEqualTo("hash-b");
	}

	@Test
	void failedUpsertKeepsOldHashAndRows() {
		givenApi("hash-a", List.of(weapon("kulstar", "Kulstar", WeaponGroup.SECONDARY, RivenType.PISTOL)),
				List.of(attribute()));
		syncService.sync(false);

		// A second weapon with the same slug violates uq_weapons_slug at commit
		givenApi("hash-b", List.of(weapon("kulstar", "Renamed", WeaponGroup.SECONDARY, RivenType.PISTOL),
				new WeaponDTO("other-id", "kulstar", "/Lotus/x", WeaponGroup.PRIMARY, RivenType.RIFLE, 0,
						Map.of("en", new I18nDTO("Dupe", "", "")))),
				List.of(attribute()));

		assertThatThrownBy(() -> syncService.sync(false)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(storedHash()).isEqualTo("hash-a");
		assertThat(weaponRepository.findBySlug("kulstar").orElseThrow().getName()).isEqualTo("Kulstar");
	}

	@Test
	void emptyManifestWritesNothing() {
		givenApi("hash-a", List.of(), List.of(attribute()));

		assertThatThrownBy(() -> syncService.sync(false)).hasMessageContaining("0 weapons");
		assertThat(jdbc.sql("SELECT count(*) FROM sync_state").query(Long.class).single()).isZero();
	}

	private void givenApi(String rivensHash, List<WeaponDTO> weapons, List<AttributeDTO> attributes) {
		when(client.getVersions()).thenReturn(response(new VersionsDTO("id",
				new VersionsDTO.Collections(null, rivensHash, null, null, null, null, null), null)));
		when(client.getWeapons()).thenReturn(response(weapons));
		when(client.getAttributes()).thenReturn(response(attributes));
	}

	private String storedHash() {
		return jdbc.sql("SELECT value FROM sync_state WHERE name = 'rivens'").query(String.class).single();
	}

	private static <T> V2ResponseDTO<T> response(T data) {
		return new V2ResponseDTO<>("0.25.0", data, null);
	}

	private static WeaponDTO weapon(String slug, String name, WeaponGroup group, RivenType rivenType) {
		return new WeaponDTO("api-" + slug, slug, "/Lotus/Weapons/" + slug, group, rivenType, 5,
				Map.of("en", new I18nDTO(name, "icon.png", "thumb.png")));
	}

	private static AttributeDTO attribute() {
		return new AttributeDTO("api-recoil", "recoil", "WeaponRecoilReductionMod", AttributeGroup.DEFAULT, "Zeti",
				"Mag", Unit.PERCENT, List.of(RivenType.RIFLE, RivenType.ARCHGUN), false, false, true,
				Map.of("en", new I18nDTO("Weapon Recoil", "icon.png", "thumb.png")));
	}

}
