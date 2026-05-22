package com.example.testqwencli;

import com.example.testqwencli.gateway.slot.postgres.PostgresSlotReleaseNotificationListener;
import com.example.testqwencli.gateway.slot.postgres.PostgresSlotReleaseNotificationPublisher;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TestQwenCliApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void rootEndpointReturnsStatusMessage() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string("TestQwenCli is running"));
	}

	@Test
	void memoryModeDoesNotCreatePostgresInfrastructure() {
		assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(SpringLiquibase.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(PostgresSlotReleaseNotificationListener.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(PostgresSlotReleaseNotificationPublisher.class)).isEmpty();
	}

	@Test
	void externalGatewayChangelogResourceExists() {
		ClassPathResource changelog = new ClassPathResource(
				"db/changelog/external-gateway/db.changelog-master.yaml");

		assertThat(changelog.exists()).isTrue();
	}
}
