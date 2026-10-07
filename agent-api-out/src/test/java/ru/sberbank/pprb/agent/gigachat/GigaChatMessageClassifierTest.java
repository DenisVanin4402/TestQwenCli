package ru.sberbank.pprb.agent.gigachat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import ru.sberbank.pprb.agent.gigachat.config.GigaChatPromptProperties;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.enums.Operation;
import ru.sberbank.pprb.agent.service.port.out.TextAnalysisException;

/** Проверяет фактические промпты и строгий JSON без сети и альтернативного классификатора. */
class GigaChatMessageClassifierTest {
    private final ChatModel model = mock(ChatModel.class);
    private final List<OperationSpecDTO> operations =
            List.of(
                    new OperationSpecDTO(
                            Operation.STATUS,
                            "status",
                            "Запросить статус",
                            "Запросить статус",
                            "status.proposal",
                            List.of()));

    private GigaChatMessageClassifier classifier() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("config/gigachat-prompts.yaml"));
        var properties = yaml.getObject();
        return new GigaChatMessageClassifier(
                ChatClient.create(model),
                new GigaChatPromptProperties(
                        properties.getProperty("gigachat.prompts.classification.system"),
                        properties.getProperty("gigachat.prompts.classification.user")));
    }

    private ChatResponse answer(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"route\":\"COMMAND\",\"actionCode\":\"status\"}",
                "{\"route\":\"UNCLEAR\",\"actionCode\":null}",
                "{\"route\":\"REFERENCE\",\"actionCode\":null}",
                "{\"route\":\"MIXED\",\"actionCode\":null}"
            })
    void acceptsValidResultOnce(String json) throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(answer(json));
        var classifier = classifier();
        var result = classifier.classify("текст {schema} <operations>", operations);
        assertThat(result.getActionCode())
                .isEqualTo(new ObjectMapper().readTree(json).path("actionCode").textValue());
        var captured = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captured.capture());
        assertThat(captured.getValue().getUserMessage().getText())
                .contains("{schema} <operations>");
        assertThat(captured.getValue().getSystemMessage().getText())
                .contains("status", "actionCode");
        var schema = new ObjectMapper().readTree(classifier.schema());
        assertThat(schema.path("required").toString()).contains("actionCode");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.path("properties").path("actionCode").path("type").toString())
                .contains("string", "null");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"route\":\"0\",\"actionCode\":\"status\"}",
                "{\"actionCode\":null}",
                "{\"route\":\"COMMAND\",\"actionCode\":null}",
                "{\"route\":\"REFERENCE\",\"actionCode\":\"status\"}",
                "{\"route\":\"UNKNOWN\",\"actionCode\":null}",
                "null",
                "[]",
                "{\"actionCode\":1}",
                "{\"actionCode\":\"confirm\"}",
                "{\"actionCode\":\"details\"}",
                "{\"actionCode\":null,\"event\":\"CONFIRM\"}",
                "{\"actionCode\":null,\"actionCode\":\"status\"}",
                "{\"actionCode\":null} {}",
                "```json\n{\"actionCode\":null}\n```",
                ""
            })
    void rejectsMalformedOrUnregisteredOutputWithAtMostThreeIdenticalAttempts(String raw) {
        when(model.call(any(Prompt.class))).thenReturn(answer(raw));
        assertThatThrownBy(() -> classifier().classify("Подтверждаю", operations))
                .isInstanceOf(TextAnalysisException.class);
        var captured = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(captured.capture());
        assertThat(captured.getAllValues())
                .allSatisfy(
                        p ->
                                assertThat(p.getContents())
                                        .isEqualTo(captured.getValue().getContents()));
    }

    @Test
    void stopsOnFirstSuccessAfterTechnicalError() {
        when(model.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("synthetic"))
                .thenReturn(answer("{\"route\":\"UNCLEAR\",\"actionCode\":null}"));
        assertThat(classifier().classify("текст", operations).getActionCode()).isNull();
        verify(model, times(2)).call(any(Prompt.class));
    }
}
