package com.riven.helper.backend.service;

import static com.riven.helper.backend.dto.V2ResponseDTO.requireData;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.riven.helper.backend.domain.Attribute;
import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Weapon;
import com.riven.helper.backend.domain.WeaponGroup;
import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.I18nDTO;
import com.riven.helper.backend.dto.WeaponDTO;
import com.riven.helper.backend.repository.AttributeRepository;
import com.riven.helper.backend.repository.WeaponRepository;

/**
 * Keeps the weapons and attributes tables in step with warframe.market's riven
 * manifests.
 */
@Service
public class WarframeMarketRivenSyncService {

	private static final Logger log = LoggerFactory.getLogger(WarframeMarketRivenSyncService.class);

	private static final String RIVENS = "rivens";

	private final WarframeMarketRestClient client;

	private final WeaponRepository weaponRepository;

	private final AttributeRepository attributeRepository;

	private final JdbcClient jdbc;

	private final TransactionTemplate transaction;

	WarframeMarketRivenSyncService(WarframeMarketRestClient client, WeaponRepository weaponRepository,
			AttributeRepository attributeRepository, JdbcClient jdbc, TransactionTemplate transaction) {
		this.client = client;
		this.weaponRepository = weaponRepository;
		this.attributeRepository = attributeRepository;
		this.jdbc = jdbc;
		this.transaction = transaction;
	}

	/**
	 * @param rivensHash the hash /v2/versions reported
	 * @param synced false if the stored hash already matched and nothing was fetched
	 */
	public record SyncResult(String rivensHash, boolean synced) {
	}

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
	void scheduledSync() {
		sync(false);
	}

	/**
	 * Syncs the riven manifests if their hash changed, or regardless with
	 * {@code force}.
	 */
	public synchronized SyncResult sync(boolean force) {
		// Only sync if riven hash has changed
		String hash = requireData(client.getVersions()).collections().rivens();
		if (!force && Optional.of(hash).equals(storedHash())) {
			return new SyncResult(hash, false);
		}

		List<WeaponDTO> weapons = requireData(client.getWeapons());
		List<AttributeDTO> attributes = requireData(client.getAttributes());
		if (weapons.isEmpty() || attributes.isEmpty()) {
			throw new IllegalStateException(
					"Empty riven manifest: %d weapons, %d attributes".formatted(weapons.size(), attributes.size()));
		}

		transaction.executeWithoutResult(status -> {
			upsertWeapons(weapons);
			upsertAttributes(attributes);
			storeHash(hash);
		});
		log.info("Synced riven manifests to {}: {} weapons, {} attributes", hash, weapons.size(), attributes.size());
		return new SyncResult(hash, true);
	}

	private void upsertWeapons(List<WeaponDTO> dtos) {
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
			weapon.setName(englishName(dto.i18n(), dto.slug()));
			return weapon;
		}).toList());
	}

	private void upsertAttributes(List<AttributeDTO> dtos) {
		Map<String, Attribute> existing = attributeRepository.findAll().stream()
				.collect(Collectors.toMap(Attribute::getApiAttributeId, Function.identity()));
		attributeRepository.saveAll(dtos.stream().map(dto -> {
			Attribute attribute = existing.getOrDefault(dto.id(), new Attribute());
			attribute.setApiAttributeId(dto.id());
			attribute.setSlug(dto.slug());
			attribute.setGameRef(dto.gameRef());
			attribute.setAttributeGroup(dto.group());
			attribute.setPrefix(dto.prefix());
			attribute.setSuffix(dto.suffix());
			attribute.setUnit(dto.unit());
			attribute.setName(englishName(dto.i18n(), dto.slug()));
			attribute.setExclusiveTo(dto.exclusiveTo());
			attribute.setPositiveOnly(dto.positiveOnly());
			attribute.setNegativeOnly(dto.negativeOnly());
			attribute.setPositiveIsNegative(dto.positiveIsNegative());
			return attribute;
		}).toList());
	}

	private Optional<String> storedHash() {
		return jdbc.sql("SELECT value FROM sync_state WHERE name = ?").param(RIVENS).query(String.class).optional();
	}

	private void storeHash(String hash) {
		jdbc.sql("""
				INSERT INTO sync_state (name, value, updated_at) VALUES (?, ?, now())
				ON CONFLICT (name) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
				""").params(RIVENS, hash).update();
	}

	private static String englishName(Map<String, I18nDTO> i18n, String slug) {
		I18nDTO english = i18n.get("en");
		if (english == null) {
			throw new IllegalStateException("No English name for " + slug);
		}
		return english.name();
	}

}
