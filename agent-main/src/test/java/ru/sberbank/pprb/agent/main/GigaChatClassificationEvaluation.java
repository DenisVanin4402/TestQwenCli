package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sberbank.pprb.agent.service.port.out.WorkflowManager;
import ru.sberbank.pprb.agent.service.turn.SessionResponseRenderer;

/** Только явный профиль запускает реальный GigaChat через полный HTTP-путь агента. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.ai.model.chat=gigachat",
            "spring.datasource.url=jdbc:h2:mem:classification-eval;DB_CLOSE_DELAY=-1"
        })
@ActiveProfiles({"poc-local", "h2"})
class GigaChatClassificationEvaluation {
    @LocalServerPort int port;
    @MockitoBean WorkflowManager workflow;
    @Autowired SessionResponseRenderer renderer;

    @Value("${classification.dataset:classpath:gigachat/classification-status.json}")
    Resource dataset;

    @Value("${spring.ai.gigachat.chat.options.model}")
    String model;

    @Test
    void evaluatesAllMessagesAndWritesConfusionMatrix() throws Exception {
        List<ClassificationEvaluation.Sample> samples;
        try (var input = dataset.getInputStream()) {
            samples = ClassificationEvaluation.readDataset(input);
        }
        var evaluation = new ClassificationEvaluation();
        var http = new PocHttpClient(port, "", Duration.ofMinutes(4));
        String clarification = renderer.clarification(UUID.randomUUID()).getResult().getMessage();
        for (var sample : samples) {
            String actual = null;
            boolean error = true;
            try {
                UUID session = UUID.randomUUID();
                UUID request = UUID.randomUUID();
                var body = http.message(request, "request", null, true);
                ((ObjectNode) body.path("message")).put("conversation_id", session.toString());
                ((ObjectNode) body.at("/message/content")).put("user_input", sample.text());
                var response = http.post(session, request, body);
                JsonNode result = http.body(response);
                boolean correlated =
                        request.toString().equals(result.at("/message/in_reply_to").asText())
                                && session.toString()
                                        .equals(result.at("/message/conversation_id").asText());
                if (response.statusCode() == 200
                        && correlated
                        && !result.at("/metadata/final_message").asBoolean(true)) {
                    String performative = result.at("/message/performative").asText();
                    if ("propose".equals(performative)
                            && validStatusConfirmation(result.path("state"))) {
                        actual = "status";
                        error = false;
                    } else if ("inform".equals(performative)
                            && clarification.equals(result.at("/message/content/result").asText())
                            && result.path("state").isArray()
                            && result.path("state").isEmpty()
                            && result.path("suggestions").isArray()
                            && result.path("suggestions").isEmpty()) {
                        error = false;
                    }
                }
            } catch (Exception exception) {
                // Сбой конкретного примера не отменяет оценку остальных; секреты/ответ SDK не
                // печатаем.
            }
            evaluation.add(sample, actual, error);
            System.out.printf(
                    "Classification example: %s, expected=%s, actual=%s, error=%s%n",
                    sample.id(), sample.expectedActionCode(), actual, error);
        }
        var report = evaluation.report(model, dataset.getDescription());
        Path path = Path.of("target", "gigachat-classification", "report.json");
        Files.createDirectories(path.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), report);
        System.out.printf(
                "Classification: TP=%s TN=%s FP=%s FN=%s errors=%s total=%s%n",
                report.get("TP"),
                report.get("TN"),
                report.get("FP"),
                report.get("FN"),
                report.get("errors"),
                report.get("total"));
        verifyNoInteractions(workflow);
        for (String counter : List.of("FP", "FN", "errors"))
            assertThat(report.path(counter).asInt()).as(counter).isZero();
    }

    private boolean validStatusConfirmation(JsonNode state) {
        if (!state.isArray() || state.size() != 2) return false;
        Map<String, String> values = new HashMap<>();
        for (JsonNode parameter : state) {
            if (!"CONFIRMATION".equals(parameter.path("type").asText())
                    || values.put(parameter.path("key").asText(), parameter.path("value").asText())
                            != null) return false;
        }
        return "42".equals(values.get("paymentNumber")) && "1".equals(values.get("preparationNo"));
    }
}
