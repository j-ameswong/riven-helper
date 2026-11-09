package com.riven.helper.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.riven.helper.backend.domain.RivenType;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads v2 envelopes shaped like the samples in docs/warframe-market-api.md
 * with Jackson's defaults, which are stricter than Spring Boot's mapper.
 */
class V2ResponseDTOTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void readsVersions() {
		V2ResponseDTO<VersionsDTO> response = mapper.readValue("""
				{
				  "apiVersion": "0.25.0",
				  "data": {
				    "id": "6addf665f8e7a2554e52ef5c",
				    "apps": { "ios": "0.0.1", "android": "0.0.1", "minIos": "", "minAndroid": "" },
				    "collections": {
				      "items": "MjAyNi0xMC0yNVQxMjozMDoyOA==",
				      "rivens": "MjAyNi0xMC0yMVQxNjowMDoxMA==",
				      "liches": "bGljaGVz", "sisters": "c2lzdGVycw==", "missions": "bWlzc2lvbnM=",
				      "npcs": "bnBjcw==", "locations": "bG9jYXRpb25z"
				    },
				    "updatedAt": "2026-10-25T12:30:28Z"
				  },
				  "error": null
				}
				""", new TypeReference<V2ResponseDTO<VersionsDTO>>() {
				});

		assertThat(response.apiVersion()).isEqualTo("0.25.0");
		assertThat(response.error()).isNull();
		assertThat(response.data()).isEqualTo(new VersionsDTO(
				"6addf665f8e7a2554e52ef5c",
				new VersionsDTO.Collections(
						"MjAyNi0xMC0yNVQxMjozMDoyOA==",
						"MjAyNi0xMC0yMVQxNjowMDoxMA==",
						"bGljaGVz", "c2lzdGVycw==", "bWlzc2lvbnM=", "bnBjcw==", "bG9jYXRpb25z"),
				Instant.parse("2026-10-25T12:30:28Z")));
	}

	@Test
	void readsArrayData() {
		V2ResponseDTO<List<WeaponDTO>> response = mapper.readValue("""
				{
				  "apiVersion": "0.25.0",
				  "data": [
				    { "id": "a", "slug": "kulstar", "gameRef": "/Lotus/Kulstar", "group": "secondary",
				      "rivenType": "pistol", "reqMasteryRank": 5, "i18n": {} },
				    { "id": "b", "slug": "rubico", "gameRef": "/Lotus/Rubico", "group": "primary",
				      "rivenType": "rifle", "reqMasteryRank": 4, "i18n": {} }
				  ],
				  "error": null
				}
				""", new TypeReference<V2ResponseDTO<List<WeaponDTO>>>() {
				});

		assertThat(response.data()).extracting(WeaponDTO::slug).containsExactly("kulstar", "rubico");
		assertThat(response.data()).extracting(WeaponDTO::rivenType).containsExactly(RivenType.PISTOL, RivenType.RIFLE);
	}

	@Test
	void keepsTheErrorAsRawJson() {
		V2ResponseDTO<VersionsDTO> response = mapper.readValue("""
				{ "apiVersion": "0.25.0", "data": null, "error": { "request": ["app.something.went_wrong"] } }
				""", new TypeReference<V2ResponseDTO<VersionsDTO>>() {
				});

		assertThat(response.data()).isNull();
		assertThat(response.error().get("request").get(0).asString()).isEqualTo("app.something.went_wrong");
		assertThatThrownBy(() -> V2ResponseDTO.requireData(response))
				.hasMessageContaining("app.something.went_wrong");
	}

	@Test
	void requireDataRejectsAnEmptyBodyOrMissingDataAndReturnsPresentData() {
		assertThatThrownBy(() -> V2ResponseDTO.requireData(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> V2ResponseDTO.requireData(new V2ResponseDTO<>("0.25.0", null, null)))
				.isInstanceOf(IllegalStateException.class);
		assertThat(V2ResponseDTO.requireData(new V2ResponseDTO<>("0.25.0", List.of(), null))).isEmpty();
	}

	@Test
	void rejectsNullListElements() {
		assertThatThrownBy(() -> mapper.readValue("""
				{ "apiVersion": "0.25.0", "data": [null], "error": null }
				""", new TypeReference<V2ResponseDTO<List<WeaponDTO>>>() {
				})).isInstanceOf(InvalidNullException.class);
	}

	@Test
	void rejectsVersionsWithoutARivensHash() {
		assertThatThrownBy(() -> mapper.readValue("""
				{ "id": "a", "collections": { "items": "aXRlbXM=" } }
				""", VersionsDTO.class)).isInstanceOf(InvalidNullException.class);
	}

}
