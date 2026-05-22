package com.example.testqwencli.gateway.callback;

import com.example.testqwencli.gateway.async.AsyncTaskRepository;
import com.example.testqwencli.gateway.callback.config.ExternalGatewayCallbackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class CallbackDeliveryDispatcher {

	private static final Logger log = LoggerFactory.getLogger(CallbackDeliveryDispatcher.class);

	private final CallbackDeliveryRepository deliveryRepository;
	private final AsyncTaskRepository taskRepository;
	private final CallbackClient callbackClient;
	private final ExternalGatewayCallbackProperties properties;
	private final Clock clock;

	public CallbackDeliveryDispatcher(
			CallbackDeliveryRepository deliveryRepository,
			AsyncTaskRepository taskRepository,
			CallbackClient callbackClient,
			ExternalGatewayCallbackProperties properties,
			Clock clock
	) {
		this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository must not be null");
		this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
		this.callbackClient = Objects.requireNonNull(callbackClient, "callbackClient must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	/**
	 * Выполняет одну доступную callback-доставку.
	 */
	public boolean dispatchOnce() {
		Optional<CallbackDelivery> claimedDelivery = deliveryRepository.claimNextPending(clock.instant());
		if (claimedDelivery.isEmpty()) {
			return false;
		}

		CallbackDelivery delivery = claimedDelivery.orElseThrow();
		taskRepository.updateCallbackDeliveryStatus(delivery.taskId(), delivery.status(), clock.instant());
		try {
			CallbackClientResponse response = callbackClient.send(delivery.payload(), delivery.callbackUrl(),
					delivery.attempt(), delivery.payload().eventId().toString());
			if (response.successful()) {
				markDelivered(delivery);
				return true;
			}
			markFailed(delivery, "Callback endpoint вернул HTTP " + response.statusCode());
			return true;
		}
		catch (RuntimeException exception) {
			markFailed(delivery, normalizeErrorMessage(exception));
			return true;
		}
	}

	private void markDelivered(CallbackDelivery delivery) {
		Instant now = clock.instant();
		deliveryRepository.markDelivered(delivery.deliveryId(), now)
				.ifPresent(updated -> {
					taskRepository.updateCallbackDeliveryStatus(updated.taskId(), updated.status(), now);
					log.info("Callback-доставка выполнена: taskId={}, clientService={}, attempt={}",
							updated.taskId(), updated.clientService(), updated.attempt());
				});
	}

	private void markFailed(CallbackDelivery delivery, String message) {
		Instant now = clock.instant();
		deliveryRepository.markRetryOrDead(delivery.deliveryId(), message, properties.retryBackoffMs(), now)
				.ifPresent(updated -> {
					taskRepository.updateCallbackDeliveryStatus(updated.taskId(), updated.status(), now);
					log.warn("Callback-доставка завершилась ошибкой: taskId={}, clientService={}, status={}, error={}",
							updated.taskId(), updated.clientService(), updated.status(), updated.lastError());
				});
	}

	private static String normalizeErrorMessage(RuntimeException exception) {
		if (exception.getMessage() == null || exception.getMessage().isBlank()) {
			return exception.toString();
		}
		return exception.getMessage();
	}
}
