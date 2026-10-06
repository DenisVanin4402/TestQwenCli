package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ru.sberbank.pprb.agent.model.enums.SessionState;
import ru.sberbank.pprb.agent.service.port.in.SessionQueryUseCase;
import ru.sberbank.pprb.agent.service.port.out.WorkflowManager;

/** Проверяет основной ACL и зеркало через настоящий HTTP, H2 и общий сценарий. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:poc-http;DB_CLOSE_DELAY=-1"})
@ActiveProfiles({"poc-local", "h2"})
class PocHttpIntegrationTest {
    private static final AtomicInteger llmRequests = new AtomicInteger();
    private static HttpServer llmServer;

    /** Включённый клиент существует, но явные ACL-действия не должны обращаться к нему. */
    @DynamicPropertySource
    static void gigaChatSettings(DynamicPropertyRegistry properties) throws IOException {
        llmServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        llmServer.createContext(
                "/",
                exchange -> {
                    llmRequests.incrementAndGet();
                    exchange.sendResponseHeaders(500, -1);
                    exchange.close();
                });
        llmServer.start();
        String base = "http://127.0.0.1:" + llmServer.getAddress().getPort();
        properties.add("spring.ai.model.chat", () -> "gigachat");
        properties.add("GIGACHAT_API_KEY", () -> "synthetic-key");
        properties.add("GIGACHAT_BASE_URL", () -> base + "/v1");
        properties.add("GIGACHAT_AUTH_URL", () -> base + "/oauth");
    }

    /** Счётчик охватывает запуск приложения и все успешные/отклонённые ходы этого набора. */
    @AfterAll
    static void noLlmCallsForAcl() {
        if (llmServer != null) {
            llmServer.stop(0);
        }
        assertThat(llmRequests.get()).isZero();
    }

    @Autowired private ChatClient gigaChatClient;
    @LocalServerPort private int port;
    @Autowired private SessionQueryUseCase queries;
    @MockitoSpyBean private WorkflowManager workflow;

    /**
     * Подмена согласуемого номера не исполняет действие ни через основной ACL, ни через зеркало.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void rejectsChangedConfirmation(String prefix) throws Exception {
        var http = new PocHttpClient(port, prefix);
        UUID session = UUID.randomUUID();
        var proposal = http.body(post(http, session, "request", "status", true));
        var before = queries.find(session).orElseThrow();
        UUID request = UUID.randomUUID();
        var input = http.message(request, "accept_propose", null, false);
        var state = input.putArray("state");
        for (var field : proposal.path("state")) {
            if ("paymentNumber".equals(field.path("key").asText())) {
                var changed = (ObjectNode) field.deepCopy();
                changed.put("value", "43");
                state.add(changed);
            }
        }
        state.addObject()
                .put("key", "preparationNo")
                .put("value", Long.toString(before.getPreparation().getPreparationNo()))
                .put("type", "CONFIRMATION");
        assertThat(http.post(session, request, input).statusCode()).isEqualTo(400);
        assertThat(queries.find(session).orElseThrow())
                .usingRecursiveComparison()
                .isEqualTo(before);
        verify(workflow, never()).callOperation(any(), any());
    }

    /**
     * Каждый дефект согласия сохраняет снимок и прекращает действие; старый номер не принимается.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void rejectsMalformedAndStaleConfirmation(String prefix) throws Exception {
        var http = new PocHttpClient(port, prefix);
        UUID session = UUID.randomUUID();
        var proposal = http.body(post(http, session, "request", "status", true));
        var before = queries.find(session).orElseThrow();
        for (String defect :
                java.util.List.of(
                        "absent",
                        "number-null",
                        "number-empty",
                        "number-format",
                        "number-overflow",
                        "number-zero",
                        "number-duplicate",
                        "field-missing",
                        "field-empty",
                        "field-null",
                        "field-duplicate",
                        "extra",
                        "leading-zero")) {
            UUID request = UUID.randomUUID();
            var input = http.confirmation(request, proposal);
            var fields = (com.fasterxml.jackson.databind.node.ArrayNode) input.path("state");
            var payment = (ObjectNode) fields.get(0);
            var number = (ObjectNode) fields.get(1);
            switch (defect) {
                case "absent" -> input.remove("state");
                case "number-null" -> number.putNull("value");
                case "number-empty" -> number.put("value", "");
                case "number-format" -> number.put("value", "1.0");
                case "number-overflow" -> number.put("value", "9223372036854775808");
                case "number-zero" -> number.put("value", "0");
                case "number-duplicate" -> fields.add(number.deepCopy());
                case "field-missing" -> fields.remove(0);
                case "field-empty" -> payment.put("value", " ");
                case "field-null" -> payment.putNull("value");
                case "field-duplicate" -> fields.add(payment.deepCopy());
                case "leading-zero" -> payment.put("value", "0042");
                case "extra" ->
                        fields.addObject()
                                .put("key", "amount")
                                .put("value", "12500.00")
                                .put("type", "CONFIRMATION");
            }
            var response = http.post(session, request, input);
            assertThat(response.statusCode()).as(defect).isEqualTo(400);
            assertThat(http.body(response).at("/message/performative").asText())
                    .isEqualTo("failure");
            assertThat(queries.find(session).orElseThrow())
                    .usingRecursiveComparison()
                    .isEqualTo(before);
        }
        post(http, session, "reject_propose", null, false);
        var next = http.body(post(http, session, "request", "status", false));
        assertThat(next.at("/state/1/value").asText()).isEqualTo("2");
        UUID request = UUID.randomUUID();
        assertThat(http.post(session, request, http.confirmation(request, proposal)).statusCode())
                .isEqualTo(400);
        assertThat(queries.find(session).orElseThrow().getPreparation().getPreparationNo())
                .isEqualTo(2);
        verify(workflow, never()).callOperation(any(), any());
        request = UUID.randomUUID();
        assertThat(http.post(session, request, http.confirmation(request, next)).statusCode())
                .isEqualTo(200);
        verify(workflow).callOperation(any(), any());
    }

    /** Вне ACL остаётся только fixture; удалённый GET недоступен и для существующей сессии. */
    @Test
    void servesUiAndOnlyFixtureHelper() throws Exception {
        var http = new PocHttpClient(port);
        assertThat(http.get("/test-ui/").statusCode()).isEqualTo(200);
        assertThat(http.get("/test-ui/app.js").statusCode()).isEqualTo(200);
        var catalog = http.body(http.get("/local-api/v1/fixtures"));
        assertThat(catalog.path("integrationMode").asText()).isEqualTo("stub");
        assertThat(catalog.at("/suggestions/0/action_code").asText()).isEqualTo("status");
        UUID session = UUID.randomUUID();
        post(http, session, "request", "status", true);
        assertThat(http.get("/local-api/v1/sessions/" + session).statusCode()).isEqualTo(404);
    }

    /** Сводка требует отдельного согласия; отказ возвращает саджест, успех закрывает диалог. */
    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void exposesAclActionsAndConfirmation(String prefix) throws Exception {
        var http = new PocHttpClient(port, prefix);
        UUID session = UUID.randomUUID();
        var prepared = post(http, session, "request", "status", true);
        assertThat(prepared.statusCode()).isEqualTo(200);
        var proposal = http.body(prepared);
        assertThat(proposal.at("/message/performative").asText()).isEqualTo("propose");
        assertThat(proposal.at("/message/content/result").asText())
                .contains("42")
                .doesNotContain("ООО Альфа");
        assertThat(proposal.at("/metadata/final_message").asBoolean()).isFalse();
        assertThat(proposal.path("suggestions").isEmpty()).isTrue();
        assertThat(proposal.path("state")).hasSize(2);
        assertThat(proposal.path("state"))
                .allSatisfy(
                        field -> assertThat(field.path("type").asText()).isEqualTo("CONFIRMATION"));

        var before = queries.find(session).orElseThrow();
        assertThat(post(http, session, "request", "restart_status", false).statusCode())
                .isEqualTo(400);
        assertThat(post(http, session, "request", "status", false).statusCode()).isEqualTo(400);
        assertThat(queries.find(session).orElseThrow())
                .usingRecursiveComparison()
                .comparingOnlyFields("state", "context", "preparation")
                .isEqualTo(before);
        var cancelled = http.body(post(http, session, "reject_propose", null, false));
        assertThat(cancelled.at("/message/performative").asText()).isEqualTo("inform");
        assertThat(cancelled.at("/suggestions/0/action_code").asText()).isEqualTo("status");
        assertThat(cancelled.at("/suggestions/0/display_mode").asText()).isEqualTo("BUTTON");
        assertThat(cancelled.path("state").isEmpty()).isTrue();
        assertThat(post(http, session, "accept_propose", null, false).statusCode()).isEqualTo(400);
        var nextProposal = http.body(post(http, session, "request", "status", false));
        assertThat(queries.find(session).orElseThrow().getPreparation().getPreparationId())
                .isNotEqualTo(before.getPreparation().getPreparationId());
        var confirmationId = UUID.randomUUID();
        var confirmation = http.confirmation(confirmationId, nextProposal);
        ((ObjectNode) confirmation.path("message").path("content")).put("action_code", "ignored");
        var accepted = http.post(session, confirmationId, confirmation);
        assertThat(accepted.statusCode()).isEqualTo(200);
        var result = http.body(accepted);
        assertThat(result.at("/message/performative").asText()).isEqualTo("inform");
        assertThat(result.at("/metadata/final_message").asBoolean()).isTrue();
        assertThat(result.path("suggestions").isEmpty()).isTrue();
        assertThat(result.path("state").isEmpty()).isTrue();
        assertThat(queries.find(session).orElseThrow().getState())
                .isEqualTo(SessionState.COMPLETED);
        var terminal = http.body(post(http, session, "request", "status", false));
        assertThat(terminal.at("/metadata/final_message").asBoolean()).isTrue();
        assertThat(terminal.at("/message/performative").asText()).isEqualTo("failure");
    }

    /** Некорректный вход не создаёт сессию, а подмена контекста не меняет исходный платёж. */
    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void rejectsInvalidCorrelationAndContext(String prefix) throws Exception {
        var http = new PocHttpClient(port, prefix);
        UUID session = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        var invalid =
                http.post(
                        session,
                        request,
                        http.message(UUID.randomUUID(), "request", "status", true));
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(http.body(invalid).at("/message/in_reply_to").asText())
                .isEqualTo(request.toString());
        assertThat(queries.find(session)).isEmpty();
        assertThat(post(http, session, "request", "status", false).statusCode()).isEqualTo(400);
        assertThat(queries.find(session)).isEmpty();
        assertThat(post(http, session, "request", "status", true).statusCode()).isEqualTo(200);
        var before = queries.find(session).orElseThrow();
        request = UUID.randomUUID();
        ObjectNode changed = http.message(request, "accept_propose", null, false);
        changed.putObject("metadata").putObject("organization").put("epk_id", "another-org");
        assertThat(http.post(session, request, changed).statusCode()).isEqualTo(400);
        assertThat(queries.find(session).orElseThrow())
                .usingRecursiveComparison()
                .isEqualTo(before);
    }

    /** Создаёт новый идентификатор хода, как при отдельном клике в чате. */
    private java.net.http.HttpResponse<String> post(
            PocHttpClient http, UUID session, String performative, String action, boolean initial)
            throws Exception {
        UUID request = UUID.randomUUID();
        return http.post(session, request, http.message(request, performative, action, initial));
    }
}
