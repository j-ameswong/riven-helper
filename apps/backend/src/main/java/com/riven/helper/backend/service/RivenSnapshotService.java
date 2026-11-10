package com.riven.helper.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.dto.RivenAuctionDTO;
import com.riven.helper.backend.repository.RivenSnapshotRepository;

import tools.jackson.databind.JsonNode;

/** Owns every write to the riven_snapshots table. */
@Service
public class RivenSnapshotService {

	private final RivenSnapshotRepository snapshotRepository;

	RivenSnapshotService(RivenSnapshotRepository snapshotRepository) {
		this.snapshotRepository = snapshotRepository;
	}

	/**
	 * Stores this version of the auction, unless a snapshot with the same
	 * {@code updated} already exists. Its {@link com.riven.helper.backend.domain.RivenAuction}
	 * row must exist first.
	 *
	 * @param raw the auction object as sent, which {@code dto} was read from
	 */
	@Transactional
	public void record(RivenAuctionDTO dto, JsonNode raw) {
		RivenAuctionDTO.Item item = dto.item();
		RivenSnapshot snapshot = new RivenSnapshot();
		snapshot.setAuctionId(dto.id());
		snapshot.setUpdatedAt(dto.updated());
		snapshot.setStartingPrice(dto.startingPrice());
		snapshot.setBuyoutPrice(dto.buyoutPrice());
		snapshot.setDirectSell(dto.isDirectSell());
		snapshot.setReRolls(item.reRolls());
		snapshot.setModRank((short) item.modRank());
		snapshot.setMasteryLevel((short) item.masteryLevel());
		snapshot.setPolarity(item.polarity());
		snapshot.setAttributes(raw.path("item").path("attributes").toString());
		snapshot.setRaw(raw.toString());
		snapshotRepository.insertIfAbsent(snapshot);
	}

}
