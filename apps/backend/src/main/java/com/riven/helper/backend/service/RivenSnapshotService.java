package com.riven.helper.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riven.helper.backend.domain.RivenSnapshot;
import com.riven.helper.backend.repository.RivenSnapshotRepository;

/** Owns every write to the riven_snapshots table. */
@Service
public class RivenSnapshotService {

	private final RivenSnapshotRepository snapshotRepository;

	RivenSnapshotService(RivenSnapshotRepository snapshotRepository) {
		this.snapshotRepository = snapshotRepository;
	}

	/**
	 * Stores the snapshot unless that version of the auction is already stored.
	 * Its {@link com.riven.helper.backend.domain.RivenAuction} row must exist first.
	 */
	@Transactional
	public void record(RivenSnapshot snapshot) {
		snapshotRepository.insertIfAbsent(snapshot);
	}

}
