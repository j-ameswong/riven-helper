package com.riven.helper.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres for tests, shared by every test class that imports this.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Static so one container serves the whole run: each distinct Spring test
	// context (@SpringBootTest, @DataJpaTest) creates this bean, and a container
	// per bean would start a Postgres per context. Testcontainers' Ryuk removes
	// it when the JVM exits. A context closed early (e.g. @DirtiesContext) would
	// stop it for everyone.
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return POSTGRES;
	}

}
