package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sberbank.pprb.agent.db.repository.SessionRepository;
import ru.sberbank.pprb.agent.model.dto.turn.MessageAnalysisDTO;
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
    @Autowired SessionRepository sessions;

    @ParameterizedTest
    @ValueSource(strings = {"", "/local-api"})
    void textPreparesAndOnlySpecialConfirmationExecutes(String prefix) throws Exception {
        when(classifier.classify(anyString(), anyList()))
                .thenReturn(new MessageAnalysisDTO("status"));
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
        when(classifier.classify(anyString(), anyList())).thenReturn(new MessageAnalysisDTO(null));
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
        assertThat(result.at("/message/content/result").asText()).contains("уточните");
        assertThat(result.at("/message/in_reply_to").asText()).isEqualTo(id.toString());
        assertThat(result.path("suggestions")).isEmpty();
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
        assertThat(http.post(session, id, body).statusCode()).isEqualTo(400);
        verifyNoInteractions(classifier);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("message").path("content"))
                .remove("action_code");
        var reply = http.post(session, id, body);
        assertThat(reply.statusCode()).isEqualTo(500);
        assertThat(http.body(reply).at("/message/performative").asText()).isEqualTo("failure");
        assertThat(sessions.existsById(session)).isFalse();
    }
}
