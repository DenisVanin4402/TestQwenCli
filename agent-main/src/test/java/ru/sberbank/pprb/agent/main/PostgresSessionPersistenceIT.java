package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.sberbank.pprb.agent.model.enums.ResultCode;
import ru.sberbank.pprb.agent.model.enums.SessionState;
import ru.sberbank.pprb.agent.service.fsm.SessionPersistenceException;

/**
 * Выполняет базовые проверки сохранения сериализованной машины и транзакций на PostgreSQL.
 * Дополнительно проверяет реальные блокировки NOWAIT, независимость разных сессий и одновременное
 * создание постоянной строки блокировки несколькими запросами.
 */
@ActiveProfiles(
        value = {"poc-local", "postgres"},
        inheritProfiles = false)
class PostgresSessionPersistenceIT extends SessionPersistenceTest {
    /** Отдельная тестовая БД обязательна; отсутствие настройки не превращается в пропуск тестов. */
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("POC_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> required("POC_TEST_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> required("POC_TEST_POSTGRES_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /**
     * Четыре одновременных запроса регистрации одной сессии успешно оставляют единственную
     * постоянную строку. Машина при регистрации не создаётся, поэтому чтение возвращает пустой
     * результат.
     */
    @Test
    void concurrentRegistrationCreatesOnePermanentSemaphore() throws Exception {
        UUID id = UUID.randomUUID();
        CyclicBarrier start = new CyclicBarrier(4);
        try (ExecutorService workers = Executors.newFixedThreadPool(4)) {
            java.util.List<Future<?>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(
                        workers.submit(
                                () -> {
                                    start.await(10, TimeUnit.SECONDS);
                                    engine.ensureSemaphore(id);
                                    return null;
                                }));
            }
            for (Future<?> result : results) {
                result.get(15, TimeUnit.SECONDS);
            }
        }
        assertThat(sessions.findAllById(java.util.List.of(id))).hasSize(1);
        assertThat(engine.find(id)).isEmpty();
    }

    /**
     * Пока действие первой сессии удерживает блокировку, конкурентные команда и чтение получают
     * SESSION_BUSY без ожидания завершения действия. Другая сессия успешно завершается независимо,
     * а после фиксации первой транзакции её сохранённое состояние становится доступно для чтения.
     */
    @Test
    void nowaitRejectsConcurrentTurnAndReadButAllowsAnotherSession() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var firstProposal = prepare(first);
        UUID firstOperation = firstProposal.getPreparation().getPreparationId();
        var secondProposal = prepare(second);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(
                        call -> {
                            ru.sberbank.pprb.agent.model.dto.operation.OperationContext operation =
                                    call.getArgument(1);
                            if (firstOperation.equals(operation.getOperationId())) {
                                entered.countDown();
                                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                            }
                            return null;
                        })
                .when(workflow)
                .callOperation(any(), any());
        try (ExecutorService workers = Executors.newFixedThreadPool(3)) {
            Future<?> running = workers.submit(() -> confirm(first, firstProposal));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                workers.submit(
                                () ->
                                        assertThatThrownBy(() -> confirm(first, firstProposal))
                                                .isInstanceOfSatisfying(
                                                        SessionPersistenceException.class,
                                                        error ->
                                                                assertThat(error.getCode())
                                                                        .isEqualTo(
                                                                                ResultCode
                                                                                        .SESSION_BUSY)))
                        .get(3, TimeUnit.SECONDS);
                workers.submit(
                                () ->
                                        assertThatThrownBy(() -> engine.find(first))
                                                .isInstanceOfSatisfying(
                                                        SessionPersistenceException.class,
                                                        error ->
                                                                assertThat(error.getCode())
                                                                        .isEqualTo(
                                                                                ResultCode
                                                                                        .SESSION_BUSY)))
                        .get(3, TimeUnit.SECONDS);
                assertThat(
                                workers.submit(() -> confirm(second, secondProposal))
                                        .get(3, TimeUnit.SECONDS)
                                        .getState())
                        .isEqualTo(SessionState.COMPLETED);
                workers.submit(
                                () ->
                                        assertThatThrownBy(
                                                        () ->
                                                                engine.cancelIfPresent(
                                                                        ru.sberbank.pprb.agent.model
                                                                                .dto.turn
                                                                                .SessionTurnInDTO
                                                                                .builder()
                                                                                .sessionId(first)
                                                                                .requestId(
                                                                                        UUID
                                                                                                .randomUUID())
                                                                                .build()))
                                                .isInstanceOfSatisfying(
                                                        SessionPersistenceException.class,
                                                        error ->
                                                                assertThat(error.getCode())
                                                                        .isEqualTo(
                                                                                ResultCode
                                                                                        .SESSION_BUSY)))
                        .get(3, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            running.get(5, TimeUnit.SECONDS);
        }
        assertThat(engine.find(first).orElseThrow().getState()).isEqualTo(SessionState.COMPLETED);
        verify(workflow, times(2)).callOperation(any(), any());
    }

    /**
     * Возвращает обязательную настройку тестовой PostgreSQL; отсутствие значения завершает тест
     * ошибкой.
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Для PostgreSQL-проверки требуется " + name);
        }
        return value;
    }
}
