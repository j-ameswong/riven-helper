package com.riven.helper.backend.service;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.riven.helper.backend.dto.AttributeDTO;
import com.riven.helper.backend.dto.V2ResponseDTO;
import com.riven.helper.backend.dto.VersionsDTO;
import com.riven.helper.backend.dto.WeaponDTO;

/**
 * The warframe.market API, documented in docs/warframe-market-api.md. Spring
 * generates the implementation, see {@link WarframeMarketClientConfiguration}.
 *
 * <p>Every call waits for a rate limit slot, so it can block. A non-2xx response
 * throws a {@code RestClientResponseException}.
 */
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
interface WarframeMarketRestClient {

	@GetExchange("/v2/versions")
	V2ResponseDTO<VersionsDTO> getVersions();

	@GetExchange("/v2/riven/weapons")
	V2ResponseDTO<List<WeaponDTO>> getWeapons();

	@GetExchange("/v2/riven/attributes")
	V2ResponseDTO<List<AttributeDTO>> getAttributes();

}
