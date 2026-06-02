package com.example.testqwencli.dashboard;

import com.example.testqwencli.gateway.exception.AsyncIdempotencyConflictException;
import com.example.testqwencli.gateway.exception.ExternalGatewayException;
import com.example.testqwencli.gateway.exception.NoSlotAvailableException;
import com.example.testqwencli.gateway.model.async.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.AsyncPriority;
import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.callback.CallbackDelivery;
import com.example.testqwencli.gateway.model.sync.ExternalSyncHeaders;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import com.example.testqwencli.gateway.services.ExternalAsyncService;
import com.example.testqwencli.gateway.services.ExternalSyncService;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
class GatewayDashboardClientAdapter implements DashboardGatewayClient {

	private final ExternalSyncService syncService;
	private final ExternalAsyncService asyncService;

	GatewayDashboardClientAdapter(ExternalSyncService syncService, ExternalAsyncService asyncService) {
		this.syncService = Objects.requireNonNull(syncService, "syncService must not be null");
		this.asyncService = Objects.requireNonNull(asyncService, "asyncService must not be null");
	}

	@Override
	public DashboardCallOutcome callSync(DashboardGatewayRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		long startedAt = System.nanoTime();
		try {
			syncService.sync(new ExternalSyncRequest(request.externalId(), request.clientService(), request.payload()),
					new ExternalSyncHeaders(request.requestId(), request.externalId().toString()));
			return new DashboardCallOutcome(DashboardCallStatus.SUCCESS, elapsedMs(startedAt), null);
		}
		catch (NoSlotAvailableException exception) {
			return new DashboardCallOutcome(DashboardCallStatus.NO_SLOT, elapsedMs(startedAt), exception.code());
		}
		catch (ExternalGatewayException exception) {
			return new DashboardCallOutcome(callStatus(exception), elapsedMs(startedAt), exception.code());
		}
		catch (RuntimeException exception) {
			return new DashboardCallOutcome(DashboardCallStatus.ERROR, elapsedMs(startedAt),
					exception.getClass().getSimpleName());
		}
	}

	@Override
	public DashboardSubmitOutcome submitAsync(DashboardGatewayRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		long startedAt = System.nanoTime();
		try {
			AsyncSubmitResponse response = asyncService.submit(new ExternalAsyncRequest(
					request.externalId(),
					request.clientService(),
					priority(request.priority()),
					request.callbackDelivery() ? AsyncDeliveryMode.CALLBACK : AsyncDeliveryMode.POLLING,
					request.payload()
			), request.requestId());
			return new DashboardSubmitOutcome(DashboardSubmitStatus.ACCEPTED, elapsedMs(startedAt), null,
					response.taskId());
		}
		catch (AsyncIdempotencyConflictException exception) {
			return new DashboardSubmitOutcome(DashboardSubmitStatus.REJECTED, elapsedMs(startedAt),
					"IDEMPOTENCY_CONFLICT", null);
		}
		catch (RuntimeException exception) {
			return new DashboardSubmitOutcome(DashboardSubmitStatus.ERROR, elapsedMs(startedAt),
					exception.getClass().getSimpleName(), null);
		}
	}

	private static AsyncPriority priority(DashboardRequestPriority priority) {
		return priority == DashboardRequestPriority.HIGH ? AsyncPriority.HIGH : AsyncPriority.LOW;
	}

	private static DashboardCallStatus callStatus(ExternalGatewayException exception) {
		String code = exception.code();
		if (code != null && code.contains("TIMEOUT")) {
			return DashboardCallStatus.TIMEOUT;
		}
		return DashboardCallStatus.ERROR;
	}

	private static long elapsedMs(long startedAt) {
		return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
	}
}
