package com.riven.helper.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres and Redis for tests, shared by every test class that imports this.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Static so one container serves the whole run: each distinct Spring test
	// context (@SpringBootTest, @DataJpaTest) creates these beans, and a container
	// per bean would start one per context. Testcontainers' Ryuk removes them
	// when the JVM exits. A context closed early (e.g. @DirtiesContext) would
	// stop them for everyone.
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8").withExposedPorts(6379);

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return POSTGRES;
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redis() {
		return REDIS;
	}

}
