package com.example.testqwencli.gateway.config;

import com.example.testqwencli.gateway.model.slot.enums.SyncAcquireWaitMode;
import com.example.testqwencli.gateway.services.impl.ListenNotifySyncSlotWaitStrategy;
import com.example.testqwencli.gateway.services.impl.LocalSyncSlotReleaseNotifier;
import com.example.testqwencli.gateway.services.impl.PollingSyncSlotWaitStrategy;
import com.example.testqwencli.gateway.services.SlotAcquireSleeper;
import com.example.testqwencli.gateway.services.SyncSlotReleaseNotifier;
import com.example.testqwencli.gateway.services.SyncSlotWaitStrategy;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExternalGatewaySlotProperties.class)
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

	@Bean
	@ConditionalOnMissingBean
	SyncSlotReleaseNotifier syncSlotReleaseNotifier() {
		return new LocalSyncSlotReleaseNotifier();
	}

	@Bean
	@ConditionalOnMissingBean
	SyncSlotWaitStrategy syncSlotWaitStrategy(
			ExternalGatewaySlotProperties properties,
			SlotAcquireSleeper sleeper,
			SyncSlotReleaseNotifier notifier
	) {
		if (properties.syncAcquireWaitMode() == SyncAcquireWaitMode.LISTEN_NOTIFY) {
			return new ListenNotifySyncSlotWaitStrategy(notifier);
		}
		return new PollingSyncSlotWaitStrategy(sleeper);
	}
}
