package com.example.testqwencli.gateway.slot;

import java.time.Duration;
import java.util.Objects;

public final class ListenNotifySyncSlotWaitStrategy implements SyncSlotWaitStrategy {

	private final SyncSlotReleaseNotifier notifier;

	public ListenNotifySyncSlotWaitStrategy(SyncSlotReleaseNotifier notifier) {
		this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
	}

	@Override
	public long currentSignalVersion() {
		return notifier.currentSignalVersion();
	}

	@Override
	public long waitBeforeRetry(long observedSignalVersion, Duration fallbackTimeout) throws InterruptedException {
		return notifier.awaitNextSignal(observedSignalVersion, fallbackTimeout);
	}
}
