package com.riven.helper.backend.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.riven.helper.backend.service.WarframeMarketRivenSyncService;
import com.riven.helper.backend.service.WarframeMarketRivenSyncService.SyncResult;

@RestController
@RequestMapping("/api/manifests/rivens")
class RivenManifestController {

	private final WarframeMarketRivenSyncService syncService;

	RivenManifestController(WarframeMarketRivenSyncService syncService) {
		this.syncService = syncService;
	}

	/** {@code force} re-syncs even if the hash is unchanged, e.g. after changing the import mapping. */
	@PostMapping("/sync")
	SyncResult sync(@RequestParam(defaultValue = "false") boolean force) {
		return syncService.sync(force);
	}

}
