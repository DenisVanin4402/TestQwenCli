package com.example.testqwencli.gateway.sync;

import com.example.testqwencli.gateway.async.AsyncTaskRepository;
import com.example.testqwencli.gateway.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.async.SyncRequestTrace;
import com.example.testqwencli.gateway.async.TaskError;
import com.example.testqwencli.gateway.slot.SlotLease;
import com.example.testqwencli.gateway.slot.SlotManager;
import com.example.testqwencli.gateway.sync.config.ExternalGatewaySyncProperties;
import com.example.testqwencli.gateway.sync.error.ExternalGatewayException;
import com.example.testqwencli.gateway.sync.error.NoSlotAvailableException;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamClient;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamRequest;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class ExternalSyncService {

	private static final Logger log = LoggerFactory.getLogger(ExternalSyncService.class);

	private final SlotManager slotManager;
	private final ExternalGatewaySyncProperties syncProperties;
	private final ExternalUpstreamClient upstreamClient;
	private final AsyncTaskRepository taskRepository;
	private final Clock clock;

	public ExternalSyncService(
			SlotManager slotManager,
			ExternalGatewaySyncProperties syncProperties,
			ExternalUpstreamClient upstreamClient,
			AsyncTaskRepository taskRepository,
			Clock clock
	) {
		this.slotManager = Objects.requireNonNull(slotManager, "slotManager must not be null");
		this.syncProperties = Objects.requireNonNull(syncProperties, "syncProperties must not be null");
		this.upstreamClient = Objects.requireNonNull(upstreamClient, "upstreamClient must not be null");
		this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	public ExternalSyncResponse sync(ExternalSyncRequest request, ExternalSyncHeaders headers) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(headers, "headers must not be null");

		Instant traceStartedAt = clock.instant();
		long startedNanos = System.nanoTime();
		try {
			SlotLease lease = acquireSyncSlot(request, headers);
			ExternalSyncResponse response = callUpstreamWithLease(request, headers, startedNanos, lease);
			recordSyncTrace(successTrace(request, response, traceStartedAt, clock.instant()));
			return response;
		}
		catch (NoSlotAvailableException exception) {
			recordSyncTrace(failureTrace(request, exception, 0, traceStartedAt, clock.instant()));
			log.warn("Sync-запрос отклонен: externalId={}, clientService={}, code={}",
					request.externalId(), request.clientService(), exception.code());
			throw exception;
		}
		catch (RuntimeException exception) {
			recordSyncTrace(failureTrace(request, exception, 1, traceStartedAt, clock.instant()));
			log.warn("Sync-запрос завершился ошибкой: externalId={}, clientService={}, error={}",
					request.externalId(), request.clientService(), exception.toString());
			log.debug("Детали ошибки sync-запроса: externalId={}", request.externalId(), exception);
			throw exception;
		}
	}

	private ExternalSyncResponse callUpstreamWithLease(
			ExternalSyncRequest request,
			ExternalSyncHeaders headers,
			long startedNanos,
			SlotLease lease
	) {
		try {
			ExternalUpstreamResponse upstreamResponse = upstreamClient.call(toUpstreamRequest(request, headers));
			long durationMs = elapsedMs(startedNanos);
			log.info("Sync-запрос завершен: externalId={}, clientService={}, durationMs={}, upstreamStatus={}",
					request.externalId(), request.clientService(), durationMs, upstreamResponse.upstreamStatus());
			return new ExternalSyncResponse(request.externalId(), ExternalSyncStatus.SUCCEEDED,
					upstreamResponse.result(), upstreamResponse.upstreamStatus(), durationMs,
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

	private SyncRequestTrace successTrace(ExternalSyncRequest request, ExternalSyncResponse response,
			Instant startedAt, Instant finishedAt) {
		return new SyncRequestTrace(request.externalId(), request.clientService(), request.payload(),
				AsyncTaskStatus.DONE, responseResult(response), null, 1, startedAt, finishedAt, null);
	}

	private SyncRequestTrace failureTrace(ExternalSyncRequest request, RuntimeException exception, int attempts,
			Instant startedAt, Instant finishedAt) {
		TaskError error = taskError(exception);
		return new SyncRequestTrace(request.externalId(), request.clientService(), request.payload(),
				AsyncTaskStatus.FAILED, null, error, attempts, startedAt, finishedAt, error.message());
	}

	private TaskError taskError(RuntimeException exception) {
		if (exception instanceof ExternalGatewayException gatewayException) {
			return new TaskError(gatewayException.code(), gatewayException.getMessage(),
					gatewayException.retryable());
		}
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			message = exception.getClass().getSimpleName();
		}
		return new TaskError("SYNC_RUNTIME_FAILURE", message, true);
	}

	private void recordSyncTrace(SyncRequestTrace trace) {
		try {
			taskRepository.recordSyncTrace(trace);
		}
		catch (RuntimeException exception) {
			log.warn("Не удалось сохранить trace sync-запроса: externalId={}, clientService={}, error={}",
					trace.externalId(), trace.clientService(), exception.toString());
			log.debug("Детали ошибки сохранения sync trace: externalId={}", trace.externalId(), exception);
		}
	}

	private Map<String, Object> responseResult(ExternalSyncResponse response) {
		LinkedHashMap<String, Object> result = new LinkedHashMap<>();
		result.putAll(response.result());
		return result;
	}

	private static String owner(ExternalSyncRequest request) {
		return request.clientService() + ":" + request.externalId();
	}

	private static long elapsedMs(long startedNanos) {
		return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
	}
}
