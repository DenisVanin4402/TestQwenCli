package com.example.testqwencli.dashboard;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LongSummaryStatistics;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Service
public class DashboardMetricsRegistry {

	private static final int MAX_LATENCY_SAMPLES = 2_000;
	private static final Duration RATE_WINDOW = Duration.ofSeconds(1);

	private final Clock clock;
	private final LongAdder syncSuccess = new LongAdder();
	private final LongAdder syncNoSlot = new LongAdder();
	private final LongAdder syncTimeout = new LongAdder();
	private final LongAdder syncErrors = new LongAdder();
	private final LongAdder asyncAccepted = new LongAdder();
	private final LongAdder asyncRejected = new LongAdder();
	private final LongAdder asyncErrors = new LongAdder();
	private final LongAdder asyncDispatchIterations = new LongAdder();
	private final LongAdder callbackDispatchIterations = new LongAdder();
	private final LongAdder expiredLeases = new LongAdder();
	private final AtomicInteger activeSyncRequests = new AtomicInteger();
	private final AtomicInteger activeAsyncSubmits = new AtomicInteger();
	private final ArrayDeque<Instant> syncEvents = new ArrayDeque<>();
	private final ArrayDeque<Instant> asyncEvents = new ArrayDeque<>();
	private final ArrayDeque<Long> latencySamples = new ArrayDeque<>();

	public DashboardMetricsRegistry(Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	public void syncStarted() {
		activeSyncRequests.incrementAndGet();
	}

	public void syncFinished(DashboardCallOutcome outcome) {
		Objects.requireNonNull(outcome, "outcome must not be null");
		activeSyncRequests.decrementAndGet();
		recordLatency(outcome.durationMs());
		recordRate(syncEvents);
		if (outcome.status() == DashboardCallStatus.SUCCESS) {
			syncSuccess.increment();
		}
		else if (outcome.status() == DashboardCallStatus.NO_SLOT) {
			syncNoSlot.increment();
		}
		else if (outcome.status() == DashboardCallStatus.TIMEOUT) {
			syncTimeout.increment();
		}
		else {
			syncErrors.increment();
		}
	}

	public void asyncStarted() {
		activeAsyncSubmits.incrementAndGet();
	}

	public void asyncFinished(DashboardSubmitOutcome outcome) {
		Objects.requireNonNull(outcome, "outcome must not be null");
		activeAsyncSubmits.decrementAndGet();
		recordLatency(outcome.durationMs());
		recordRate(asyncEvents);
		if (outcome.status() == DashboardSubmitStatus.ACCEPTED) {
			asyncAccepted.increment();
		}
		else if (outcome.status() == DashboardSubmitStatus.REJECTED) {
			asyncRejected.increment();
		}
		else {
			asyncErrors.increment();
		}
	}

	public void recordAsyncDispatchIterations(int count) {
		if (count > 0) {
			asyncDispatchIterations.add(count);
		}
	}

	public void recordCallbackDispatchIterations(int count) {
		if (count > 0) {
			callbackDispatchIterations.add(count);
		}
	}

	public void recordExpiredLeases(int count) {
		if (count > 0) {
			expiredLeases.add(count);
		}
	}

	public synchronized void reset() {
		syncSuccess.reset();
		syncNoSlot.reset();
		syncTimeout.reset();
		syncErrors.reset();
		asyncAccepted.reset();
		asyncRejected.reset();
		asyncErrors.reset();
		asyncDispatchIterations.reset();
		callbackDispatchIterations.reset();
		expiredLeases.reset();
		syncEvents.clear();
		asyncEvents.clear();
		latencySamples.clear();
	}

	public DashboardRuntimeMetrics snapshot() {
		LatencyPercentiles percentiles = latencyPercentiles();
		return new DashboardRuntimeMetrics(
				syncSuccess.sum(),
				syncNoSlot.sum(),
				syncTimeout.sum(),
				syncErrors.sum(),
				asyncAccepted.sum(),
				asyncRejected.sum(),
				asyncErrors.sum(),
				asyncDispatchIterations.sum(),
				callbackDispatchIterations.sum(),
				expiredLeases.sum(),
				activeSyncRequests.get(),
				activeAsyncSubmits.get(),
				rate(syncEvents),
				rate(asyncEvents),
				percentiles.p50(),
				percentiles.p95(),
				percentiles.p99()
		);
	}

	private synchronized void recordLatency(long durationMs) {
		latencySamples.addLast(Math.max(0, durationMs));
		while (latencySamples.size() > MAX_LATENCY_SAMPLES) {
			latencySamples.removeFirst();
		}
	}

	private synchronized void recordRate(ArrayDeque<Instant> events) {
		events.addLast(clock.instant());
		trimEvents(events, clock.instant().minus(RATE_WINDOW));
	}

	private synchronized long rate(ArrayDeque<Instant> events) {
		trimEvents(events, clock.instant().minus(RATE_WINDOW));
		return events.size();
	}

	private void trimEvents(ArrayDeque<Instant> events, Instant threshold) {
		while (!events.isEmpty() && events.peekFirst().isBefore(threshold)) {
			events.removeFirst();
		}
	}

	private synchronized LatencyPercentiles latencyPercentiles() {
		if (latencySamples.isEmpty()) {
			return new LatencyPercentiles(0, 0, 0);
		}
		ArrayList<Long> sorted = new ArrayList<>(latencySamples);
		sorted.sort(Comparator.naturalOrder());
		LongSummaryStatistics statistics = sorted.stream().mapToLong(Long::longValue).summaryStatistics();
		return new LatencyPercentiles(
				percentile(sorted, 0.50, statistics.getMax()),
				percentile(sorted, 0.95, statistics.getMax()),
				percentile(sorted, 0.99, statistics.getMax())
		);
	}

	private static long percentile(ArrayList<Long> sorted, double percentile, long fallback) {
		if (sorted.isEmpty()) {
			return fallback;
		}
		int index = (int) Math.ceil(sorted.size() * percentile) - 1;
		return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
	}

	private record LatencyPercentiles(long p50, long p95, long p99) {
	}
}
