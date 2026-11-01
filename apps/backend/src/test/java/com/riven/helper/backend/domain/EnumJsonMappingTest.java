package com.riven.helper.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The enums read and write the lowercase values used by the warframe.market
 * API, not their constant names.
 */
class EnumJsonMappingTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	static Stream<Enum<?>> allConstants() {
		return Stream.of(WeaponGroup.values(), RivenType.values(), AttributeGroup.values(), Unit.values())
				.flatMap(Stream::of);
	}

	@ParameterizedTest
	@MethodSource("allConstants")
	void writesAndReadsLowercaseValue(Enum<?> constant) {
		String json = mapper.writeValueAsString(constant);

		assertThat(json).isEqualTo("\"" + constant.name().toLowerCase() + "\"");
		assertThat(mapper.readValue(json, constant.getDeclaringClass())).isSameAs(constant);
	}

	@Test
	void rejectsConstantName() {
		assertThatThrownBy(() -> mapper.readValue("\"RIFLE\"", RivenType.class))
				.isInstanceOf(InvalidFormatException.class);
	}

}
