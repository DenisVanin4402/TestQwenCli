package com.example.testqwencli.gateway.callback;

import com.example.testqwencli.gateway.async.AsyncDeliveryMode;
import com.example.testqwencli.gateway.async.AsyncTask;
import com.example.testqwencli.gateway.async.AsyncTaskRepository;
import com.example.testqwencli.gateway.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.async.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.callback.config.ExternalGatewayCallbackProperties;
import com.example.testqwencli.gateway.callback.config.ExternalGatewayClientsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class CallbackDeliveryPlanner {

	private static final Logger log = LoggerFactory.getLogger(CallbackDeliveryPlanner.class);

	private final CallbackDeliveryRepository deliveryRepository;
	private final AsyncTaskRepository taskRepository;
	private final ExternalGatewayCallbackProperties callbackProperties;
	private final ExternalGatewayClientsProperties clientsProperties;
	private final Clock clock;

	public CallbackDeliveryPlanner(
			CallbackDeliveryRepository deliveryRepository,
			AsyncTaskRepository taskRepository,
			ExternalGatewayCallbackProperties callbackProperties,
			ExternalGatewayClientsProperties clientsProperties,
			Clock clock
	) {
		this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository must not be null");
		this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
		this.callbackProperties = Objects.requireNonNull(callbackProperties, "callbackProperties must not be null");
		this.clientsProperties = Objects.requireNonNull(clientsProperties, "clientsProperties must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	/**
	 * Планирует callback-доставку для финальной async-задачи.
	 */
	public Optional<CallbackDelivery> planForFinalTask(AsyncTask task) {
		Objects.requireNonNull(task, "task must not be null");
		if (!isFinal(task.status())) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		if (task.deliveryMode() != AsyncDeliveryMode.CALLBACK) {
			taskRepository.updateCallbackDeliveryStatus(task.taskId(), CallbackDeliveryStatus.NOT_REQUIRED, now);
			return Optional.empty();
		}

		try {
			CallbackDelivery delivery = createDelivery(task, now);
			taskRepository.updateCallbackDeliveryStatus(task.taskId(), delivery.status(), now);
			log.info("Callback-доставка запланирована: taskId={}, clientService={}, status={}",
					task.taskId(), task.clientService(), delivery.status());
			return Optional.of(delivery);
		}
		catch (RuntimeException exception) {
			log.warn("Не удалось запланировать callback-доставку: taskId={}, clientService={}, error={}",
					task.taskId(), task.clientService(), exception.toString());
			log.debug("Детали ошибки планирования callback-доставки: taskId={}", task.taskId(), exception);
			return Optional.empty();
		}
	}

	private CallbackDelivery createDelivery(AsyncTask task, Instant now) {
		Optional<URI> callbackUrl = clientsProperties.callbackUrl(task.clientService());
		if (callbackUrl.isEmpty()) {
			String message = "Callback URL не настроен для clientService=" + task.clientService();
			CallbackDelivery delivery = deliveryRepository.createDead(task, message, callbackProperties.maxAttempts(),
					now);
			log.warn("Callback-доставка переведена в DEAD из-за отсутствия allow-list URL: taskId={}, clientService={}",
					task.taskId(), task.clientService());
			return delivery;
		}
		return deliveryRepository.createPending(task, callbackUrl.orElseThrow(), callbackProperties.maxAttempts(),
				now);
	}

	private static boolean isFinal(AsyncTaskStatus status) {
		return status == AsyncTaskStatus.DONE
				|| status == AsyncTaskStatus.FAILED
				|| status == AsyncTaskStatus.DEAD
				|| status == AsyncTaskStatus.CANCELLED;
	}
}
