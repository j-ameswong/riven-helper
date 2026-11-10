package com.riven.helper.backend.service;

import static com.riven.helper.backend.dto.V2ResponseDTO.requireData;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.WeaponDTO;

/**
 * Keeps the weapons and attributes tables in step with warframe.market's riven
 * manifests.
 */
@Service
public class WarframeMarketRivenSyncService {

	private static final Logger log = LoggerFactory.getLogger(WarframeMarketRivenSyncService.class);

	private static final String RIVENS = "rivens";

	private final WarframeMarketRestClient client;

	private final WeaponService weaponService;

	private final AttributeService attributeService;

	private final JdbcClient jdbc;

	private final TransactionTemplate transaction;

	WarframeMarketRivenSyncService(WarframeMarketRestClient client, WeaponService weaponService,
			AttributeService attributeService, JdbcClient jdbc, TransactionTemplate transaction) {
		this.client = client;
		this.weaponService = weaponService;
		this.attributeService = attributeService;
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
			weaponService.upsertAll(weapons);
			attributeService.upsertAll(attributes);
			storeHash(hash);
		});
		log.info("Synced riven manifests to {}: {} weapons, {} attributes", hash, weapons.size(), attributes.size());
		return new SyncResult(hash, true);
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

}
