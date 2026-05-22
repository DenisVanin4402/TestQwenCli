package com.example.testqwencli.gateway.slot;

import java.time.Duration;

@FunctionalInterface
public interface SlotAcquireSleeper {

	void sleep(Duration duration) throws InterruptedException;
}
