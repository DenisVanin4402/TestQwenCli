package com.example.testqwencli.gateway.integration;

import com.example.testqwencli.gateway.config.ExternalGatewaySlotProperties;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.model.slot.enums.SyncAcquireWaitMode;
import com.example.testqwencli.gateway.repository.SlotRepository;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotReleaseNotificationListener;
import com.example.testqwencli.gateway.services.SlotManager;
import com.example.testqwencli.gateway.services.SyncSlotReleaseNotifier;
import com.example.testqwencli.gateway.services.impl.ListenNotifySyncSlotWaitStrategy;
import com.example.testqwencli.gateway.services.impl.SlotManagerImpl;
import com.example.testqwencli.gateway.support.PostgresIntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
		"external-gateway.repository.type=postgres",
		"external-gateway.postgres.liquibase-enabled=true",
		"external-gateway.slots.sync-acquire-wait-mode=listen_notify",
		"external-gateway.slots.sync-acquire-poll-interval=5s",
		"external-gateway.slots.lease-reap-interval-ms=600000",
		"external-gateway.async.dispatcher-enabled=false",
		"external-gateway.callback.delivery-enabled=false"
})
class PostgresSlotListenNotifyIT extends PostgresIntegrationTestSupport {

	// Совпадает с production-настройкой числа sync-slots, созданных Liquibase changelog.
	private static final int TOTAL_SLOTS = 5;
	// PostgreSQL channel, на который подписан production LISTEN worker.
	private static final String SLOT_RELEASE_CHANNEL = "external_gateway_slot_released";
	// Верхняя граница ожидания acquire: тесты должны завершаться раньше через NOTIFY или короткий fallback.
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);

	// Production SlotManager, собранный Spring context-ом в режиме PostgreSQL LISTEN/NOTIFY.
	@Autowired
	private SlotManager slotManager;

	// Repository используется для ручной сборки SlotManager с управляемым fallback interval.
	@Autowired
	private SlotRepository slotRepository;

	// Локальный notifier получает сигнал от PostgreSQL listener и будит ожидающие acquire.
	@Autowired
	private SyncSlotReleaseNotifier notifier;

	// Production listener нужен тесту, чтобы проверить реальную подписку PostgreSQL LISTEN.
	@Autowired
	private PostgresSlotReleaseNotificationListener notificationListener;

	/**
	 * Очищает runtime-таблицы gateway перед каждым сценарием, чтобы тесты не зависели от порядка запуска.
	 */
	@BeforeEach
	void cleanBeforeTest() {
		// Сбрасываем очереди, waiter-записи и активные lease, сохраняя Liquibase-схему и базовые slot-строки.
		cleanGatewayTables();
	}

	/**
	 * Очищает runtime-таблицы gateway после каждого сценария, чтобы следующий integration-тест получил чистую БД.
	 */
	@AfterEach
	void cleanAfterTest() {
		// Повторная очистка защищает от частично завершенных сценариев и отмененных фоновых ожиданий.
		cleanGatewayTables();
	}

	/**
	 * Проверяет, что ожидающий sync acquire просыпается от реального PostgreSQL NOTIFY, а не от длинного fallback
	 * timeout.
	 */
	@Test
	void waitingSyncAcquireWakesAfterPostgresNotifyWithoutWaitingForFullFallbackTimeout() throws Exception {
		// Сначала убеждаемся, что listener уже выполнил LISTEN и способен получать PostgreSQL notifications.
		ensurePostgresListenerReceivesNotification();

		// Занимаем все sync-слоты, чтобы следующий sync acquire гарантированно перешел в режим ожидания.
		List<SlotLease> leases = acquireAllSyncSlots(slotManager);
		AtomicReference<Thread> waitingThread = new AtomicReference<>();
		ExecutorService executor = singleThreadExecutor(waitingThread, "postgres-listen-notify-sync-acquire");
		AtomicReference<SlotLease> acquiredLease = new AtomicReference<>();

		// Запускаем ожидающий acquire в отдельном потоке, потому что основной поток будет освобождать слот.
		Future<Optional<SlotLease>> waitingAcquire = executor.submit(
				() -> slotManager.acquireSyncSlot("listen-notify-waiter", WAIT_TIMEOUT));

		try {
			// Дожидаемся, пока acquire зарегистрирует sync waiter и реально заблокируется на wait strategy.
			awaitWaitingSyncAcquire(waitingThread);

			// Освобождаем слот SQL-ом в обход SlotReleaseNotificationPublisher, чтобы локальный notifier не помог тесту.
			SlotLease releasedLease = leases.getFirst();
			releaseSlotWithoutPublisher(releasedLease);

			// Отправляем PostgreSQL NOTIFY вручную: это единственный быстрый сигнал для ожидающего acquire.
			sendPostgresSlotReleasedNotification();

			// Если LISTEN/NOTIFY работает, acquire завершается быстрее 5-секундного fallback interval.
			Optional<SlotLease> result = waitingAcquire.get(2, TimeUnit.SECONDS);
			assertThat(result).isPresent();
			acquiredLease.set(result.orElseThrow());
			assertThat(acquiredLease.get().slotId()).isEqualTo(releasedLease.slotId());
			assertThat(liveSyncWaiters()).isZero();
		}
		finally {
			// Гарантированно останавливаем фоновые ожидания и освобождаем полученный lease, если сценарий упал позже.
			cancelIfRunning(waitingAcquire);
			releaseIfPresent(acquiredLease.get());
			executor.shutdownNow();
		}
	}

	/**
	 * Проверяет, что при множестве ожидающих sync acquire одно освобождение слота выдает lease ровно одному waiter.
	 */
	@Test
	void postgresNotifyLetsOnlyOneOfManyWaitingSyncAcquiresTakeOneReleasedSlot() throws Exception {
		// Базовый сценарий: десять waiters получают сигнал, но свободным становится только один slot.
		assertPostgresNotifyCompletesExactlyReleasedSlotCount(1);
	}

	/**
	 * Проверяет, что при множестве ожидающих sync acquire количество завершенных ожиданий равно числу освобожденных
	 * слотов.
	 */
	@Test
	void postgresNotifyLetsExactlyReleasedSlotCountWaitingSyncAcquiresTakeReleasedSlots() throws Exception {
		// Зеркальный сценарий: число успешных acquire должно совпасть с количеством освобожденных slots.
		assertPostgresNotifyCompletesExactlyReleasedSlotCount(3);
	}

	/**
	 * Проверяет fallback-поведение LISTEN/NOTIFY strategy: потерянная notification не должна зависнуть ожидающий
	 * sync acquire навсегда.
	 */
	@Test
	void waitingSyncAcquireFallsBackWhenPostgresNotifyIsLost() throws Exception {
		// Создаем отдельный SlotManager с коротким fallback interval, чтобы тест был быстрым и детерминированным.
		SlotManager fallbackManager = fallbackSlotManager(Duration.ofMillis(100));

		// Занимаем все sync-слоты через тот же manager, который затем будет ждать освобождения слота.
		List<SlotLease> leases = acquireAllSyncSlots(fallbackManager);
		AtomicReference<Thread> waitingThread = new AtomicReference<>();
		ExecutorService executor = singleThreadExecutor(waitingThread, "postgres-listen-notify-fallback");
		AtomicReference<SlotLease> acquiredLease = new AtomicReference<>();

		// Запускаем ожидающий acquire и оставляем основной поток управлять состоянием БД.
		Future<Optional<SlotLease>> waitingAcquire = executor.submit(
				() -> fallbackManager.acquireSyncSlot("listen-notify-fallback-waiter", WAIT_TIMEOUT));

		try {
			// Проверяем, что acquire успел зарегистрировать waiter и ждать сигнал/fallback timeout.
			awaitWaitingSyncAcquire(waitingThread);

			// Освобождаем слот без NOTIFY: listener не получает сигнал, поэтому сработать должен fallback timeout.
			SlotLease releasedLease = leases.getFirst();
			releaseSlotWithoutPublisher(releasedLease);

			// Acquire должен проснуться на коротком fallback interval и забрать освобожденный слот.
			Optional<SlotLease> result = waitingAcquire.get(2, TimeUnit.SECONDS);
			assertThat(result).isPresent();
			acquiredLease.set(result.orElseThrow());
			assertThat(acquiredLease.get().slotId()).isEqualTo(releasedLease.slotId());
			assertThat(liveSyncWaiters()).isZero();
		}
		finally {
			// Очищаем фоновые ресурсы и lease независимо от результата assertions.
			cancelIfRunning(waitingAcquire);
			releaseIfPresent(acquiredLease.get());
			executor.shutdownNow();
		}
	}

	private void assertPostgresNotifyCompletesExactlyReleasedSlotCount(int releaseCount) throws Exception {
		// Сначала подтверждаем, что быстрый wake-up действительно пойдет через PostgreSQL LISTEN/NOTIFY.
		ensurePostgresListenerReceivesNotification();

		// Создаем в два раза больше contenders, чем доступных slots, чтобы после NOTIFY остались ожидающие waiters.
		int waiterCount = TOTAL_SLOTS * 2;
		// Занимаем все slots, чтобы каждый contender гарантированно зарегистрировал sync waiter.
		List<SlotLease> leases = acquireAllSyncSlots(slotManager);
		// Потоки сохраняются отдельно: по ним тест проверяет, что все contenders реально заблокировались.
		List<Thread> waitingThreads = new CopyOnWriteArrayList<>();
		ExecutorService executor = fixedThreadExecutor(waiterCount, waitingThreads, "postgres-listen-notify-contender");
		// CompletionService позволяет забрать только завершившиеся acquire, не дожидаясь timeout остальных waiters.
		ExecutorCompletionService<Optional<SlotLease>> completionService = new ExecutorCompletionService<>(executor);
		// Полученные leases собираются отдельно, чтобы корректно освободить их в finally.
		List<SlotLease> acquiredLeases = new ArrayList<>();

		// Все contenders запускают обычный SlotManager.acquireSyncSlot и конкурируют за один общий набор rows.
		List<Future<Optional<SlotLease>>> waitingAcquires = IntStream.range(0, waiterCount)
				.mapToObj(index -> completionService.submit(
						() -> slotManager.acquireSyncSlot("listen-notify-contender-" + index, WAIT_TIMEOUT)))
				.toList();

		try {
			// До освобождения slots все contenders должны уже стоять в ext_sync_waiters.
			awaitWaitingSyncAcquires(waiterCount, waitingThreads);

			// Освобождаем ровно releaseCount slots прямым SQL, чтобы локальный publisher не будил waiters сам.
			List<SlotLease> releasedLeases = leases.stream()
					.limit(releaseCount)
					.toList();
			releasedLeases.forEach(this::releaseSlotWithoutPublisher);

			// Один PostgreSQL NOTIFY будит всех waiters, после чего source of truth остается row-level acquire.
			sendPostgresSlotReleasedNotification();

			// Ровно releaseCount acquire должны быстро завершиться и вернуть lease на освобожденные slots.
			acquiredLeases.addAll(takeCompletedLeases(completionService, releaseCount, Duration.ofSeconds(2)));
			// Дополнительное завершение до fallback interval означало бы, что лишний waiter получил несуществующий slot.
			assertThat(completionService.poll(1, TimeUnit.SECONDS))
					.as("лишний ожидающий sync acquire не должен получить lease до fallback interval")
					.isNull();
			// Остальные waiters остаются зарегистрированными, потому что slots для них еще не освободились.
			assertThat(liveSyncWaiters()).isEqualTo(waiterCount - releaseCount);
			// Полученные leases должны соответствовать только тем slots, которые тест освободил перед NOTIFY.
			assertThat(acquiredLeases).hasSize(releaseCount);
			assertThat(acquiredLeases).extracting(SlotLease::slotId)
					.containsExactlyInAnyOrderElementsOf(releasedLeases.stream()
							.map(SlotLease::slotId)
							.toList());
			// Дубли по slotId/leaseId запрещены: один physical slot не может быть выдан двум waiters.
			assertThat(acquiredLeases).extracting(SlotLease::slotId).doesNotHaveDuplicates();
			assertThat(acquiredLeases).extracting(SlotLease::leaseId).doesNotHaveDuplicates();
		}
		finally {
			// Невыигравшие waiters прерываются, чтобы тест не ждал их 10-секундного timeout.
			cancelIfRunning(waitingAcquires);
			// Успешно полученные leases освобождаются через production SlotManager.
			acquiredLeases.forEach(this::releaseIfPresent);
			executor.shutdownNow();
		}
	}

	/**
	 * Проверяет готовность PostgreSQL listener: бин запущен, выполнил LISTEN и увеличивает локальную signal version
	 * после тестового NOTIFY.
	 */
	private void ensurePostgresListenerReceivesNotification() {
		// SmartLifecycle должен быть запущен Spring context-ом при старте теста.
		assertThat(notificationListener.isRunning()).isTrue();

		// Фиксируем текущую signal version, чтобы отличить новое notification-событие от старого состояния.
		long observedSignalVersion = notifier.currentSignalVersion();

		// Повторно отправляем NOTIFY до тех пор, пока listener не подтвердит прием через локальный notifier.
		asyncAwaiter(Duration.ofSeconds(5)).until("PostgreSQL LISTEN получает тестовый NOTIFY",
				() -> {
					sendPostgresSlotReleasedNotification();
					return notifier.currentSignalVersion();
				},
				signalVersion -> signalVersion > observedSignalVersion);
	}

	/**
	 * Дожидается, что ожидающий sync acquire зарегистрировал waiter в PostgreSQL и перешел в состояние ожидания.
	 */
	private void awaitWaitingSyncAcquire(AtomicReference<Thread> waitingThread) {
		// Проверяем оба признака ожидания: запись в ext_sync_waiters и блокировку отдельного Java-потока.
		asyncAwaiter(Duration.ofSeconds(5)).untilAsserted("sync acquire зарегистрировал waiter и ждет сигнал",
				() -> {
					assertThat(liveSyncWaiters()).isEqualTo(1);
					assertThat(waitingThread.get()).isNotNull();
					assertThat(isBlockedWaitingThread(waitingThread.get())).isTrue();
				});
	}

	/**
	 * Дожидается, что все конкурентные sync acquire зарегистрировали waiters и ждут notification/fallback.
	 */
	private void awaitWaitingSyncAcquires(int waiterCount, List<Thread> waitingThreads) {
		// Все waiters должны войти в ожидание до NOTIFY, иначе тест может проверить не тот порядок событий.
		asyncAwaiter(Duration.ofSeconds(5)).untilAsserted("все sync acquire зарегистрировали waiters и ждут сигнал",
				() -> {
					assertThat(liveSyncWaiters()).isEqualTo(waiterCount);
					assertThat(waitingThreads).hasSize(waiterCount);
					assertThat(waitingThreads).allSatisfy(thread ->
							assertThat(isBlockedWaitingThread(thread)).isTrue());
				});
	}

	/**
	 * Создает тестовый SlotManager поверх тех же PostgreSQL repository/notifier, но с управляемым fallback interval.
	 */
	private SlotManager fallbackSlotManager(Duration fallbackInterval) {
		// Переиспользуем production-значения слотов и меняем только interval ожидания для быстрого fallback-сценария.
		ExternalGatewaySlotProperties properties = new ExternalGatewaySlotProperties(
				TOTAL_SLOTS,
				1,
				Duration.ofSeconds(30),
				Duration.ofSeconds(5),
				fallbackInterval,
				SyncAcquireWaitMode.LISTEN_NOTIFY
		);
		// Собираем manager вручную, чтобы не менять Spring context и не влиять на основной LISTEN/NOTIFY сценарий.
		return new SlotManagerImpl(slotRepository, Clock.systemUTC(), new ListenNotifySyncSlotWaitStrategy(notifier),
				properties);
	}

	/**
	 * Занимает все sync-слоты через переданный SlotManager и возвращает lease для последующего освобождения.
	 */
	private List<SlotLease> acquireAllSyncSlots(SlotManager manager) {
		// Количество попыток равно настроенному числу слотов, поэтому следующий sync acquire обязан ждать.
		return IntStream.range(0, TOTAL_SLOTS)
				.mapToObj(index -> manager.acquireSyncSlot("preoccupied-sync-" + index, Duration.ZERO)
						.orElseThrow())
				.toList();
	}

	/**
	 * Освобождает slot прямым SQL без вызова `SlotReleaseNotificationPublisher`.
	 */
	private void releaseSlotWithoutPublisher(SlotLease lease) {
		// Прямой UPDATE нужен, чтобы тест не получил локальный signal из production publisher в обход PostgreSQL.
		int updated = jdbcTemplate().update("""
				UPDATE %s.ext_slots
				SET lease_id = NULL,
				    owner = NULL,
				    kind = NULL,
				    acquired_at = NULL,
				    expires_at = NULL,
				    task_id = NULL
				WHERE slot_id = ?
				  AND lease_id = ?
				""".formatted(POSTGRES_SCHEMA), lease.slotId(), lease.leaseId());
		// Ровно одна измененная строка подтверждает, что освобожден именно ожидаемый lease.
		assertThat(updated).isEqualTo(1);
	}

	/**
	 * Отправляет notification в тот же PostgreSQL channel, на который подписан production listener.
	 */
	private void sendPostgresSlotReleasedNotification() {
		// Канал намеренно совпадает с production-константой PostgresSlotNotificationChannels.SLOT_RELEASED.
		jdbcTemplate().execute("NOTIFY " + SLOT_RELEASE_CHANNEL);
	}

	/**
	 * Возвращает текущее количество sync waiters в PostgreSQL.
	 */
	private Long liveSyncWaiters() {
		// Тесты сверяют состояние waiters напрямую в БД, чтобы отличать успешный acquire от продолжающегося ожидания.
		return jdbcTemplate().queryForObject("SELECT COUNT(*) FROM " + POSTGRES_SCHEMA + ".ext_sync_waiters",
				Long.class);
	}

	/**
	 * Освобождает lease, если ожидающий acquire успел его получить.
	 */
	private void releaseIfPresent(SlotLease lease) {
		// Метод используется в finally, поэтому null означает, что acquire не завершился или вернул Optional.empty().
		if (lease != null) {
			slotManager.release(lease.slotId(), lease.leaseId());
		}
	}

	/**
	 * Создает single-thread executor и сохраняет ссылку на поток для диагностики состояния ожидания.
	 */
	private static ExecutorService singleThreadExecutor(AtomicReference<Thread> threadRef, String threadName) {
		// Собственная фабрика потоков нужна, чтобы тест мог проверить WAITING/TIMED_WAITING у ожидающего acquire.
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, threadName);
			thread.setDaemon(true);
			threadRef.set(thread);
			return thread;
		});
	}

	/**
	 * Создает fixed-thread executor и сохраняет ссылки на потоки для проверки, что все waiters вошли в ожидание.
	 */
	private static ExecutorService fixedThreadExecutor(int threadCount, List<Thread> threadRefs, String threadNamePrefix) {
		AtomicInteger threadIndex = new AtomicInteger();
		return Executors.newFixedThreadPool(threadCount, runnable -> {
			// Именованный daemon thread упрощает диагностику зависаний и не удерживает JVM после аварийного теста.
			Thread thread = new Thread(runnable, threadNamePrefix + "-" + threadIndex.getAndIncrement());
			thread.setDaemon(true);
			threadRefs.add(thread);
			return thread;
		});
	}

	/**
	 * Проверяет, что поток ожидающего acquire находится в одном из блокирующих состояний JVM.
	 */
	private static boolean isBlockedWaitingThread(Thread thread) {
		// WAITING/TIMED_WAITING означает, что acquire не крутится активно, а ждет signal или fallback timeout.
		return EnumSet.of(Thread.State.WAITING, Thread.State.TIMED_WAITING).contains(thread.getState());
	}

	/**
	 * Забирает ровно ожидаемое число завершившихся acquire без ожидания полного timeout остальных waiters.
	 */
	private static List<SlotLease> takeCompletedLeases(
			ExecutorCompletionService<Optional<SlotLease>> completionService,
			int expectedCount,
			Duration timeout
	) throws Exception {
		List<SlotLease> leases = new ArrayList<>();
		// Единый deadline важен: каждое ожидание не должно получать полный timeout заново.
		long deadlineNanos = System.nanoTime() + timeout.toNanos();
		while (leases.size() < expectedCount) {
			// Poll с остатком времени позволяет тесту быстро упасть, если нужное число acquire не завершилось.
			long remainingNanos = deadlineNanos - System.nanoTime();
			assertThat(remainingNanos)
					.as("ожидаемые sync acquire должны завершиться до истечения timeout")
					.isPositive();
			Future<Optional<SlotLease>> future = completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
			assertThat(future)
					.as("ожидаемый sync acquire должен получить lease после NOTIFY")
					.isNotNull();
			Optional<SlotLease> lease = future.get(0, TimeUnit.MILLISECONDS);
			assertThat(lease)
					.as("завершившийся sync acquire должен вернуть lease")
					.isPresent();
			// Optional.empty здесь означал бы timeout/interrupt вместо реального получения слота.
			leases.add(lease.orElseThrow());
		}
		return leases;
	}

	/**
	 * Отменяет Future только если фоновая задача еще не завершилась.
	 */
	private static void cancelIfRunning(Future<?> future) {
		// Отмена с interrupt нужна для аварийного выхода из ожидания при падении assertions до завершения acquire.
		if (!future.isDone()) {
			future.cancel(true);
		}
	}

	private static void cancelIfRunning(List<? extends Future<?>> futures) {
		// Массовая отмена используется для contenders, которые корректно остались ждать свободных slots.
		futures.forEach(PostgresSlotListenNotifyIT::cancelIfRunning);
	}
}
