package com.example.testqwencli.gateway.slot;

import java.time.Duration;

public interface SyncSlotReleaseNotifier {

	long currentSignalVersion();

	long awaitNextSignal(long observedSignalVersion, Duration fallbackTimeout) throws InterruptedException;

	void notifySlotReleased();
}
