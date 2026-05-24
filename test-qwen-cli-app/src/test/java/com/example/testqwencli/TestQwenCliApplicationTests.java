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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
	void openApiDocsAreAvailable() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists());
	}

	@Test
	void swaggerUiIsAvailable() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void dashboardPageRedirectsToStaticUi() throws Exception {
		mockMvc.perform(get("/dashboard"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/dashboard/index.html"));
	}

	@Test
	void dashboardStaticUiIsAvailable() throws Exception {
		mockMvc.perform(get("/dashboard/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Gateway Test Dashboard")));
	}

	@Test
	void dashboardSnapshotReturnsLoadAndHealthState() throws Exception {
		mockMvc.perform(get("/dashboard/api/snapshot"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.load.running").value(false))
				.andExpect(jsonPath("$.load.profile.syncRps").isNumber())
				.andExpect(jsonPath("$.health.repositoryMode").value("memory"))
				.andExpect(jsonPath("$.health.slots.total").value(5));
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

	@Test
	void consoleLoggingUsesUtf8ByDefault() throws Exception {
		Properties properties = loadProperties("application.properties");

		assertThat(properties)
				.containsEntry("logging.charset.console", "UTF-8")
				.containsEntry("logging.charset.file", "UTF-8")
				.containsEntry("server.servlet.encoding.charset", "UTF-8")
				.containsEntry("server.servlet.encoding.force", "true")
				.containsEntry("external-gateway.callback.delivery-timeout-ms", "30s")
				.containsEntry("external-gateway.callback.delivery-recovery-interval-ms", "1000");
	}

	@Test
	void postgresProfileConfiguresLocalDockerDatabase() throws Exception {
		Properties properties = loadProperties("application-postgres.properties");

		assertThat(properties)
				.containsEntry("external-gateway.repository.type", "postgres")
				.containsEntry("external-gateway.postgres.jdbc-url",
						"jdbc:postgresql://localhost:5432/external_gateway")
				.containsEntry("external-gateway.postgres.username", "external_gateway")
				.containsEntry("external-gateway.postgres.schema", "external_gateway")
				.containsEntry("external-gateway.postgres.liquibase-enabled", "true");
	}

	private static Properties loadProperties(String path) throws Exception {
		ClassPathResource resource = new ClassPathResource(path);
		Properties properties = new Properties();
		try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}
}
