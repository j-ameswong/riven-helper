package com.riven.helper.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = "riven-helper.scheduling.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RivenHelperBackendApplicationTests {

	@Autowired
	MockMvcTester mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthIsUpWithDatabaseAndRedisDetails() {
		assertThat(mvc.get().uri("/actuator/health"))
				.hasStatusOk()
				.bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.status").isEqualTo("UP");
					assertThat(json).extractingPath("$.components.db.status").isEqualTo("UP");
					assertThat(json).extractingPath("$.components.redis.status").isEqualTo("UP");
				});
	}

}
