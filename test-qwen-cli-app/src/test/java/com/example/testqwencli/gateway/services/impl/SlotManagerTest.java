package com.example.testqwencli.gateway.services.impl;

import com.example.testqwencli.gateway.config.ExternalGatewaySlotProperties;
import com.example.testqwencli.gateway.model.slot.SlotKind;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.model.slot.SyncAcquireWaitMode;
import com.example.testqwencli.gateway.repository.memory.MemorySlotRepository;
import com.example.testqwencli.gateway.services.impl.ListenNotifySyncSlotWaitStrategy;
import com.example.testqwencli.gateway.services.impl.PollingSyncSlotWaitStrategy;
import com.example.testqwencli.gateway.services.impl.SlotManagerImpl;
import com.example.testqwencli.gateway.services.SlotAcquireSleeper;
import com.example.testqwencli.gateway.services.SlotManager;
import com.example.testqwencli.gateway.services.SyncSlotReleaseNotifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SlotManagerTest {

	private static final Instant NOW = Instant.parse("2026-05-22T00:00:00Z");

	@Test
	void defaultSyncAcquireWaitModeIsPolling() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("external-gateway.slots.total", "5")
				.withProperty("external-gateway.slots.target-free-sync-slots", "1")
				.withProperty("external-gateway.slots.lease-ttl", "30s")
				.withProperty("external-gateway.slots.sync-waiter-ttl", "5s")
				.withProperty("external-gateway.slots.sync-acquire-poll-interval", "10ms");

		ExternalGatewaySlotProperties properties = Binder.get(environment)
				.bind("external-gateway.slots", ExternalGatewaySlotProperties.class)
				.orElseThrow(IllegalStateException::new);

		assertThat(properties.syncAcquireWaitMode()).isEqualTo(SyncAcquireWaitMode.POLLING);
	}

	@Test
	void concurrentSyncAcquireDoesNotExceedFiveActiveLeases() throws Exception {
		ExternalGatewaySlotProperties properties = properties(Duration.ofMillis(1));
		MemorySlotRepository repository = new MemorySlotRepository(properties);
		SlotManager manager = new SlotManagerImpl(repository, Clock.systemUTC(),
				new PollingSyncSlotWaitStrategy(Thread::sleep), properties);
		ExecutorService executor = Executors.newFixedThreadPool(12);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger activeLeases = new AtomicInteger();
		AtomicInteger maxActiveLeases = new AtomicInteger();

		try {
			List<Future<Boolean>> futures = IntStream.range(0, 12)
					.mapToObj(index -> executor.submit(() -> acquireAndRelease(manager, start, activeLeases,
							maxActiveLeases, index)))
					.toList();

			start.countDown();

			for (Future<Boolean> future : futures) {
				assertThat(future.get(3, TimeUnit.SECONDS)).isTrue();
			}
			assertThat(maxActiveLeases).hasValueLessThanOrEqualTo(5);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void syncWaiterIsRemovedAfterSuccessfulAcquire() throws Exception {
		ExternalGatewaySlotProperties properties = properties(Duration.ofMillis(10));
		MemorySlotRepository repository = new MemorySlotRepository(properties);
		BlockingSleeper sleeper = new BlockingSleeper();
		SlotManager manager = new SlotManagerImpl(repository, Clock.systemUTC(),
				new PollingSyncSlotWaitStrategy(sleeper), properties);
		List<SlotLease> leases = acquireAllSyncSlots(repository, Instant.now());
		ExecutorService executor = Executors.newSingleThreadExecutor();

		try {
			Future<Optional<SlotLease>> waitingAcquire = executor.submit(
					() -> manager.acquireSyncSlot("waiting-owner", Duration.ofSeconds(2)));

			assertThat(sleeper.awaitPause()).isTrue();
			assertThat(repository.countLiveSyncWaiters(Instant.now())).isEqualTo(1);

			SlotLease releasedLease = leases.getFirst();
			assertThat(repository.release(releasedLease.slotId(), releasedLease.leaseId())).isTrue();
			sleeper.resume();

			Optional<SlotLease> acquiredLease = waitingAcquire.get(1, TimeUnit.SECONDS);
			assertThat(acquiredLease).isPresent();
			assertThat(repository.countLiveSyncWaiters(Instant.now())).isZero();
			acquiredLease.ifPresent(lease -> manager.release(lease.slotId(), lease.leaseId()));
		}
		finally {
			sleeper.resume();
			executor.shutdownNow();
		}
	}

	@Test
	void syncWaiterIsRemovedAfterTimeout() {
		ExternalGatewaySlotProperties properties = properties(Duration.ofMillis(10));
		MemorySlotRepository repository = new MemorySlotRepository(properties);
		MutableClock clock = new MutableClock(NOW);
		SlotManager manager = new SlotManagerImpl(repository, clock,
				new PollingSyncSlotWaitStrategy(clock::advance), properties);
		acquireAllSyncSlots(repository, clock.instant());

		Optional<SlotLease> acquiredLease = manager.acquireSyncSlot("timeout-owner", Duration.ofMillis(25));

		assertThat(acquiredLease).isEmpty();
		assertThat(repository.countLiveSyncWaiters(clock.instant())).isZero();
	}

	@Test
	void asyncAcquireIsBlockedByLiveSyncWaiterCreatedByWaitingSyncAcquire() throws Exception {
		ExternalGatewaySlotProperties properties = properties(Duration.ofMillis(10));
		MemorySlotRepository repository = new MemorySlotRepository(properties);
		BlockingSleeper sleeper = new BlockingSleeper();
		SlotManager manager = new SlotManagerImpl(repository, Clock.systemUTC(),
				new PollingSyncSlotWaitStrategy(sleeper), properties);
		List<SlotLease> leases = acquireAllSyncSlots(repository, Instant.now());
		ExecutorService executor = Executors.newSingleThreadExecutor();

		try {
			Future<Optional<SlotLease>> waitingAcquire = executor.submit(
					() -> manager.acquireSyncSlot("waiting-owner", Duration.ofSeconds(2)));

			assertThat(sleeper.awaitPause()).isTrue();
			assertThat(repository.countLiveSyncWaiters(Instant.now())).isEqualTo(1);

			SlotLease releasedLease = leases.getFirst();
			assertThat(repository.release(releasedLease.slotId(), releasedLease.leaseId())).isTrue();
			assertThat(manager.tryAcquireAsyncSlot("async-owner", "task-1")).isEmpty();

			sleeper.resume();
			Optional<SlotLease> acquiredLease = waitingAcquire.get(1, TimeUnit.SECONDS);
			assertThat(acquiredLease).isPresent();
			assertThat(repository.countLiveSyncWaiters(Instant.now())).isZero();
			acquiredLease.ifPresent(lease -> manager.release(lease.slotId(), lease.leaseId()));
		}
		finally {
			sleeper.resume();
			executor.shutdownNow();
		}
	}

	@Test
	void listenNotifyModeWakesWaitingSyncAcquireWithoutFullPollInterval() throws Exception {
		ExternalGatewaySlotProperties properties = properties(Duration.ofSeconds(5), SyncAcquireWaitMode.LISTEN_NOTIFY);
		MemorySlotRepository repository = new MemorySlotRepository(properties);
		RecordingNotifier notifier = new RecordingNotifier();
		SlotManager manager = new SlotManagerImpl(repository, Clock.systemUTC(),
				new ListenNotifySyncSlotWaitStrategy(notifier), properties);
		List<SlotLease> leases = acquireAllSyncSlots(repository, Instant.now());
		ExecutorService executor = Executors.newSingleThreadExecutor();

		try {
			Future<Optional<SlotLease>> waitingAcquire = executor.submit(
					() -> manager.acquireSyncSlot("waiting-owner", Duration.ofSeconds(10)));

			assertThat(notifier.awaitWaitStarted()).isTrue();
			SlotLease releasedLease = leases.getFirst();
			assertThat(repository.release(releasedLease.slotId(), releasedLease.leaseId())).isTrue();
			notifier.notifySlotReleased();

			Optional<SlotLease> acquiredLease = waitingAcquire.get(1, TimeUnit.SECONDS);
			assertThat(acquiredLease).isPresent();
			acquiredLease.ifPresent(lease -> manager.release(lease.slotId(), lease.leaseId()));
		}
		finally {
			notifier.notifySlotReleased();
			executor.shutdownNow();
		}
	}

	@Test
	void listenNotifyModeFallsBackToTimeoutWhenNotificationIsLost() throws Exception {
		ExternalGatewaySlotProperties properties = properties(Duration.ofMillis(25), SyncAcquireWaitMode.LISTEN_NOTIFY);
		MemorySlotRepository repository = new MemorySlotRepository(properties);
		RecordingNotifier notifier = new RecordingNotifier();
		SlotManager manager = new SlotManagerImpl(repository, Clock.systemUTC(),
				new ListenNotifySyncSlotWaitStrategy(notifier), properties);
		List<SlotLease> leases = acquireAllSyncSlots(repository, Instant.now());
		ExecutorService executor = Executors.newSingleThreadExecutor();

		try {
			Future<Optional<SlotLease>> waitingAcquire = executor.submit(
					() -> manager.acquireSyncSlot("waiting-owner", Duration.ofSeconds(2)));

			assertThat(notifier.awaitWaitStarted()).isTrue();
			SlotLease releasedLease = leases.getFirst();
			assertThat(repository.release(releasedLease.slotId(), releasedLease.leaseId())).isTrue();

			Optional<SlotLease> acquiredLease = waitingAcquire.get(1, TimeUnit.SECONDS);
			assertThat(acquiredLease).isPresent();
			acquiredLease.ifPresent(lease -> manager.release(lease.slotId(), lease.leaseId()));
		}
		finally {
			notifier.notifySlotReleased();
			executor.shutdownNow();
		}
	}

	@Test
	void releaseAndHeartbeatUseLeaseIdProtection() {
		ExternalGatewaySlotProperties properties = properties(Duration.ofMillis(10));
		MemorySlotRepository repository = new MemorySlotRepository(properties);
		SlotManager manager = new SlotManagerImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC),
				new PollingSyncSlotWaitStrategy(duration -> {
				}), properties);
		SlotLease lease = manager.acquireSyncSlot("sync-owner", Duration.ZERO).orElseThrow();
		UUID wrongLeaseId = UUID.randomUUID();

		assertThat(manager.release(lease.slotId(), wrongLeaseId)).isFalse();
		assertThat(manager.heartbeat(lease.slotId(), wrongLeaseId)).isEmpty();
		assertThat(manager.heartbeat(lease.slotId(), lease.leaseId())).isPresent();
		assertThat(manager.release(lease.slotId(), lease.leaseId())).isTrue();
		assertThat(manager.heartbeat(lease.slotId(), lease.leaseId())).isEmpty();
	}

	@Test
	void reapExpiredLeasesUsesCurrentClock() {
		ExternalGatewaySlotProperties properties = properties(Duration.ofMillis(10));
		MemorySlotRepository repository = new MemorySlotRepository(properties);
		SlotManager manager = new SlotManagerImpl(repository, Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC),
				new PollingSyncSlotWaitStrategy(duration -> {
				}), properties);
		acquireAllSyncSlots(repository, NOW);

		assertThat(manager.reapExpiredLeases()).isEqualTo(5);
		assertThat(repository.countBusySlots(SlotKind.SYNC)).isZero();
	}

	private static boolean acquireAndRelease(
			SlotManager manager,
			CountDownLatch start,
			AtomicInteger activeLeases,
			AtomicInteger maxActiveLeases,
			int index
	) throws Exception {
		assertThat(start.await(1, TimeUnit.SECONDS)).isTrue();
		Optional<SlotLease> lease = manager.acquireSyncSlot("sync-owner-" + index, Duration.ofSeconds(2));
		if (lease.isEmpty()) {
			return false;
		}

		int currentActive = activeLeases.incrementAndGet();
		maxActiveLeases.accumulateAndGet(currentActive, Math::max);
		try {
			Thread.sleep(25);
		}
		finally {
			activeLeases.decrementAndGet();
			manager.release(lease.orElseThrow().slotId(), lease.orElseThrow().leaseId());
		}
		return true;
	}

	private static List<SlotLease> acquireAllSyncSlots(MemorySlotRepository repository, Instant now) {
		return IntStream.range(0, 5)
				.mapToObj(index -> repository.acquireSyncSlot("existing-owner-" + index, now).orElseThrow())
				.toList();
	}

	private static ExternalGatewaySlotProperties properties(Duration pollInterval) {
		return properties(pollInterval, SyncAcquireWaitMode.POLLING);
	}

	private static ExternalGatewaySlotProperties properties(Duration pollInterval, SyncAcquireWaitMode waitMode) {
		return new ExternalGatewaySlotProperties(5, 1, Duration.ofSeconds(30), Duration.ofSeconds(5),
				pollInterval, waitMode);
	}

	private static final class BlockingSleeper implements SlotAcquireSleeper {

		private final CountDownLatch paused = new CountDownLatch(1);
		private final CountDownLatch resumed = new CountDownLatch(1);

		@Override
		public void sleep(Duration duration) throws InterruptedException {
			paused.countDown();
			if (!resumed.await(1, TimeUnit.SECONDS)) {
				throw new InterruptedException("Тестовое ожидание sync-слота не было разблокировано");
			}
		}

		private boolean awaitPause() throws InterruptedException {
			return paused.await(1, TimeUnit.SECONDS);
		}

		private void resume() {
			resumed.countDown();
		}
	}

	private static final class RecordingNotifier implements SyncSlotReleaseNotifier {

		private final ReentrantLock lock = new ReentrantLock();
		private final Condition slotReleased = lock.newCondition();
		private final CountDownLatch waitStarted = new CountDownLatch(1);
		private long signalVersion;

		@Override
		public long currentSignalVersion() {
			lock.lock();
			try {
				return signalVersion;
			}
			finally {
				lock.unlock();
			}
		}

		@Override
		public long awaitNextSignal(long observedSignalVersion, Duration fallbackTimeout)
				throws InterruptedException {
			waitStarted.countDown();
			long nanos = fallbackTimeout.toNanos();
			lock.lockInterruptibly();
			try {
				while (signalVersion == observedSignalVersion && nanos > 0) {
					nanos = slotReleased.awaitNanos(nanos);
				}
				return signalVersion;
			}
			finally {
				lock.unlock();
			}
		}

		@Override
		public void notifySlotReleased() {
			lock.lock();
			try {
				signalVersion++;
				slotReleased.signalAll();
			}
			finally {
				lock.unlock();
			}
		}

		private boolean awaitWaitStarted() throws InterruptedException {
			return waitStarted.await(1, TimeUnit.SECONDS);
		}
	}

	private static final class MutableClock extends Clock {

		private final ZoneId zone;
		private Instant instant;

		private MutableClock(Instant instant) {
			this(instant, ZoneOffset.UTC);
		}

		private MutableClock(Instant instant, ZoneId zone) {
			this.instant = instant;
			this.zone = zone;
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
