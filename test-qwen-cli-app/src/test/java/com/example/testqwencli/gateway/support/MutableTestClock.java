package com.example.testqwencli.gateway.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableTestClock extends Clock {

	private final AtomicReference<Instant> instant;
	private final ZoneId zone;

	public MutableTestClock(Instant instant) {
		this(instant, ZoneOffset.UTC);
	}

	public MutableTestClock(Instant instant, ZoneId zone) {
		this.instant = new AtomicReference<>(Objects.requireNonNull(instant, "instant must not be null"));
		this.zone = Objects.requireNonNull(zone, "zone must not be null");
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableTestClock(instant(), zone);
	}

	@Override
	public Instant instant() {
		return instant.get();
	}

	public Instant set(Instant instant) {
		this.instant.set(Objects.requireNonNull(instant, "instant must not be null"));
		return instant;
	}

	public Instant advance(Duration duration) {
		Objects.requireNonNull(duration, "duration must not be null");
		return instant.updateAndGet(current -> current.plus(duration));
	}
}
