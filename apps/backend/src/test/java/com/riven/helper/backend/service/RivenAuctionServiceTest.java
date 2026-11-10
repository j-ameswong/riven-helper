package com.riven.helper.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.riven.helper.backend.domain.RivenAuction;
import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.repository.RivenAuctionRepository;

class RivenAuctionServiceTest {

	private final RivenAuctionRepository auctionRepository = mock(RivenAuctionRepository.class);

	private final RivenSnapshotService snapshotService = mock(RivenSnapshotService.class);

	private final RivenAuctionService auctionService = new RivenAuctionService(auctionRepository, snapshotService);

	@Test
	void rejectsASnapshotOfAnotherAuctionBeforeWriting() {
		RivenAuction auction = new RivenAuction();
		auction.setAuctionId("a1");
		RivenSnapshot snapshot = new RivenSnapshot();
		snapshot.setAuctionId("a2");

		assertThatThrownBy(() -> auctionService.archive(auction, snapshot))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("a2");
		verify(auctionRepository, never()).upsert(any());
		verify(snapshotService, never()).record(any());
	}

}
