package com.example.testqwencli.gateway.support;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AsyncTestAwaiter {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(50);

	private final Duration timeout;
	private final Duration pollInterval;

	private AsyncTestAwaiter(Duration timeout, Duration pollInterval) {
		this.timeout = requirePositive(timeout, "timeout");
		this.pollInterval = requirePositive(pollInterval, "pollInterval");
	}

	public static AsyncTestAwaiter defaults() {
		return new AsyncTestAwaiter(DEFAULT_TIMEOUT, DEFAULT_POLL_INTERVAL);
	}

	public static AsyncTestAwaiter of(Duration timeout, Duration pollInterval) {
		return new AsyncTestAwaiter(timeout, pollInterval);
	}

	public void untilTrue(String description, BooleanSupplier condition) {
		Objects.requireNonNull(condition, "condition must not be null");
		until(description, condition::getAsBoolean, Boolean.TRUE::equals);
	}

	public void untilAsserted(String description, CheckedRunnable assertion) {
		Objects.requireNonNull(assertion, "assertion must not be null");
		Throwable lastFailure = null;
		long deadline = deadlineNanos();
		do {
			try {
				assertion.run();
				return;
			}
			catch (AssertionError | Exception failure) {
				lastFailure = failure;
			}
			sleep(description);
		}
		while (System.nanoTime() <= deadline);
		throw timeoutError(description, lastFailure, null);
	}

	public <T> T untilPresent(String description, Supplier<Optional<T>> supplier) {
		Objects.requireNonNull(supplier, "supplier must not be null");
		return until(description, supplier, Optional::isPresent).orElseThrow();
	}

	public <T> T until(String description, Supplier<T> supplier, Predicate<T> predicate) {
		Objects.requireNonNull(supplier, "supplier must not be null");
		Objects.requireNonNull(predicate, "predicate must not be null");
		Throwable lastFailure = null;
		T lastValue = null;
		long deadline = deadlineNanos();
		do {
			try {
				lastValue = supplier.get();
				if (predicate.test(lastValue)) {
					return lastValue;
				}
			}
			catch (RuntimeException failure) {
				lastFailure = failure;
			}
			sleep(description);
		}
		while (System.nanoTime() <= deadline);
		throw timeoutError(description, lastFailure, lastValue);
	}

	private long deadlineNanos() {
		return System.nanoTime() + timeout.toNanos();
	}

	private void sleep(String description) {
		try {
			Thread.sleep(pollInterval.toMillis());
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw timeoutError(description, exception, null);
		}
	}

	private AssertionError timeoutError(String description, Throwable cause, Object lastValue) {
		String normalizedDescription = description == null || description.isBlank()
				? "асинхронное условие"
				: description;
		String message = "Не дождались условия: " + normalizedDescription
				+ " за " + timeout.toMillis() + " мс";
		if (lastValue != null) {
			message += ", последнее значение: " + lastValue;
		}
		AssertionError error = new AssertionError(message);
		if (cause != null) {
			error.initCause(cause);
		}
		return error;
	}

	private static Duration requirePositive(Duration duration, String name) {
		Objects.requireNonNull(duration, name + " must not be null");
		if (duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		return duration;
	}

	@FunctionalInterface
	public interface CheckedRunnable {

		void run() throws Exception;
	}
}
