package com.riven.helper.backend.service;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.riven.helper.backend.domain.RivenAuction;
import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.dto.RivenAuctionDTO;
import com.riven.helper.backend.mapper.RivenAuctionMapper;
import com.riven.helper.backend.mapper.RivenSnapshotMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads a raw riven auction from /v1/auctions or /v1/auctions/search into entities. */
@Component
class RivenAuctionReader {

	private static final Logger log = LoggerFactory.getLogger(RivenAuctionReader.class);

	private final JsonMapper jsonMapper;

	private final RivenAuctionMapper auctionMapper;

	private final RivenSnapshotMapper snapshotMapper;

	RivenAuctionReader(JsonMapper jsonMapper, RivenAuctionMapper auctionMapper, RivenSnapshotMapper snapshotMapper) {
		this.jsonMapper = jsonMapper;
		this.auctionMapper = auctionMapper;
		this.snapshotMapper = snapshotMapper;
	}

	record Riven(RivenAuction auction, RivenSnapshot snapshot) {
	}

	/**
	 * Skips, returning empty, a malformed auction or one for a weapon missing from
	 * {@code weaponIds}.
	 *
	 * @param weaponIds weapon ids keyed by slug
	 */
	Optional<Riven> read(JsonNode node, Map<String, Long> weaponIds) {
		RivenAuctionDTO auction;
		try {
			auction = jsonMapper.treeToValue(node, RivenAuctionDTO.class);
		}
		catch (JacksonException ex) {
			log.warn("Skipping malformed riven auction {}: {}", node.path("id"), ex.getOriginalMessage());
			return Optional.empty();
		}
		Long weaponId = weaponIds.get(auction.item().weaponUrlName());
		if (weaponId == null) {
			log.warn("Skipping riven auction {}: unknown weapon {}", auction.id(), auction.item().weaponUrlName());
			return Optional.empty();
		}
		return Optional.of(
				new Riven(auctionMapper.toEntity(auction, weaponId), snapshotMapper.toEntity(auction, node)));
	}

}
