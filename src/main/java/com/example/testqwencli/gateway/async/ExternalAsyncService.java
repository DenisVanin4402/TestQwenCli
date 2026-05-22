package com.example.testqwencli.gateway.async;

import com.example.testqwencli.gateway.async.config.ExternalGatewayAsyncProperties;
import com.example.testqwencli.gateway.async.error.AsyncIdempotencyConflictException;
import com.example.testqwencli.gateway.async.error.AsyncTaskNotFoundException;
import com.example.testqwencli.gateway.async.error.AsyncTaskStateConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExternalAsyncService {

	private static final Logger log = LoggerFactory.getLogger(ExternalAsyncService.class);

	private final AsyncTaskRepository repository;
	private final ExternalGatewayAsyncProperties properties;
	private final Clock clock;

	public ExternalAsyncService(
			AsyncTaskRepository repository,
			ExternalGatewayAsyncProperties properties,
			Clock clock
	) {
		this.repository = Objects.requireNonNull(repository, "repository must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	/**
	 * Ставит async-задачу в очередь или возвращает существующую задачу по idempotency key.
	 */
	public AsyncSubmitResponse submit(ExternalAsyncRequest request, String requestId) {
		Objects.requireNonNull(request, "request must not be null");

		AsyncSubmitResult result = repository.submit(request, properties.maxAttempts(), clock.instant());
		if (result.type() == AsyncSubmitResultType.IDEMPOTENCY_CONFLICT) {
			throw new AsyncIdempotencyConflictException(requestId, result.existingTaskId(),
					result.conflictingFields());
		}

		AsyncTask task = result.task();
		log.info("Async-задача принята: taskId={}, clientService={}, externalId={}, alreadyExisted={}",
				task.taskId(), task.clientService(), task.externalId(), result.alreadyExisted());
		return AsyncSubmitResponse.from(task, result.alreadyExisted());
	}

	/**
	 * Возвращает задачу по внутреннему идентификатору.
	 */
	public AsyncTask getByTaskId(long taskId, String clientService, String requestId) {
		return repository.findByTaskId(taskId, clientServiceScope(clientService))
				.orElseThrow(() -> new AsyncTaskNotFoundException(requestId, taskId));
	}

	/**
	 * Возвращает задачу по externalId.
	 */
	public AsyncTask getByExternalId(UUID externalId, String clientService, String requestId) {
		Objects.requireNonNull(externalId, "externalId must not be null");
		Optional<String> scope = clientServiceScope(clientService);
		return repository.findByExternalId(externalId, scope)
				.orElseThrow(() -> new AsyncTaskNotFoundException(requestId, externalId, scope.orElse(null)));
	}

	/**
	 * Отменяет PENDING-задачу. Повторная отмена CANCELLED-задачи идемпотентна.
	 */
	public AsyncTask cancel(long taskId, String clientService, String requestId) {
		AsyncTaskUpdateResult result = repository.cancel(taskId, clientServiceScope(clientService), clock.instant());
		return resolveUpdateResult(taskId, requestId, result);
	}

	/**
	 * Возвращает задачу в очередь для ручного retry, если ее состояние это разрешает.
	 */
	public AsyncTask retry(long taskId, String clientService, String requestId) {
		AsyncTaskUpdateResult result = repository.retry(taskId, clientServiceScope(clientService), clock.instant());
		return resolveUpdateResult(taskId, requestId, result);
	}

	private AsyncTask resolveUpdateResult(long taskId, String requestId, AsyncTaskUpdateResult result) {
		if (result.status() == AsyncTaskUpdateStatus.NOT_FOUND) {
			throw new AsyncTaskNotFoundException(requestId, taskId);
		}
		if (result.status() == AsyncTaskUpdateStatus.CONFLICT) {
			throw new AsyncTaskStateConflictException(requestId, taskId, result.task().status(), result.message());
		}
		return result.task();
	}

	private Optional<String> clientServiceScope(String clientService) {
		if (clientService == null || clientService.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(clientService.trim());
	}
}
