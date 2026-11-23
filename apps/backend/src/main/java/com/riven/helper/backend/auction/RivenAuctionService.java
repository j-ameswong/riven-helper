package com.riven.helper.backend.auction;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns every write to the riven_auctions and riven_attributes tables. */
@Service
public class RivenAuctionService {

	private final RivenAuctionRepository auctionRepository;

	private final RivenAttributeRepository attributeRepository;

	private final RivenSnapshotService snapshotService;

	RivenAuctionService(RivenAuctionRepository auctionRepository, RivenAttributeRepository attributeRepository,
			RivenSnapshotService snapshotService) {
		this.auctionRepository = auctionRepository;
		this.attributeRepository = attributeRepository;
		this.snapshotService = snapshotService;
	}

	/**
	 * Upserts the auction with its riven's {@code attributes}, and records
	 * {@code snapshot}, a version of it, together. Writes nothing for an auction
	 * already stored as seen.
	 */
	@Transactional
	public void archive(RivenAuction auction, List<RivenAttribute> attributes, RivenSnapshot snapshot) {
		String auctionId = auction.getAuctionId();
		if (!snapshot.getAuctionId().equals(auctionId)
				|| attributes.stream().anyMatch(attribute -> !attribute.getAuctionId().equals(auctionId))) {
			throw new IllegalArgumentException("Snapshot or attributes don't belong to auction " + auctionId);
		}
		if (auctionRepository.upsert(auction) == 0) {
			return;
		}
		attributes.forEach(attributeRepository::insertIfAbsent);
		snapshotService.record(snapshot);
	}

	/**
	 * Marks the weapon's auctions missing from a complete crawl as removed, see
	 * {@link RivenAuctionRepository#markRemoved}.
	 */
	@Transactional
	public int markRemoved(long weaponId, Collection<String> seen, OffsetDateTime crawlStartedAt) {
		return auctionRepository.markRemoved(weaponId, seen, crawlStartedAt);
	}

	/** The {@code created_at} of the newest auction archived, if any. */
	@Transactional(readOnly = true)
	public Optional<OffsetDateTime> findNewestCreatedAt() {
		return auctionRepository.findNewestCreatedAt();
	}

	/** See {@link RivenAuctionRepository#countLive}. */
	@Transactional(readOnly = true)
	public List<ListingCount> countLive(long weaponId) {
		return auctionRepository.countLive(weaponId);
	}

}
