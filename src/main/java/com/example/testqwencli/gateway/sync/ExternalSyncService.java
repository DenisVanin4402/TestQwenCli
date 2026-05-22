package com.example.testqwencli.gateway.sync;

import com.example.testqwencli.gateway.slot.SlotLease;
import com.example.testqwencli.gateway.slot.SlotManager;
import com.example.testqwencli.gateway.sync.config.ExternalGatewaySyncProperties;
import com.example.testqwencli.gateway.sync.error.NoSlotAvailableException;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamClient;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamRequest;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
public class ExternalSyncService {

	private static final Logger log = LoggerFactory.getLogger(ExternalSyncService.class);

	private final SlotManager slotManager;
	private final ExternalGatewaySyncProperties syncProperties;
	private final ExternalUpstreamClient upstreamClient;

	public ExternalSyncService(
			SlotManager slotManager,
			ExternalGatewaySyncProperties syncProperties,
			ExternalUpstreamClient upstreamClient
	) {
		this.slotManager = Objects.requireNonNull(slotManager, "slotManager must not be null");
		this.syncProperties = Objects.requireNonNull(syncProperties, "syncProperties must not be null");
		this.upstreamClient = Objects.requireNonNull(upstreamClient, "upstreamClient must not be null");
	}

	/**
	 * Выполняет синхронный вызов внешнего сервиса через общий лимитер слотов.
	 *
	 * @param request запрос сервиса-клиента
	 * @param headers технические заголовки вызова
	 * @return успешный нормализованный ответ внешнего сервиса
	 */
	public ExternalSyncResponse sync(ExternalSyncRequest request, ExternalSyncHeaders headers) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(headers, "headers must not be null");

		long startedAt = System.nanoTime();
		SlotLease lease = acquireSyncSlot(request, headers);
		try {
			ExternalUpstreamResponse upstreamResponse = upstreamClient.call(toUpstreamRequest(request, headers));
			return new ExternalSyncResponse(request.externalId(), ExternalSyncStatus.SUCCEEDED,
					upstreamResponse.result(), upstreamResponse.upstreamStatus(), elapsedMs(startedAt),
					upstreamResponse.upstreamTraceId());
		}
		finally {
			releaseSlot(lease);
		}
	}

	private SlotLease acquireSyncSlot(ExternalSyncRequest request, ExternalSyncHeaders headers) {
		Duration timeout = syncProperties.waitTimeoutMs();
		return slotManager.acquireSyncSlot(owner(request), timeout)
				.orElseThrow(() -> new NoSlotAvailableException(headers.requestId(), timeout));
	}

	private ExternalUpstreamRequest toUpstreamRequest(ExternalSyncRequest request, ExternalSyncHeaders headers) {
		return new ExternalUpstreamRequest(request.externalId(), request.clientService(), request.payload(),
				headers.requestId(), headers.idempotencyKey());
	}

	private void releaseSlot(SlotLease lease) {
		boolean released = slotManager.release(lease.slotId(), lease.leaseId());
		if (!released) {
			log.warn("Не удалось освободить sync-слот: slotId={}, leaseId={}", lease.slotId(), lease.leaseId());
		}
	}

	private static String owner(ExternalSyncRequest request) {
		return request.clientService() + ":" + request.externalId();
	}

	private static long elapsedMs(long startedAt) {
		return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
	}
}
