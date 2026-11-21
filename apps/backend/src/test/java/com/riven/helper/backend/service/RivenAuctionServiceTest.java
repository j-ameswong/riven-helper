package com.riven.helper.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.riven.helper.backend.domain.RivenAttribute;
import com.riven.helper.backend.domain.RivenAuction;
import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.repository.RivenAttributeRepository;
import com.riven.helper.backend.repository.RivenAuctionRepository;

class RivenAuctionServiceTest {

	private final RivenAuctionRepository auctionRepository = mock(RivenAuctionRepository.class);

	private final RivenAttributeRepository attributeRepository = mock(RivenAttributeRepository.class);

	private final RivenSnapshotService snapshotService = mock(RivenSnapshotService.class);

	private final RivenAuctionService auctionService = new RivenAuctionService(auctionRepository, attributeRepository,
			snapshotService);

	@Test
	void rejectsASnapshotOfAnotherAuctionBeforeWriting() {
		assertRejectedBeforeWriting(List.of(attributeOf("a1")), snapshotOf("a2"));
	}

	@Test
	void rejectsAnAttributeOfAnotherAuctionBeforeWriting() {
		assertRejectedBeforeWriting(List.of(attributeOf("a1"), attributeOf("a2")), snapshotOf("a1"));
	}

	private void assertRejectedBeforeWriting(List<RivenAttribute> attributes, RivenSnapshot snapshot) {
		RivenAuction auction = new RivenAuction();
		auction.setAuctionId("a1");

		assertThatThrownBy(() -> auctionService.archive(auction, attributes, snapshot))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("a1");
		verify(auctionRepository, never()).upsert(any());
		verify(attributeRepository, never()).insertIfAbsent(any());
		verify(snapshotService, never()).record(any());
	}

	private static RivenAttribute attributeOf(String auctionId) {
		RivenAttribute attribute = new RivenAttribute();
		attribute.setAuctionId(auctionId);
		return attribute;
	}

	private static RivenSnapshot snapshotOf(String auctionId) {
		RivenSnapshot snapshot = new RivenSnapshot();
		snapshot.setAuctionId(auctionId);
		return snapshot;
	}

}
