package com.riven.helper.backend.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.riven.helper.backend.auction.RivenAttribute;
import com.riven.helper.backend.auction.RivenAuction;
import com.riven.helper.backend.auction.RivenSnapshot;
import com.riven.helper.backend.warframemarket.RivenAuctionDTO;

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

	record Riven(RivenAuction auction, List<RivenAttribute> attributes, RivenSnapshot snapshot) {
	}

	/** Weapon and attribute ids, each keyed by slug, that an auction refers to. */
	record Manifest(Map<String, Long> weaponIds, Map<String, Long> attributeIds) {

		/** False for a malformed auction too, which {@link #read} skips anyway. */
		boolean knows(JsonNode node) {
			JsonNode item = node.path("item");
			return weaponIds.containsKey(item.path("weapon_url_name").asString())
					&& item.path("attributes").valueStream()
							.allMatch(attribute -> attributeIds.containsKey(attribute.path("url_name").asString()));
		}

	}

	/**
	 * Skips, returning empty, a malformed auction or one with a weapon or attribute
	 * missing from {@code manifest}.
	 */
	Optional<Riven> read(JsonNode node, Manifest manifest) {
		RivenAuctionDTO auction;
		try {
			auction = jsonMapper.treeToValue(node, RivenAuctionDTO.class);
		}
		catch (JacksonException ex) {
			log.warn("Skipping malformed riven auction {}: {}", node.path("id"), ex.getOriginalMessage());
			return Optional.empty();
		}
		Long weaponId = manifest.weaponIds().get(auction.item().weaponUrlName());
		if (weaponId == null) {
			log.warn("Skipping riven auction {}: unknown weapon {}", auction.id(), auction.item().weaponUrlName());
			return Optional.empty();
		}
		List<RivenAttribute> attributes = new ArrayList<>();
		for (RivenAuctionDTO.Attribute attribute : auction.item().attributes()) {
			Long attributeId = manifest.attributeIds().get(attribute.urlName());
			if (attributeId == null) {
				log.warn("Skipping riven auction {}: unknown attribute {}", auction.id(), attribute.urlName());
				return Optional.empty();
			}
			attributes.add(auctionMapper.toEntity(auction.id(), attributeId, attribute));
		}
		return Optional.of(new Riven(auctionMapper.toEntity(auction, weaponId), attributes,
				snapshotMapper.toEntity(auction, node)));
	}

}
