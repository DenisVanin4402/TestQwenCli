package com.example.testqwencli.gateway.slot;

import java.time.Duration;

public interface SyncSlotWaitStrategy {

	long currentSignalVersion();

	long waitBeforeRetry(long observedSignalVersion, Duration fallbackTimeout) throws InterruptedException;
}
