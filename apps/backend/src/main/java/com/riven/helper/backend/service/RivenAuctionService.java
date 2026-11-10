package com.riven.helper.backend.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riven.helper.backend.domain.RivenAuction;
import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.repository.RivenAuctionRepository;

/** Owns every write to the riven_auctions table. */
@Service
public class RivenAuctionService {

	private final RivenAuctionRepository auctionRepository;

	private final RivenSnapshotService snapshotService;

	RivenAuctionService(RivenAuctionRepository auctionRepository, RivenSnapshotService snapshotService) {
		this.auctionRepository = auctionRepository;
		this.snapshotService = snapshotService;
	}

	/** Upserts the auction and records {@code snapshot}, a version of it, together. */
	@Transactional
	public void archive(RivenAuction auction, RivenSnapshot snapshot) {
		if (!snapshot.getAuctionId().equals(auction.getAuctionId())) {
			throw new IllegalArgumentException("Snapshot of auction %s doesn't belong to auction %s"
					.formatted(snapshot.getAuctionId(), auction.getAuctionId()));
		}
		auctionRepository.upsert(auction);
		snapshotService.record(snapshot);
	}

	/** The {@code created_at} of the newest auction archived, if any. */
	@Transactional(readOnly = true)
	public Optional<OffsetDateTime> findNewestCreatedAt() {
		return auctionRepository.findNewestCreatedAt();
	}

}
