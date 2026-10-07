package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import chat.giga.springai.GigaChatModel;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import ru.sberbank.pprb.agent.model.dto.turn.*;
import ru.sberbank.pprb.agent.service.port.out.*;

/** Реальный ACL/GigaChat-прогон; предметную оценку текстов выполняет исполнитель по отчёту. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.ai.model.chat=gigachat",
            "spring.datasource.url=jdbc:h2:mem:reference-eval;DB_CLOSE_DELAY=-1"
        })
@ActiveProfiles({"poc-local", "h2"})
class GigaChatReferenceEvaluation {
    @LocalServerPort int port;
    @MockitoBean WorkflowManager workflow;
    @MockitoSpyBean TextMessageClassifier classifier;
    @MockitoSpyBean ReferenceAnswerProvider reference;
    @MockitoSpyBean GigaChatModel chatModel;

    @Value("classpath:gigachat/reference.json")
    Resource dataset;

    @Test
    void evaluatesAllSamplesWithoutConfirmingOperations() throws Exception {
        var json = new ObjectMapper();
        JsonNode samples;
        try (var stream = dataset.getInputStream()) {
            samples = json.readTree(stream);
        }
        assertThat(samples.size()).isBetween(1, 10);
        var analysis = new AtomicReference<MessageAnalysisDTO>();
        var answer = new AtomicReference<ReferenceAnswerDTO>();
        doAnswer(
                        call -> {
                            var result = (MessageAnalysisDTO) call.callRealMethod();
                            analysis.set(result);
                            return result;
                        })
                .when(classifier)
                .classify(anyString(), anyList());
        doAnswer(
                        call -> {
                            var result = (ReferenceAnswerDTO) call.callRealMethod();
                            answer.set(result);
                            return result;
                        })
                .when(reference)
                .answer(anyString());
        var reports = json.createArrayNode();
        var http = new PocHttpClient(port, "", Duration.ofMinutes(4));
        int failures = 0;
        for (JsonNode sample : samples) {
            analysis.set(null);
            answer.set(null);
            clearInvocations(classifier, reference, chatModel);
            ObjectNode row = sample.deepCopy();
            try {
                var session = UUID.randomUUID();
                var id = UUID.randomUUID();
                var body = http.message(id, "request", null, true);
                ((ObjectNode) body.path("message")).put("conversation_id", session.toString());
                ((ObjectNode) body.at("/message/content"))
                        .put("user_input", sample.path("text").asText());
                var response = http.post(session, id, body);
                var result = http.body(response);
                row.put("http", response.statusCode());
                row.set("acl", result);
                row.set("analysis", json.valueToTree(analysis.get()));
                row.set("reference", json.valueToTree(answer.get()));
                long modelCalls =
                        mockingDetails(chatModel).getInvocations().stream()
                                .filter(i -> i.getMethod().getName().equals("call"))
                                .count();
                row.put("modelCalls", modelCalls);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(result.at("/message/in_reply_to").asText()).isEqualTo(id.toString());
                assertThat(result.at("/message/conversation_id").asText())
                        .isEqualTo(session.toString());
                assertThat(analysis.get()).isNotNull();
                assertThat(analysis.get().getRoute().name())
                        .isEqualTo(sample.path("route").asText());
                verify(classifier).classify(anyString(), anyList());
                boolean isReference = "REFERENCE".equals(sample.path("route").asText());
                if (isReference) {
                    verify(reference).answer(sample.path("text").asText());
                    assertThat(answer.get()).isNotNull();
                    assertThat(answer.get().getFound()).isEqualTo(sample.path("found").asBoolean());
                    assertThat(modelCalls).isBetween(2L, 4L);
                } else {
                    verifyNoInteractions(reference);
                    assertThat(modelCalls).isBetween(1L, 3L);
                }
                boolean command = "COMMAND".equals(sample.path("route").asText());
                assertThat(result.at("/message/performative").asText())
                        .isEqualTo(command ? "propose" : "inform");
                assertThat(result.at("/metadata/final_message").asBoolean()).isFalse();
                if (!command) {
                    assertThat(result.path("state")).isEmpty();
                    assertThat(result.path("suggestions")).hasSize(1);
                    assertThat(result.at("/suggestions/0/action_code").asText())
                            .isEqualTo("status");
                    assertThat(result.at("/metadata/additional_info/0/value").asText())
                            .isEqualTo("information");
                }
                row.put("contractPassed", true);
            } catch (Exception | AssertionError failure) {
                failures++;
                row.put("contractPassed", false);
                row.put("failureType", failure.getClass().getSimpleName());
            }
            reports.add(row);
        }
        Path target = Path.of("target", "gigachat-reference", "report.json");
        Files.createDirectories(target.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), reports);
        verifyNoInteractions(workflow);
        assertThat(failures).as("Ошибки маршрута/контракта; отчёт: %s", target).isZero();
    }
}
