package com.riven.helper.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.riven.helper.backend.TestcontainersConfiguration;
import com.riven.helper.backend.domain.AttributeGroup;
import com.riven.helper.backend.domain.RivenType;
import com.riven.helper.backend.domain.Unit;
import com.riven.helper.backend.domain.WeaponGroup;

/**
 * The allowed enum values are listed both in the Java enums and in the CHECK
 * constraints of the Liquibase changelog. Fails when the two lists drift apart.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EnumCheckConstraintsTest {

	private static final Pattern QUOTED_LITERAL = Pattern.compile("'([^']*)'");

	@Autowired
	TestEntityManager entityManager;

	static Stream<Arguments> enumBackedConstraints() {
		return Stream.of(
				arguments("ck_weapons_weapon_group", valuesOf(WeaponGroup.values(), WeaponGroup::value)),
				arguments("ck_weapons_riven_type", valuesOf(RivenType.values(), RivenType::value)),
				arguments("ck_attributes_exclusive_to", valuesOf(RivenType.values(), RivenType::value)),
				arguments("ck_attributes_attribute_group", valuesOf(AttributeGroup.values(), AttributeGroup::value)),
				arguments("ck_attributes_unit", valuesOf(Unit.values(), Unit::value)));
	}

	@ParameterizedTest
	@MethodSource("enumBackedConstraints")
	void checkConstraintAllowsExactlyTheEnumValues(String constraintName, Set<String> enumValues) {
		String definition = (String) entityManager.getEntityManager()
				.createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = :name")
				.setParameter("name", constraintName)
				.getSingleResult();

		assertThat(quotedLiterals(definition)).as(definition).isEqualTo(enumValues);
	}

	private static <E> Set<String> valuesOf(E[] constants, Function<E, String> value) {
		return Arrays.stream(constants).map(value).collect(Collectors.toSet());
	}

	private static Set<String> quotedLiterals(String definition) {
		Matcher matcher = QUOTED_LITERAL.matcher(definition);
		return matcher.results().map(result -> result.group(1)).collect(Collectors.toSet());
	}

}
