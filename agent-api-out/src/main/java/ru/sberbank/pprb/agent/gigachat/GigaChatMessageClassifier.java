package ru.sberbank.pprb.agent.gigachat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import ru.sberbank.pprb.agent.gigachat.config.GigaChatPromptProperties;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.dto.turn.MessageAnalysisDTO;
import ru.sberbank.pprb.agent.model.enums.MessageRoute;
import ru.sberbank.pprb.agent.service.port.out.*;

/** Синхронный JSON-разбор без доступа к FSM; технические повторы ограничены этим вызовом. */
public class GigaChatMessageClassifier implements TextMessageClassifier {
    private final ChatClient client;
    private final GigaChatPromptProperties prompts;
    private final ObjectMapper json =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final String schema;

    public GigaChatMessageClassifier(ChatClient client, GigaChatPromptProperties prompts) {
        this.client = client;
        this.prompts = prompts;
        // Схема выводится из DTO; уточняются обязательность nullable-поля и закрытость объекта.
        java.util.Map<String, Object> generated =
                new BeanOutputConverter<>(MessageAnalysisDTO.class).getJsonSchemaMap();
        ObjectNode node = json.valueToTree(generated);
        node.putArray("required").add("route").add("actionCode");
        node.put("additionalProperties", false);
        ((ObjectNode) node.path("properties").path("actionCode"))
                .putArray("type")
                .add("string")
                .add("null");
        schema = node.toString();
    }

    /** Возвращает схему фактического контракта для проверки согласованности с парсером. */
    String schema() {
        return schema;
    }

    @Override
    public MessageAnalysisDTO classify(String text, List<OperationSpecDTO> operations) {
        String choices =
                operations.stream()
                        .map(o -> o.getActionCode() + ": " + o.getTitle())
                        .collect(Collectors.joining("\n"));
        String system =
                prompts.system().replace("{{operations}}", choices).replace("{{schema}}", schema);
        // Однократная подстановка: переменные внутри сообщения клиента не интерпретируются.
        String user = prompts.user().replace("{{message}}", text);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String raw = client.prompt().system(system).user(user).call().content();
                JsonNode node = json.readTree(raw);
                if (node == null
                        || !node.isObject()
                        || node.size() != 2
                        || !node.has("route")
                        || !node.get("route").isTextual()
                        || !node.has("actionCode")
                        || !(node.get("actionCode").isNull()
                                || node.get("actionCode").isTextual())) {
                    throw new IllegalArgumentException("Нарушен JSON-контракт разбора");
                }
                MessageAnalysisDTO result =
                        new MessageAnalysisDTO(
                                MessageRoute.valueOf(node.get("route").textValue()),
                                node.get("actionCode").textValue());
                if (result.getRoute() == MessageRoute.COMMAND) {
                    if (result.getActionCode() == null
                            || result.getActionCode().isBlank()
                            || operations.stream()
                                    .noneMatch(
                                            o ->
                                                    o.getActionCode()
                                                            .equals(result.getActionCode()))) {
                        throw new IllegalArgumentException("Незарегистрированная операция");
                    }
                } else if (result.getActionCode() != null) {
                    throw new IllegalArgumentException("Код операции допустим только для COMMAND");
                }
                return result;
            } catch (Exception exception) {
                // Промпты/ответ логирует общий advisor; повтор использует тот же промпт.
                if (attempt == 2) throw new TextAnalysisException("Не удалось разобрать сообщение");
            }
        }
        throw new IllegalStateException("Недостижимая ветвь");
    }
}
