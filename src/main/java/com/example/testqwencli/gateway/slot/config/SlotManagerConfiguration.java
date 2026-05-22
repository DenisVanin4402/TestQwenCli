package com.example.testqwencli.gateway.slot.config;

import com.example.testqwencli.gateway.slot.SlotAcquireSleeper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SlotManagerConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Clock externalGatewayClock() {
		return Clock.systemUTC();
	}

	@Bean
	@ConditionalOnMissingBean
	SlotAcquireSleeper slotAcquireSleeper() {
		return Thread::sleep;
	}
}
