package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sberbank.pprb.agent.db.repository.SessionRepository;
import ru.sberbank.pprb.agent.model.dto.turn.MessageAnalysisDTO;
import ru.sberbank.pprb.agent.model.dto.turn.ReferenceAnswerDTO;
import ru.sberbank.pprb.agent.model.enums.MessageRoute;
import ru.sberbank.pprb.agent.service.port.in.SessionQueryUseCase;
import ru.sberbank.pprb.agent.service.port.out.*;

/** Настоящие HTTP/FSM/БД при детерминированном результате классификации. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:text-acl;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"poc-local", "h2"})
class TextAclIntegrationTest {
    @LocalServerPort int port;
    @MockitoBean TextMessageClassifier classifier;
    @MockitoBean WorkflowManager workflow;
    @MockitoBean ReferenceAnswerProvider reference;
    @Autowired SessionRepository sessions;
    @Autowired SessionQueryUseCase query;

    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void navigationResetReturnsMenuAndNextPreparationHasNewNumber(String prefix) throws Exception {
        var http = new PocHttpClient(port, prefix);
        var session = UUID.randomUUID();
        var id = UUID.randomUUID();
        var proposal =
                http.body(http.post(session, id, http.message(id, "request", "status", true)));
        id = UUID.randomUUID();
        var reset =
                http.body(
                        http.post(
                                session,
                                id,
                                http.message(id, "request", "reset_operation", false)));
        assertThat(reset.at("/message/performative").asText()).isEqualTo("inform");
        assertThat(reset.path("state")).isEmpty();
        assertThat(reset.at("/suggestions/0/action_code").asText()).isEqualTo("status");
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO(MessageRoute.REFERENCE, null));
        when(reference.answer(anyString()))
                .thenReturn(new ReferenceAnswerDTO(false, null, List.of()));
        id = UUID.randomUUID();
        assertThat(
                        http.body(http.post(session, id, question(http, id)))
                                .at("/suggestions/0/action_code")
                                .asText())
                .isEqualTo("status");
        id = UUID.randomUUID();
        var next = http.body(http.post(session, id, http.message(id, "request", "status", false)));
        assertThat(next.at("/state/1/value").asText()).isEqualTo("2");
        id = UUID.randomUUID();
        assertThat(http.post(session, id, http.confirmation(id, proposal)).statusCode())
                .isEqualTo(400);
        verifyNoInteractions(workflow);
        verify(classifier, times(1)).classify(anyString(), anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void questionsKeepPreparationAndConfirmationStillExecutesExactlyOnce(String prefix)
            throws Exception {
        var http = new PocHttpClient(port, prefix);
        var session = UUID.randomUUID();
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO(MessageRoute.REFERENCE, null));
        when(reference.answer(anyString()))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return new ReferenceAnswerDTO(
                                    true, "Комиссия не указана", List.of("KB-03"));
                        });
        var id = UUID.randomUUID();
        var first = http.body(http.post(session, id, question(http, id)));
        assertThat(first.at("/metadata/additional_info/0/value").asText()).isEqualTo("information");
        assertThat(first.at("/suggestions/0/action_code").asText()).isEqualTo("status");
        assertThat(sessions.existsById(session)).isFalse();
        id = UUID.randomUUID();
        var proposal =
                http.body(http.post(session, id, http.message(id, "request", "status", true)));
        var before = query.find(session).orElseThrow();
        for (int outcome = 0; outcome < 4; outcome++) {
            if (outcome == 1)
                when(reference.answer(anyString()))
                        .thenReturn(new ReferenceAnswerDTO(false, null, List.of()));
            if (outcome == 2)
                when(reference.answer(anyString()))
                        .thenThrow(new ReferenceAnswerException("synthetic"));
            if (outcome == 3)
                when(classifier.classify(anyString(), anyList()))
                        .thenReturn(new MessageAnalysisDTO(MessageRoute.UNCLEAR, null));
            id = UUID.randomUUID();
            var response = http.post(session, id, question(http, id));
            var answer = http.body(response);
            assertThat(response.statusCode()).isEqualTo(outcome == 2 ? 500 : 200);
            assertThat(answer.at("/metadata/additional_info/0/value").asText())
                    .isEqualTo("information");
            assertThat(answer.at("/message/in_reply_to").asText()).isEqualTo(id.toString());
            assertThat(answer.path("state")).isEmpty();
            if (outcome == 2) assertThat(answer.path("suggestions")).isEmpty();
            else {
                assertThat(answer.path("suggestions")).hasSize(2);
                assertThat(answer.at("/suggestions/0/action_code").asText())
                        .isEqualTo("resume_operation");
                assertThat(answer.at("/suggestions/1/action_code").asText())
                        .isEqualTo("reset_operation");
            }
            assertThat(query.find(session).orElseThrow())
                    .usingRecursiveComparison()
                    .isEqualTo(before);
        }
        verifyNoInteractions(workflow);
        id = UUID.randomUUID();
        var invalid = http.post(session, id, http.message(id, "request", "status", false));
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(http.body(invalid).path("suggestions")).hasSize(2);
        id = UUID.randomUUID();
        var resumed =
                http.body(
                        http.post(
                                session,
                                id,
                                http.message(id, "request", "resume_operation", false)));
        assertThat(resumed.path("state")).isEqualTo(proposal.path("state"));
        assertThat(resumed.at("/message/content/confirmation_view"))
                .isEqualTo(proposal.at("/message/content/confirmation_view"));
        assertThat(resumed.at("/message/content/result").asText())
                .isEqualTo(proposal.at("/message/content/result").asText());
        assertThat(resumed.at("/suggestions/0/performative").asText()).isEqualTo("accept_propose");
        verifyNoInteractions(workflow);
        id = UUID.randomUUID();
        assertThat(http.post(session, id, http.confirmation(id, resumed)).statusCode())
                .isEqualTo(200);
        verify(workflow).callOperation(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject_propose", "accept_propose"})
    void waitingReferenceDoesNotBlockOrOverwriteParallelOperation(String control) throws Exception {
        var http = new PocHttpClient(port);
        var session = UUID.randomUUID();
        var id = UUID.randomUUID();
        var proposal =
                http.body(http.post(session, id, http.message(id, "request", "status", true)));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO(MessageRoute.REFERENCE, null));
        when(reference.answer(anyString()))
                .thenAnswer(
                        invocation -> {
                            entered.countDown();
                            assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                            return new ReferenceAnswerDTO(
                                    true, "Тариф не указан", List.of("KB-03"));
                        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var questionId = UUID.randomUUID();
            var future =
                    executor.submit(
                            () -> http.post(session, questionId, question(http, questionId)));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var controlId = UUID.randomUUID();
                var body =
                        "accept_propose".equals(control)
                                ? http.confirmation(controlId, proposal)
                                : http.message(controlId, control, null, false);
                assertThat(http.post(session, controlId, body).statusCode()).isEqualTo(200);
                var saved = query.find(session).orElseThrow();
                release.countDown();
                var response = future.get(10, TimeUnit.SECONDS);
                assertThat(response.statusCode()).isEqualTo(200);
                var answer = http.body(response);
                assertThat(answer.at("/metadata/final_message").asBoolean())
                        .isEqualTo("accept_propose".equals(control));
                assertThat(answer.path("suggestions"))
                        .hasSize("accept_propose".equals(control) ? 0 : 1);
                if ("reject_propose".equals(control))
                    assertThat(answer.at("/suggestions/0/action_code").asText())
                            .isEqualTo("status");
                assertThat(query.find(session).orElseThrow())
                        .usingRecursiveComparison()
                        .isEqualTo(saved);
            } finally {
                release.countDown();
            }
        }
    }

    private ObjectNode question(PocHttpClient http, UUID id) {
        var body = http.message(id, "request", null, false);
        ((ObjectNode) body.at("/message/content")).put("user_input", "Какая комиссия?");
        return body;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void textPreparesAndOnlySpecialConfirmationExecutes(String prefix) throws Exception {
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(
                        new MessageAnalysisDTO(
                                ru.sberbank.pprb.agent.model.enums.MessageRoute.COMMAND, "status"));
        var http = new PocHttpClient(port, prefix);
        var session = UUID.randomUUID();
        var request = UUID.randomUUID();
        var body = http.message(request, "request", null, true);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("message").path("content"))
                .put("user_input", "Узнай статус");
        var reply = http.post(session, request, body);
        assertThat(reply.statusCode()).isEqualTo(200);
        var proposal = http.body(reply);
        assertThat(proposal.at("/message/performative").asText()).isEqualTo("propose");
        assertThat(proposal.path("state")).hasSize(2);
        verifyNoInteractions(workflow);
        var acceptId = UUID.randomUUID();
        var accepted = http.post(session, acceptId, http.confirmation(acceptId, proposal));
        assertThat(accepted.statusCode()).isEqualTo(200);
        verify(workflow).callOperation(any(), any());
        verify(classifier).classify(eq("Узнай статус"), anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void clarificationReturnsInformWithoutCreatingSession(String prefix) throws Exception {
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(
                        new MessageAnalysisDTO(
                                ru.sberbank.pprb.agent.model.enums.MessageRoute.UNCLEAR, null));
        var http = new PocHttpClient(port, prefix);
        var session = UUID.randomUUID();
        var id = UUID.randomUUID();
        var body = http.message(id, "request", null, true);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("message").path("content"))
                .put("user_input", "Подтверждаю");
        var reply = http.post(session, id, body);
        var result = http.body(reply);
        assertThat(reply.statusCode()).isEqualTo(200);
        assertThat(result.at("/message/performative").asText()).isEqualTo("inform");
        assertThat(result.at("/message/content/result").asText()).containsIgnoringCase("уточните");
        assertThat(result.at("/message/in_reply_to").asText()).isEqualTo(id.toString());
        assertThat(result.at("/suggestions/0/action_code").asText()).isEqualTo("status");
        assertThat(result.path("state")).isEmpty();
        assertThat(result.at("/metadata/final_message").asBoolean()).isFalse();
        assertThat(sessions.existsById(session)).isFalse();
        verifyNoInteractions(workflow);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void failureIsNotClarificationAndUnknownActionDoesNotFallBackToText(String prefix)
            throws Exception {
        when(classifier.classify(anyString(), anyList()))
                .thenThrow(new TextAnalysisException("synthetic"));
        var http = new PocHttpClient(port, prefix);
        var session = UUID.randomUUID();
        var id = UUID.randomUUID();
        var body = http.message(id, "request", "unknown", true);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("message").path("content"))
                .put("user_input", "статус");
        assertThat(http.post(session, id, body).statusCode()).isEqualTo(200);
        verifyNoInteractions(classifier);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("message").path("content"))
                .remove("action_code");
        var reply = http.post(session, id, body);
        assertThat(reply.statusCode()).isEqualTo(500);
        assertThat(http.body(reply).at("/message/performative").asText()).isEqualTo("failure");
        assertThat(sessions.existsById(session)).isFalse();
    }
}
