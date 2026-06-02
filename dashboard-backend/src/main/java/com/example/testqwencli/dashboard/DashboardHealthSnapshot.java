package com.example.testqwencli.dashboard;

import java.util.Map;

/**
 * Снимок технического состояния gateway, который дашборд показывает рядом с метриками нагрузки.
 *
 * @param repositoryMode активный режим хранения gateway: память или PostgreSQL.
 * @param slots состояние пула слотов.
 * @param asyncQueue состояние очереди async-задач.
 * @param callbacks состояние очереди callback-доставок.
 * @param dispatchers включенность фоновых dispatch-механизмов.
 */
public record DashboardHealthSnapshot(
		String repositoryMode,
		SlotPoolHealth slots,
		AsyncQueueHealth asyncQueue,
		CallbackDeliveryHealth callbacks,
		DispatcherHealth dispatchers
) {

	/**
	 * Агрегированное состояние слотов gateway.
	 *
	 * @param total общее количество слотов в пуле.
	 * @param syncBusy количество занятых синхронными запросами слотов.
	 * @param asyncBusy количество занятых асинхронной обработкой слотов.
	 * @param free количество свободных слотов.
	 * @param syncReserve минимальный резерв слотов, который остается доступным для sync-потока.
	 * @param asyncAllowed количество слотов, которые async-поток может занимать с учетом sync-резерва.
	 * @param liveSyncWaiters количество sync-запросов, которые сейчас ждут освобождения слота.
	 */
	public record SlotPoolHealth(
			int total,
			long syncBusy,
			long asyncBusy,
			long free,
			int syncReserve,
			long asyncAllowed,
			long liveSyncWaiters
	) {
	}

	/**
	 * Состояние таблицы/очереди async-задач.
	 *
	 * @param byStatus распределение задач по статусам {@code PENDING}, {@code IN_PROGRESS}, {@code RETRY},
	 * {@code DONE}, {@code DEAD}, {@code CANCELLED}.
	 * @param pending количество задач, готовых к взятию в обработку.
	 * @param inProgress количество задач, которые сейчас обрабатываются.
	 * @param done количество успешно завершенных задач.
	 * @param dead количество задач, окончательно завершенных ошибкой.
	 * @param cancelled количество отмененных задач.
	 * @param retry количество задач, ожидающих повторной обработки после backoff.
	 * @param withoutAnswer количество задач без финального ответа upstream.
	 * @param oldestActiveAgeSeconds возраст самой старой активной задачи в секундах.
	 */
	public record AsyncQueueHealth(
			Map<String, Long> byStatus,
			long pending,
			long inProgress,
			long done,
			long dead,
			long cancelled,
			long retry,
			long withoutAnswer,
			long oldestActiveAgeSeconds
	) {
	}

	/**
	 * Состояние очереди callback-доставок.
	 *
	 * @param byStatus распределение доставок по статусам {@code PENDING}, {@code DELIVERING}, {@code RETRY},
	 * {@code DELIVERED}, {@code DEAD}, {@code NOT_REQUIRED}.
	 * @param backlog количество доставок, которые еще требуют отправки.
	 * @param retry количество доставок, ожидающих следующей попытки.
	 * @param dead количество доставок, по которым попытки исчерпаны.
	 * @param oldestBacklogAgeSeconds возраст самой старой недоставленной записи в секундах.
	 */
	public record CallbackDeliveryHealth(
			Map<String, Long> byStatus,
			long backlog,
			long retry,
			long dead,
			long oldestBacklogAgeSeconds
	) {
	}

	/**
	 * Состояние фоновых обработчиков gateway.
	 *
	 * @param asyncDispatcherEnabled {@code true}, если включен плановый обработчик async-очереди.
	 * @param callbackDispatcherEnabled {@code true}, если включен плановый обработчик callback-очереди.
	 */
	public record DispatcherHealth(
			boolean asyncDispatcherEnabled,
			boolean callbackDispatcherEnabled
	) {
	}
}
