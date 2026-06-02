package com.example.testqwencli.gateway.services.impl;

import com.example.testqwencli.gateway.services.SyncSlotReleaseNotifier;
import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Objects;

public final class LocalSyncSlotReleaseNotifier implements SyncSlotReleaseNotifier {

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition slotReleased = lock.newCondition();
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
	public long awaitNextSignal(long observedSignalVersion, Duration fallbackTimeout) throws InterruptedException {
		Objects.requireNonNull(fallbackTimeout, "fallbackTimeout must not be null");
		if (fallbackTimeout.isZero() || fallbackTimeout.isNegative()) {
			return currentSignalVersion();
		}

		long nanos = toNanos(fallbackTimeout);
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

	private static long toNanos(Duration duration) {
		try {
			return duration.toNanos();
		}
		catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}
}
