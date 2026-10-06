package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sberbank.pprb.agent.db.repository.SessionRepository;
import ru.sberbank.pprb.agent.model.enums.SessionEvent;
import ru.sberbank.pprb.agent.model.enums.SessionState;
import ru.sberbank.pprb.agent.service.turn.SessionResponseRenderer;

/**
 * Проверяет сквозной путь первого этапа через ACL HTTP, Spring, PostgreSQL, машину состояний и
 * сгенерированный Feign-клиент. Вместо внешнего corr работает управляемый HTTP-сервер; реальные
 * компоненты приложения позволяют проверить ответы API, состояние БД и число отправок. Отдельные
 * сценарии точечно подменяют сохранение или формирование сводки, чтобы воспроизвести сбой.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "integrations.invest-corr.stub=disabled",
            "spring.cloud.openfeign.client.config.invest-corr.readTimeout=15000"
        })
@ActiveProfiles({"poc-local", "postgres"})
class PocEndToEndIT {
    private final java.util.Map<UUID, JsonNode> proposals =
            new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    ru.sberbank.pprb.agent.service.port.in.SessionQueryUseCase queries;

    /**
     * Сервер принимает настоящие HTTP-запросы Feign и позволяет воспроизвести сбои внешней границы.
     */
    private static final CorrServer corr = new CorrServer();

    @LocalServerPort int port;
    @Autowired SessionRepository sessions;
    @Autowired JpaStateMachineRepository nativeStates;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean DefaultStateMachinePersister<SessionState, SessionEvent, Object> persister;
    @MockitoSpyBean SessionResponseRenderer renderer;
    private PocHttpClient http;

    /**
     * Настройки указывают на отдельную PostgreSQL; без неё полный E2E-набор должен завершиться
     * ошибкой.
     */
    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("POC_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> required("POC_TEST_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> required("POC_TEST_POSTGRES_PASSWORD"));
        registry.add("integrations.invest-corr.url", corr::address);
    }

    /**
     * Сбрасывает управление corr и создаёт HTTP-клиент; сами сценарии используют новые UUID сессий.
     */
    @BeforeEach
    void setup() {
        corr.reset();
        http = new PocHttpClient(port);
    }

    /** Освобождает только HTTP-сервер и потоки, созданные этим тестовым набором. */
    @AfterAll
    static void closeServer() {
        corr.close();
    }

    /**
     * Повторный выбор статуса на подтверждении отклоняется без изменения предложения и вызова corr.
     * Отдельное согласие отправляет данные показанного черновика с его UUID и возвращает финальный
     * ответ ACL с правильной адресацией. После завершения новые команды не вызывают повторную
     * операцию.
     */
    @Test
    void preparationConfirmationAndTerminalContract() throws Exception {
        UUID session = prepare();
        JsonNode first = snapshot(session);
        String operationId = first.at("/preparation/preparationId").asText();
        assertThat(proposals.get(session).at("/message/content/result").asText())
                .isEqualTo("Запросить статус платежа №42?");
        var rejected = post(session, "request", "status", false);
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(http.body(rejected).at("/message/performative").asText()).isEqualTo("failure");
        JsonNode awaiting = snapshot(session);
        assertThat(awaiting.at("/state").asText()).isEqualTo("AWAITING_CONFIRM");
        assertThat(awaiting.at("/preparation")).isEqualTo(first.at("/preparation"));
        assertThat(corr.calls).hasValue(0);

        UUID request = UUID.randomUUID();
        ObjectNode input = http.confirmation(request, proposals.get(session));
        ((ObjectNode) input.path("message").path("content"))
                .put("action_code", "unsupported_action");
        var response = http.post(session, request, input);
        JsonNode output = http.body(response);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(output.at("/message/performative").asText()).isEqualTo("inform");
        assertThat(output.at("/message/content/status_code").asText()).isEqualTo("200");
        assertThat(output.at("/message/sender").asText()).isEqualTo("invest-pro");
        assertThat(output.at("/message/receiver").asText()).isEqualTo("LOCAL_TEST");
        assertThat(output.at("/message/in_reply_to").asText()).isEqualTo(request.toString());
        assertThat(output.at("/message/conversation_id").asText())
                .isEqualTo(input.at("/message/conversation_id").asText());
        assertThat(output.at("/metadata/final_message").asBoolean()).isTrue();
        assertThat(corr.calls).hasValue(1);
        assertThat(corr.key.get()).isEqualTo(operationId);
        assertThat(corr.payload.get().get("clientRequestId").asText()).isEqualTo(operationId);
        assertThat(corr.payload.get().get("operation").asText()).isEqualTo("status");
        assertThat(corr.payload.get().get("epkId").asText()).isEqualTo("test-org-1");
        assertThat(corr.payload.get().get("digitalUserId").asText()).isEqualTo("test-user-1");
        assertThat(corr.payload.get().get("paymentId").asText()).isEqualTo("test-payment-42");
        assertThat(snapshot(session).at("/state").asText()).isEqualTo("COMPLETED");
        for (String[] command :
                List.of(
                        new String[] {"request", "status"},
                        new String[] {"accept_propose", null},
                        new String[] {"reject_propose", null})) {
            var denied = post(session, command[0], command[1], false);
            assertThat(denied.statusCode()).isEqualTo(400);
            assertThat(http.body(denied).at("/metadata/final_message").asBoolean()).isTrue();
        }
        assertThat(corr.calls).hasValue(1);
    }

    /**
     * Ошибка нового контекста не теряет завершённость, уже установленную при восстановлении FSM.
     */
    @Test
    void completedSessionRetainsTerminalFlagOnContextMismatch() throws Exception {
        UUID session = prepare();
        assertThat(confirm(session, proposals.get(session)).statusCode()).isEqualTo(200);
        UUID request = UUID.randomUUID();
        ObjectNode input = http.message(request, "accept_propose", null, false);
        input.putObject("metadata").putObject("organization").put("epk_id", "another-organization");

        var response = http.post(session, request, input);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(http.body(response).at("/message/performative").asText()).isEqualTo("failure");
        assertThat(http.body(response).at("/metadata/final_message").asBoolean()).isTrue();
        assertThat(corr.calls).hasValue(1);
        assertThat(snapshot(session).at("/state").asText()).isEqualTo("COMPLETED");
    }

    /**
     * Удалённая команда перезапуска отклоняется без изменения подготовки и вызова corr. Отказ
     * удаляет подготовку; следующий выбор создаёт новое предложение, которое можно подтвердить
     * только отдельным сообщением.
     */
    @Test
    void rejectsRestartAndAllowsNewPreparationAfterCancellation() throws Exception {
        UUID session = prepare();
        JsonNode before = snapshot(session);
        String firstId = before.at("/preparation/preparationId").asText();
        var rejected = post(session, "request", "restart_status", false);
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(http.body(rejected).at("/message/performative").asText()).isEqualTo("failure");
        assertThat(snapshot(session)).isEqualTo(before);
        assertThat(corr.calls).hasValue(0);
        assertThat(post(session, "reject_propose", null, false).statusCode()).isEqualTo(200);
        assertThat(snapshot(session).at("/state").asText()).isEqualTo("CHOOSING_REQUEST_TYPE");
        assertThat(confirm(session, proposals.get(session)).statusCode()).isEqualTo(400);
        assertThat(post(session, "reject_propose", null, false).statusCode()).isEqualTo(200);
        assertThat(post(session, "request", "status", false).statusCode()).isEqualTo(200);
        JsonNode selected = snapshot(session);
        assertThat(selected.at("/preparation/preparationId").asText()).isNotEqualTo(firstId);
        assertThat(selected.at("/preparation/preparationNo").asInt()).isEqualTo(2);
        assertThat(selected.at("/preparation/confirmedRequestId").isTextual()).isFalse();
        assertThat(corr.calls).hasValue(0);
        assertThat(confirm(session, proposals.get(session)).statusCode()).isEqualTo(200);
        assertThat(corr.calls).hasValue(1);
    }

    /**
     * Ошибочный первый запрос возвращает адресованный отказ и не создаёт машину. Проверяются
     * отсутствие контекста, преждевременное согласие, удалённая команда перезапуска, несовпадение
     * UUID, неверные версия и адресат, неподдерживаемая команда, повтор реквизита и явный null.
     * Следующий правильный запрос может использовать тот же UUID сессии.
     */
    @ParameterizedTest(name = "Невалидный первый вход: {0}")
    @ValueSource(
            strings = {
                "missing-context",
                "confirm-first",
                "removed-restart",
                "reply-mismatch",
                "wrong-version",
                "wrong-receiver",
                "unsupported",
                "duplicate-payment",
                "null-metadata"
            })
    void invalidInitialInputDoesNotCreateMachine(String variant) throws Exception {
        UUID session = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        ObjectNode input = http.message(request, "request", "status", true);
        ObjectNode message = (ObjectNode) input.get("message");
        switch (variant) {
            case "missing-context" -> input.remove("metadata");
            case "confirm-first" -> message.put("performative", "accept_propose");
            case "removed-restart" ->
                    ((ObjectNode) message.get("content")).put("action_code", "restart_status");
            case "reply-mismatch" -> message.put("reply_with", UUID.randomUUID().toString());
            case "wrong-version" -> message.put("version", "9");
            case "wrong-receiver" -> message.put("receiver", "another-agent");
            case "unsupported" ->
                    ((ObjectNode) message.get("content")).put("action_code", "recall");
            case "duplicate-payment" ->
                    ((com.fasterxml.jackson.databind.node.ArrayNode)
                                    input.at("/metadata/additional_info"))
                            .addObject()
                            .put("key", "paymentId")
                            .put("value", "other");
            case "null-metadata" -> input.putNull("metadata");
            default -> throw new AssertionError(variant);
        }
        var response = http.post(session, request, input);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(http.body(response).at("/message/performative").asText()).isEqualTo("failure");
        assertThat(http.body(response).at("/message/in_reply_to").asText())
                .isEqualTo(request.toString());
        assertThat(queries.find(session)).isEmpty();
        assertThat(post(session, "request", "status", true).statusCode()).isEqualTo(200);
        assertThat(corr.calls).hasValue(0);
    }

    /**
     * Подтверждение с другой организацией или другим платежом отклоняется. Сохранённый черновик
     * остаётся прежним, а внешний запрос не отправляется ни в одном из вариантов подмены.
     */
    @ParameterizedTest(name = "Подмена контекста: {0}")
    @ValueSource(strings = {"organization", "payment"})
    void rejectsChangedContextWithoutLosingPreparation(String field) throws Exception {
        UUID session = prepare();
        String original = snapshot(session).at("/preparation/preparationId").asText();
        UUID request = UUID.randomUUID();
        ObjectNode input = http.message(request, "accept_propose", null, true);
        if ("organization".equals(field)) {
            ((ObjectNode) input.at("/metadata/organization")).put("epk_id", "other");
        } else {
            ((ObjectNode) input.at("/metadata/additional_info/0")).put("value", "other");
        }
        assertThat(http.post(session, request, input).statusCode()).isEqualTo(400);
        assertThat(snapshot(session).at("/preparation/preparationId").asText()).isEqualTo(original);
        assertThat(corr.calls).hasValue(0);
    }

    /**
     * Отказ corr, HTTP-ошибка, повреждённый или неполный ответ и потеря ответа возвращают
     * нефинальный ACL failure. Согласие откатывается, запрос не повторяется; при потере ответа
     * сервер уже учитывает внешний эффект, который локальный откат не отменяет.
     */
    @ParameterizedTest(name = "Ошибка corr: {0}")
    @ValueSource(
            strings = {"rejected", "http-error", "invalid-json", "incomplete", "lost-response"})
    void externalFailuresRollbackAndNeverRetry(String failure) throws Exception {
        UUID session = prepare();
        corr.behavior = failure;
        var response = confirm(session, proposals.get(session));
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(http.body(response).at("/message/performative").asText()).isEqualTo("failure");
        assertThat(http.body(response).at("/metadata/final_message").asBoolean()).isFalse();
        JsonNode saved = snapshot(session);
        assertThat(saved.at("/state").asText()).isEqualTo("AWAITING_CONFIRM");
        assertThat(saved.at("/preparation/confirmedRequestId").isTextual()).isFalse();
        assertThat(corr.calls).hasValue(1);
        assertThat(corr.effects.get()).isEqualTo("lost-response".equals(failure) ? 1 : 0);
    }

    /**
     * Сбой сохранения машины после успешного corr возвращает HTTP 500 и оставляет прежний шаг
     * ожидания согласия. Внешний эффект при этом остаётся выполненным ровно один раз.
     */
    @Test
    void persistenceFailureAfterCorrPreservesLastCommit() throws Exception {
        UUID session = prepare();
        doThrow(new IllegalStateException("injected persistence failure"))
                .doCallRealMethod()
                .when(persister)
                .persist(any(), any());
        assertThat(confirm(session, proposals.get(session)).statusCode()).isEqualTo(500);
        assertThat(snapshot(session).at("/state").asText()).isEqualTo("AWAITING_CONFIRM");
        assertThat(corr.calls).hasValue(1);
        assertThat(corr.effects).hasValue(1);
    }

    /**
     * PostgreSQL проверяет отложенный внешний ключ только при commit: Action и настоящее сохранение
     * машины успевают завершиться без ошибки. Отказ фиксации возвращает failure, откатывает все
     * локальные изменения и не повторяет уже выполненный внешний вызов.
     */
    @Test
    void commitFailureAfterCorrPreservesLastCommit() throws Exception {
        UUID session = prepare();
        JsonNode before = snapshot(session);
        AtomicInteger completedSaves = new AtomicInteger();
        jdbc.execute(
                """
                CREATE TABLE poc_commit_failure_probe (
                    session_id uuid NOT NULL REFERENCES agent_session(session_id)
                        DEFERRABLE INITIALLY DEFERRED
                )
                """);
        try {
            doAnswer(
                            invocation -> {
                                Object result = invocation.callRealMethod();
                                // Несуществующая сессия допустима до завершения транзакции. JDBC
                                // использует то же соединение, что и штатное JPA-сохранение машины.
                                jdbc.update(
                                        "INSERT INTO poc_commit_failure_probe (session_id) VALUES (?)",
                                        UUID.randomUUID());
                                completedSaves.incrementAndGet();
                                return result;
                            })
                    .when(persister)
                    .persist(any(), any());

            var response = confirm(session, proposals.get(session));
            assertThat(completedSaves).hasValue(1);
            assertThat(response.statusCode()).isEqualTo(500);
            JsonNode body = http.body(response);
            assertThat(body.at("/message/performative").asText()).isEqualTo("failure");
            assertThat(body.at("/message/content/status_code").asText()).isEqualTo("500");
            assertThat(body.at("/metadata/final_message").asBoolean()).isFalse();
            assertThat(snapshot(session)).isEqualTo(before);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM poc_commit_failure_probe", Long.class))
                    .isZero();
            assertThat(corr.calls).hasValue(1);
            assertThat(corr.effects).hasValue(1);
        } finally {
            jdbc.execute("DROP TABLE poc_commit_failure_probe");
        }
    }

    /**
     * Сбой формирования ответа после фиксации транзакции возвращает HTTP 500, но сессия остаётся
     * завершённой. Следующее согласие отклоняется и не повторяет уже выполненную внешнюю операцию.
     */
    @Test
    void responseFailureDoesNotUndoCommitOrRepeatOperation() throws Exception {
        UUID session = prepare();
        doThrow(new IllegalStateException("injected template failure"))
                .when(renderer)
                .render(any());
        assertThat(confirm(session, proposals.get(session)).statusCode()).isEqualTo(500);
        doCallRealMethod().when(renderer).render(any());
        assertThat(snapshot(session).at("/state").asText()).isEqualTo("COMPLETED");
        assertThat(confirm(session, proposals.get(session)).statusCode()).isEqualTo(400);
        assertThat(corr.calls).hasValue(1);
        assertThat(corr.effects).hasValue(1);
    }

    /**
     * Неизвестный формат или повреждённые сериализованные данные приводят к ошибке чтения и
     * обработки команды. Существующая машина не заменяется новой, и запрос corr не отправляется.
     */
    @ParameterizedTest(name = "Повреждение хранения: {0}")
    @ValueSource(strings = {"format", "binary"})
    void invalidStoredMachineIsNotReinitialized(String fault) throws Exception {
        UUID session = prepare();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            if ("format".equals(fault)) {
                                var row = sessions.findById(session).orElseThrow();
                                row.setFormatId("unsupported");
                                sessions.saveAndFlush(row);
                            } else {
                                var row = nativeStates.findById(session.toString()).orElseThrow();
                                row.setStateMachineContext(new byte[] {1, 2, 3});
                                nativeStates.save(row);
                            }
                        });
        assertThatThrownBy(() -> queries.find(session))
                .isInstanceOf(ru.sberbank.pprb.agent.service.port.in.SessionQueryException.class);
        assertThat(post(session, "request", "status", true).statusCode()).isEqualTo(500);
        assertThat(corr.calls).hasValue(0);
    }

    /**
     * Пока corr удерживает подтверждение первой сессии, её конкурентные POST и внутреннее чтение
     * получают ошибку занятости. Вторая сессия успешно завершается независимо; после освобождения
     * внешнего вызова первая тоже завершается, и всего corr получает два запроса.
     */
    @Test
    void concurrentRequestsRespectSessionSemaphore() throws Exception {
        UUID first = prepare();
        UUID second = prepare();
        corr.blockedOperation = snapshot(first).at("/preparation/preparationId").asText();
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<java.net.http.HttpResponse<String>> running =
                    workers.submit(() -> confirm(first, proposals.get(first)));
            try {
                assertThat(corr.entered.await(5, TimeUnit.SECONDS)).isTrue();
                var busy = confirm(first, proposals.get(first));
                assertThat(busy.statusCode()).isEqualTo(400);
                assertThat(http.body(busy).at("/message/content/reason").asText())
                        .contains("другое сообщение");
                assertThatThrownBy(() -> queries.find(first))
                        .isInstanceOf(
                                ru.sberbank.pprb.agent.service.port.in.SessionQueryException.class);
                assertThat(confirm(second, proposals.get(second)).statusCode()).isEqualTo(200);
            } finally {
                corr.release.countDown();
            }
            assertThat(running.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(snapshot(first).at("/state").asText()).isEqualTo("COMPLETED");
        assertThat(corr.calls).hasValue(2);
    }

    /**
     * Создаёт сессию и черновик через ACL API с полным тестовым контекстом. Проверяет успешный
     * ответ с final_message=false, поскольку отдельное согласие ещё не получено.
     */
    private UUID prepare() throws Exception {
        UUID session = UUID.randomUUID();
        var response = post(session, "request", "status", true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(http.body(response).at("/metadata/final_message").asBoolean()).isFalse();
        return session;
    }

    /** Отсутствующие и повреждённые UUID заголовков отклоняются до регистрации бизнес-сессии. */
    @ParameterizedTest(name = "Некорректные заголовки: {0}")
    @ValueSource(
            strings = {"missing-request", "missing-session", "invalid-request", "invalid-session"})
    void rejectsInvalidCorrelationHeaders(String variant) throws Exception {
        UUID session = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        String sessionHeader =
                variant.equals("missing-session")
                        ? null
                        : variant.equals("invalid-session") ? "not-a-uuid" : session.toString();
        String requestHeader =
                variant.equals("missing-request")
                        ? null
                        : variant.equals("invalid-request") ? "not-a-uuid" : request.toString();
        var response =
                http.postHeaders(
                        sessionHeader,
                        requestHeader,
                        http.message(request, "request", "status", true));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(sessions.existsById(session)).isFalse();
        assertThat(corr.calls).hasValue(0);
    }

    /**
     * Отправляет сообщение на настоящий HTTP-порт приложения с новым Request-Id. Параметр initial
     * определяет, включаются ли полные исходные данные платежа в сообщение.
     */
    private java.net.http.HttpResponse<String> post(
            UUID session, String performative, String action, boolean initial) throws Exception {
        UUID request = UUID.randomUUID();
        var response =
                http.post(session, request, http.message(request, performative, action, initial));
        var body = http.body(response);
        if ("propose".equals(body.at("/message/performative").asText()))
            proposals.put(session, body);
        return response;
    }

    /** Возвращает явно выбранное предложение, не читая серверную сессию. */
    private java.net.http.HttpResponse<String> confirm(UUID session, JsonNode proposal)
            throws Exception {
        UUID request = UUID.randomUUID();
        return http.post(session, request, http.confirmation(request, proposal));
    }

    /** Проверяет сохранённое представление через внутренний сервис без дополнительного HTTP API. */
    private JsonNode snapshot(UUID session) throws Exception {
        return new ObjectMapper()
                .findAndRegisterModules()
                .valueToTree(queries.find(session).orElseThrow());
    }

    /**
     * Возвращает обязательную настройку тестовой PostgreSQL; её отсутствие завершает набор ошибкой.
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Требуется " + name);
        return value;
    }

    /**
     * Локальный сервер временного corr-контракта для проверки настоящих запросов Feign,
     * транспортных ошибок и тайм-аута. Раздельно учитывает полученные запросы и выполненные внешние
     * эффекты, позволяя воспроизвести выполнение операции до потери ответа.
     */
    private static class CorrServer {
        /** Число HTTP-запросов, полученных corr в текущем сценарии, включая ошибочные обращения. */
        final AtomicInteger calls = new AtomicInteger();

        /** Число выполненных сервером операций; потеря ответа не уменьшает этот счётчик. */
        final AtomicInteger effects = new AtomicInteger();

        /**
         * Тело последнего запроса для проверки платежа и идентификатора подтверждённой операции.
         */
        final AtomicReference<JsonNode> payload = new AtomicReference<>();

        /**
         * Последний заголовок Idempotency-Key для сверки с UUID показанного пользователю черновика.
         */
        final AtomicReference<String> key = new AtomicReference<>();

        /** Потоки обработки HTTP-запросов, позволяющие проверять независимость разных сессий. */
        final ExecutorService workers = Executors.newCachedThreadPool();

        /**
         * Локальный HTTP-сервер на свободном порту, принадлежащий только этому тестовому набору.
         */
        final HttpServer server;

        /** Выбранный тестом режим: успешный ответ, отказ или конкретный сбой внешнего сервиса. */
        volatile String behavior;

        /**
         * UUID операции, ответ на которую нужно задержать; null означает отсутствие блокируемой
         * операции.
         */
        volatile String blockedOperation;

        /**
         * Сигнал тесту, что запрос блокируемой операции уже принят и её сессия удерживает
         * транзакцию.
         */
        volatile CountDownLatch entered;

        /** Разрешение продолжить обработку задержанного запроса после конкурентных проверок. */
        volatile CountDownLatch release;

        /**
         * Запускает HTTP-обработчик временного corr-контракта. Он записывает полученный запрос, при
         * необходимости задерживает выбранную операцию и воспроизводит заданный вид ответа.
         */
        CorrServer() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.setExecutor(workers);
                server.createContext(
                        "/prototype/v1/status-requests",
                        exchange -> {
                            calls.incrementAndGet();
                            JsonNode input = new ObjectMapper().readTree(exchange.getRequestBody());
                            payload.set(input);
                            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                            if (input.get("clientRequestId").asText().equals(blockedOperation)) {
                                entered.countDown();
                                try {
                                    if (!release.await(10, TimeUnit.SECONDS)) {
                                        throw new IOException(
                                                "Тест не освободил удерживаемый corr за 10 секунд");
                                    }
                                } catch (InterruptedException error) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            String mode = behavior;
                            if ("accepted".equals(mode) || "lost-response".equals(mode))
                                effects.incrementAndGet();
                            if ("lost-response".equals(mode)) {
                                // Потеря ответа проверяется разрывом соединения; тайм-аут выделен в
                                // PocCorrTimeoutIT.
                                exchange.close();
                                return;
                            }
                            String body =
                                    switch (mode) {
                                        case "rejected" ->
                                                "{\"outcome\":\"REJECTED\",\"reasonCode\":\"DECLINED\",\"reasonText\":\"rejected\"}";
                                        case "invalid-json" -> "invalid";
                                        case "incomplete" -> "{\"outcome\":\"ACCEPTED\"}";
                                        default ->
                                                "{\"outcome\":\"ACCEPTED\",\"reference\":\"ref\"}";
                                    };
                            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().add("Content-Type", "application/json");
                            exchange.sendResponseHeaders(
                                    "http-error".equals(mode) ? 503 : 200, bytes.length);
                            exchange.getResponseBody().write(bytes);
                            exchange.close();
                        });
                server.start();
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
        }

        /**
         * Сбрасывает управление между завершёнными тестами; сессии приложения имеют уникальные
         * UUID.
         */
        void reset() {
            calls.set(0);
            effects.set(0);
            payload.set(null);
            key.set(null);
            behavior = "accepted";
            blockedOperation = null;
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        /**
         * Возвращает локальный адрес с выделенным ОС портом для настройки настоящего Feign-клиента.
         */
        String address() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /** Завершает сервер и его собственные потоки после E2E-набора. */
        void close() {
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
