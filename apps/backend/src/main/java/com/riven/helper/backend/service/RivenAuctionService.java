package com.riven.helper.backend.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riven.helper.backend.domain.RivenAuction;
import com.riven.helper.backend.dto.RivenAuctionDTO;
import com.riven.helper.backend.repository.RivenAuctionRepository;

import tools.jackson.databind.JsonNode;

/** Owns every write to the riven_auctions table. */
@Service
public class RivenAuctionService {

	private final RivenAuctionRepository auctionRepository;

	private final RivenSnapshotService snapshotService;

	RivenAuctionService(RivenAuctionRepository auctionRepository, RivenSnapshotService snapshotService) {
		this.auctionRepository = auctionRepository;
		this.snapshotService = snapshotService;
	}

	/**
	 * Upserts the auction and records this version of it as a snapshot, together.
	 *
	 * @param weaponId the id of the weapon {@code dto}'s {@code weapon_url_name} names
	 * @param raw the auction object as sent, which {@code dto} was read from
	 */
	@Transactional
	public void archive(RivenAuctionDTO dto, long weaponId, JsonNode raw) {
		RivenAuction auction = new RivenAuction();
		auction.setAuctionId(dto.id());
		auction.setWeaponId(weaponId);
		auction.setOwnerId(dto.owner().id());
		auction.setCreatedAt(dto.created());
		auction.setLastUpdatedAt(dto.updated());
		auctionRepository.upsert(auction);
		snapshotService.record(dto, raw);
	}

	/** The {@code created_at} of the newest auction archived, if any. */
	@Transactional(readOnly = true)
	public Optional<OffsetDateTime> findNewestCreatedAt() {
		return auctionRepository.findNewestCreatedAt();
	}

}
