package com.example.testqwencli.gateway.services.impl;

import com.example.testqwencli.gateway.services.SlotAcquireSleeper;
import com.example.testqwencli.gateway.services.SyncSlotWaitStrategy;
import java.time.Duration;
import java.util.Objects;

public final class PollingSyncSlotWaitStrategy implements SyncSlotWaitStrategy {

	private final SlotAcquireSleeper sleeper;

	public PollingSyncSlotWaitStrategy(SlotAcquireSleeper sleeper) {
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
	}

	@Override
	public long currentSignalVersion() {
		return 0;
	}

	@Override
	public long waitBeforeRetry(long observedSignalVersion, Duration fallbackTimeout) throws InterruptedException {
		sleeper.sleep(fallbackTimeout);
		return observedSignalVersion;
	}
}
