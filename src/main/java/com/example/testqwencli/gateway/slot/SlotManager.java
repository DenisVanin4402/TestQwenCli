package com.example.testqwencli.gateway.slot;

import com.example.testqwencli.gateway.slot.config.ExternalGatewaySlotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public final class SlotManager {

	private static final Logger log = LoggerFactory.getLogger(SlotManager.class);

	private final SlotRepository slotRepository;
	private final Clock clock;
	private final SlotAcquireSleeper sleeper;
	private final Duration syncAcquirePollInterval;

	public SlotManager(
			SlotRepository slotRepository,
			Clock clock,
			SlotAcquireSleeper sleeper,
			ExternalGatewaySlotProperties properties
	) {
		this.slotRepository = Objects.requireNonNull(slotRepository, "slotRepository must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
		Objects.requireNonNull(properties, "properties must not be null");
		this.syncAcquirePollInterval = properties.syncAcquirePollInterval();
	}

	/**
	 * Получает sync-слот для владельца с ожиданием до заданного timeout.
	 */
	public Optional<SlotLease> acquireSyncSlot(String owner, Duration timeout) {
		validateOwner(owner);
		requireNotNegative(timeout);

		Instant startedAt = clock.instant();
		Optional<SlotLease> immediateLease = slotRepository.acquireSyncSlot(owner, startedAt);
		if (immediateLease.isPresent() || timeout.isZero()) {
			return immediateLease;
		}

		UUID waiterId = slotRepository.registerSyncWaiter(owner, startedAt);
		try {
			return waitForSyncSlot(owner, startedAt.plus(timeout));
		}
		finally {
			slotRepository.removeSyncWaiter(waiterId);
		}
	}

	/**
	 * Пытается получить async-слот без ожидания.
	 */
	public Optional<SlotLease> tryAcquireAsyncSlot(String owner, String taskId) {
		validateOwner(owner);
		return slotRepository.acquireAsyncSlot(owner, taskId, clock.instant());
	}

	/**
	 * Освобождает слот только при совпадении slotId и leaseId.
	 */
	public boolean release(int slotId, UUID leaseId) {
		Objects.requireNonNull(leaseId, "leaseId must not be null");
		return slotRepository.release(slotId, leaseId);
	}

	/**
	 * Продлевает lease слота только при совпадении slotId и leaseId.
	 */
	public Optional<SlotLease> heartbeat(int slotId, UUID leaseId) {
		Objects.requireNonNull(leaseId, "leaseId must not be null");
		return slotRepository.heartbeat(slotId, leaseId, clock.instant());
	}

	/**
	 * Освобождает истекшие lease слотов.
	 */
	public int reapExpiredLeases() {
		return slotRepository.reapExpiredLeases(clock.instant());
	}

	private Optional<SlotLease> waitForSyncSlot(String owner, Instant deadline) {
		while (true) {
			Instant now = clock.instant();
			Optional<SlotLease> lease = slotRepository.acquireSyncSlot(owner, now);
			if (lease.isPresent()) {
				return lease;
			}
			if (!now.isBefore(deadline)) {
				log.debug("Истекло ожидание sync-слота: owner={}", owner);
				return Optional.empty();
			}
			if (!sleepUntilNextAttempt(now, deadline)) {
				return Optional.empty();
			}
		}
	}

	private boolean sleepUntilNextAttempt(Instant now, Instant deadline) {
		try {
			sleeper.sleep(nextPause(now, deadline));
			return true;
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			log.debug("Ожидание sync-слота прервано", exception);
			return false;
		}
	}

	private Duration nextPause(Instant now, Instant deadline) {
		Duration remaining = Duration.between(now, deadline);
		if (remaining.compareTo(syncAcquirePollInterval) < 0) {
			return remaining;
		}
		return syncAcquirePollInterval;
	}

	private static void validateOwner(String owner) {
		Objects.requireNonNull(owner, "owner must not be null");
		if (owner.isBlank()) {
			throw new IllegalArgumentException("Владелец lease не должен быть пустым");
		}
	}

	private static void requireNotNegative(Duration timeout) {
		Objects.requireNonNull(timeout, "timeout must not be null");
		if (timeout.isNegative()) {
			throw new IllegalArgumentException("Timeout ожидания sync-слота не должен быть отрицательным");
		}
	}
}
