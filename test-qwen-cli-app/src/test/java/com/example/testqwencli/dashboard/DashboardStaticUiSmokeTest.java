package com.example.testqwencli.dashboard;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"external-gateway.async.dispatcher-enabled=false",
				"external-gateway.callback.delivery-enabled=false"
		}
)
class DashboardStaticUiSmokeTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@AfterEach
	void stopLoad() {
		restTemplate.postForEntity("/dashboard/api/load/stop", null, DashboardLoadState.class);
	}

	@Test
	void dashboardStaticUiAndLoadLifecycleWorkOverRealHttpPort() {
		String html = okBody(restTemplate.getForEntity("/dashboard/index.html", String.class));
		assertThat(html)
				.contains("Gateway Test Dashboard")
				.contains("id=\"apiToggle\"")
				.contains("id=\"startStop\"")
				.contains("id=\"syncRps\"")
				.contains("id=\"latency\"")
				.contains("id=\"eventLog\"")
				.contains("const apiBase = \"/dashboard/api\"");

		DashboardSnapshot initialSnapshot = okBody(restTemplate.getForEntity(
				"/dashboard/api/snapshot",
				DashboardSnapshot.class
		));
		assertThat(initialSnapshot.load().running()).isFalse();
		assertThat(initialSnapshot.health().repositoryMode()).isEqualTo("memory");

		DashboardLoadProfile profile = new DashboardLoadProfile(0, 0, 15, 900, 7, List.of("invest-pay"));
		DashboardLoadProfile updatedProfile = okBody(restTemplate.exchange(
				"/dashboard/api/load/profile",
				HttpMethod.PUT,
				new HttpEntity<>(profile),
				DashboardLoadProfile.class
		));
		assertThat(updatedProfile).isEqualTo(profile);

		DashboardSimulationSettings settings = new DashboardSimulationSettings(35, 5, 3, 16, 10, 4);
		DashboardSimulationSettings updatedSettings = okBody(restTemplate.exchange(
				"/dashboard/api/upstream-simulation",
				HttpMethod.PUT,
				new HttpEntity<>(settings),
				DashboardSimulationSettings.class
		));
		assertThat(updatedSettings).isEqualTo(settings);

		DashboardLoadState started = okBody(restTemplate.postForEntity(
				"/dashboard/api/load/start",
				null,
				DashboardLoadState.class
		));
		assertThat(started.running()).isTrue();
		assertThat(started.profile()).isEqualTo(profile);

		DashboardSnapshot runningSnapshot = okBody(restTemplate.getForEntity(
				"/dashboard/api/snapshot",
				DashboardSnapshot.class
		));
		assertThat(runningSnapshot.load().running()).isTrue();
		assertThat(runningSnapshot.load().profile()).isEqualTo(profile);
		assertThat(runningSnapshot.simulation()).isEqualTo(settings);

		DashboardLoadState stopped = okBody(restTemplate.postForEntity(
				"/dashboard/api/load/stop",
				null,
				DashboardLoadState.class
		));
		assertThat(stopped.running()).isFalse();
		assertThat(stopped.startedAt()).isNull();
	}

	private static <T> T okBody(ResponseEntity<T> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		return response.getBody();
	}
}
