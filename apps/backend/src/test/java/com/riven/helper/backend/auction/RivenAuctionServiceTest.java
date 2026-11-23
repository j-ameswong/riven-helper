package com.riven.helper.backend.auction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

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

	@Test
	void writesNothingMoreForAnAuctionAlreadyStoredAsSeen() {
		RivenAuction auction = RivenAuction.builder().auctionId("a1").build();
		when(auctionRepository.upsert(auction)).thenReturn(0);

		auctionService.archive(auction, List.of(attributeOf("a1")), snapshotOf("a1"));

		verify(attributeRepository, never()).insertIfAbsent(any());
		verify(snapshotService, never()).record(any());
	}

	private void assertRejectedBeforeWriting(List<RivenAttribute> attributes, RivenSnapshot snapshot) {
		RivenAuction auction = RivenAuction.builder().auctionId("a1").build();

		assertThatThrownBy(() -> auctionService.archive(auction, attributes, snapshot))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("a1");
		verify(auctionRepository, never()).upsert(any());
		verify(attributeRepository, never()).insertIfAbsent(any());
		verify(snapshotService, never()).record(any());
	}

	private static RivenAttribute attributeOf(String auctionId) {
		return RivenAttribute.builder().auctionId(auctionId).build();
	}

	private static RivenSnapshot snapshotOf(String auctionId) {
		return RivenSnapshot.builder().auctionId(auctionId).build();
	}

}
