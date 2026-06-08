package com.example.testqwencli.dashboard;

import com.example.testqwencli.dashboard.enums.DashboardCallStatus;
import com.example.testqwencli.dashboard.enums.DashboardRequestPriority;
import com.example.testqwencli.dashboard.enums.DashboardSubmitStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DashboardLoadRunner {

	private final DashboardGatewayClient gatewayClient;
	private final DashboardMetricsRegistry metricsRegistry;
	private final AtomicReference<DashboardLoadProfile> profile =
			new AtomicReference<>(DashboardLoadProfile.defaults());
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final ExecutorService requestExecutor = Executors.newFixedThreadPool(64);
	private volatile Instant startedAt;

	public DashboardLoadRunner(DashboardGatewayClient gatewayClient, DashboardMetricsRegistry metricsRegistry) {
		this.gatewayClient = Objects.requireNonNull(gatewayClient, "gatewayClient must not be null");
		this.metricsRegistry = Objects.requireNonNull(metricsRegistry, "metricsRegistry must not be null");
	}

	@PostConstruct
	void startSchedulers() {
		scheduler.scheduleAtFixedRate(this::submitLoadTickSafely, 1, 1, TimeUnit.SECONDS);
	}

	public DashboardLoadState start() {
		if (running.compareAndSet(false, true)) {
			startedAt = Instant.now();
		}
		return state();
	}

	public DashboardLoadState stop() {
		running.set(false);
		startedAt = null;
		return state();
	}

	public DashboardLoadState state() {
		return new DashboardLoadState(running.get(), startedAt, profile.get());
	}

	public DashboardLoadProfile updateProfile(DashboardLoadProfile profile) {
		Objects.requireNonNull(profile, "profile must not be null");
		this.profile.set(profile);
		return profile;
	}

	private void submitLoadTickSafely() {
		try {
			if (running.get()) {
				submitLoadTick(profile.get());
			}
		}
		catch (RuntimeException exception) {
			// Генератор нагрузки не должен останавливаться из-за одиночной ошибки.
		}
	}

	private void submitLoadTick(DashboardLoadProfile profile) {
		for (int index = 0; index < profile.syncRps(); index++) {
			requestExecutor.submit(() -> callSync(profile));
		}
		for (int index = 0; index < profile.asyncRps(); index++) {
			requestExecutor.submit(() -> submitAsync(profile));
		}
	}

	private void callSync(DashboardLoadProfile profile) {
		metricsRegistry.syncStarted();
		try {
			metricsRegistry.syncFinished(gatewayClient.callSync(newRequest(profile, false)));
		}
		catch (RuntimeException exception) {
			metricsRegistry.syncFinished(new DashboardCallOutcome(DashboardCallStatus.ERROR, 0,
					exception.getClass().getSimpleName()));
		}
	}

	private void submitAsync(DashboardLoadProfile profile) {
		metricsRegistry.asyncStarted();
		try {
			metricsRegistry.asyncFinished(gatewayClient.submitAsync(newRequest(profile, true)));
		}
		catch (RuntimeException exception) {
			metricsRegistry.asyncFinished(new DashboardSubmitOutcome(DashboardSubmitStatus.ERROR, 0,
					exception.getClass().getSimpleName(), null));
		}
	}

	private DashboardGatewayRequest newRequest(DashboardLoadProfile profile, boolean async) {
		UUID externalId = UUID.randomUUID();
		String clientService = nextClientService(profile);
		DashboardRequestPriority priority = nextPriority(profile);
		return new DashboardGatewayRequest(
				externalId,
				clientService,
				priority,
				async,
				payload(externalId, async, priority, profile.timeoutMs()),
				"dashboard-" + externalId
		);
	}

	private String nextClientService(DashboardLoadProfile profile) {
		int index = ThreadLocalRandom.current().nextInt(profile.clientServices().size());
		return profile.clientServices().get(index);
	}

	private DashboardRequestPriority nextPriority(DashboardLoadProfile profile) {
		int value = ThreadLocalRandom.current().nextInt(100);
		return value < profile.highPriorityPercent() ? DashboardRequestPriority.HIGH : DashboardRequestPriority.LOW;
	}

	private Map<String, Object> payload(
			UUID externalId,
			boolean async,
			DashboardRequestPriority priority,
			int syncTimeoutMs
	) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("operation", async ? "dashboard-async-test" : "dashboard-sync-test");
		payload.put("externalId", externalId.toString());
		payload.put("priority", priority.name());
		payload.put("createdBy", "dashboard");
		if (!async) {
			payload.put(DashboardGatewayRequest.SYNC_TIMEOUT_PAYLOAD_KEY, syncTimeoutMs);
		}
		return payload;
	}

	@PreDestroy
	void shutdown() {
		running.set(false);
		scheduler.shutdownNow();
		requestExecutor.shutdownNow();
	}
}
