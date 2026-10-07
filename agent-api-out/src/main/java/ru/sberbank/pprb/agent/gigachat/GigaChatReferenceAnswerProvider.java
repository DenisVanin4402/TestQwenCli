package ru.sberbank.pprb.agent.gigachat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import ru.sberbank.pprb.agent.gigachat.config.GigaChatReferenceProperties;
import ru.sberbank.pprb.agent.model.dto.turn.ReferenceAnswerDTO;
import ru.sberbank.pprb.agent.service.port.out.ReferenceAnswerException;
import ru.sberbank.pprb.agent.service.port.out.ReferenceAnswerProvider;

/** Один справочный вызов по полному ресурсу знаний; JSON и метки проверяются локально. */
public class GigaChatReferenceAnswerProvider implements ReferenceAnswerProvider {
    private final ChatClient client;
    private final String system;
    private final String user;
    private final Set<String> sourceIds;
    private final ObjectMapper json =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public GigaChatReferenceAnswerProvider(ChatClient client, GigaChatReferenceProperties prompts) {
        this.client = client;
        String knowledge;
        try {
            knowledge = prompts.knowledgeResource().getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Не удалось прочитать знания", exception);
        }
        if (knowledge.isBlank()) throw new IllegalArgumentException("Знания пусты");
        Set<String> ids = new HashSet<>();
        // Проверяется только формат самостоятельной метки, без разбора статей и заголовков.
        Matcher markers =
                Pattern.compile("(?<![\\p{L}\\p{N}_-])KB-[0-9]+(?![\\p{L}\\p{N}_-])")
                        .matcher(knowledge);
        while (markers.find()) ids.add(markers.group());
        sourceIds = Set.copyOf(ids);
        ObjectNode schema =
                json.valueToTree(
                        new BeanOutputConverter<>(ReferenceAnswerDTO.class).getJsonSchemaMap());
        schema.putArray("required").add("found").add("answer").add("sourceIds");
        schema.put("additionalProperties", false);
        ((ObjectNode) schema.path("properties").path("answer"))
                .putArray("type")
                .add("string")
                .add("null");
        system =
                prompts.system()
                        .replace("{{schema}}", schema.toString())
                        .replace("{{knowledge}}", knowledge);
        user = prompts.user();
    }

    @Override
    public ReferenceAnswerDTO answer(String question) {
        try {
            String raw =
                    client.prompt()
                            .system(system)
                            .user(user.replace("{{message}}", question))
                            .call()
                            .content();
            JsonNode node = json.readTree(raw);
            if (node == null
                    || !node.isObject()
                    || node.size() != 3
                    || !node.has("found")
                    || !node.get("found").isBoolean()
                    || !node.has("answer")
                    || !(node.get("answer").isNull() || node.get("answer").isTextual())
                    || !node.has("sourceIds")
                    || !node.get("sourceIds").isArray()) {
                throw new IllegalArgumentException("Нарушен JSON-контракт справки");
            }
            Set<String> used = new HashSet<>();
            for (JsonNode id : node.get("sourceIds")) {
                if (!id.isTextual()
                        || !sourceIds.contains(id.textValue())
                        || !used.add(id.textValue())) {
                    throw new IllegalArgumentException("Некорректные метки справки");
                }
            }
            ReferenceAnswerDTO result = json.treeToValue(node, ReferenceAnswerDTO.class);
            if (result.getFound()) {
                if (result.getAnswer() == null || result.getAnswer().isBlank())
                    throw new IllegalArgumentException("Нет текста ответа");
                // Ответ без опоры на раздел знаний не выдаём пользователю как найденную справку.
                if (used.isEmpty()) return new ReferenceAnswerDTO(false, null, List.of());
            } else if (result.getAnswer() != null || !used.isEmpty()) {
                throw new IllegalArgumentException("Ответ при отсутствии сведений");
            }
            return result;
        } catch (Exception exception) {
            throw new ReferenceAnswerException("Не удалось получить справку");
        }
    }
}
