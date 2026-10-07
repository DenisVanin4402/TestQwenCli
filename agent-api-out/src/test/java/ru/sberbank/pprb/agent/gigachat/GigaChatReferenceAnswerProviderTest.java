package ru.sberbank.pprb.agent.gigachat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import ru.sberbank.pprb.agent.gigachat.config.GigaChatReferenceProperties;
import ru.sberbank.pprb.agent.service.port.out.ReferenceAnswerException;

/** Проверяет строгую форму справки и единственную попытку без сети. */
class GigaChatReferenceAnswerProviderTest {
    private final ChatModel model = mock(ChatModel.class);

    private GigaChatReferenceAnswerProvider provider() {
        return provider(new ClassPathResource("knowledge/status-reference.md"));
    }

    private GigaChatReferenceAnswerProvider provider(Resource resource) {
        return new GigaChatReferenceAnswerProvider(
                ChatClient.create(model),
                new GigaChatReferenceProperties(
                        "{{knowledge}}\n{{schema}}", "{{message}}", resource));
    }

    private GigaChatReferenceAnswerProvider provider(String knowledge) {
        return provider(new ByteArrayResource(knowledge.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"found\":true,\"answer\":\"Комиссия — 50 рублей\",\"sourceIds\":[\"KB-03\"]}",
                "{\"found\":false,\"answer\":null,\"sourceIds\":[]}"
            })
    void answersOnceUsingAllKnowledgeAndLiteralQuestion(String raw) {
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(raw)))));
        var result = provider().answer("Вопрос {{schema}} {{knowledge}}?");
        assertThat(result.getFound()).isNotNull();
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText())
                .contains("KB-01", "KB-10", "sourceIds", "50 рублей");
        assertThat(prompt.getValue().getUserMessage().getText())
                .isEqualTo("Вопрос {{schema}} {{knowledge}}?");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "[]",
                "",
                "{\"found\":\"true\",\"answer\":\"Ответ\",\"sourceIds\":[\"KB-03\"]}",
                "{\"found\":true,\"answer\":\" \",\"sourceIds\":[\"KB-03\"]}",
                "{\"found\":true,\"answer\":\"Ответ\",\"sourceIds\":[\"KB-99\"]}",
                "{\"found\":true,\"answer\":\"Ответ\",\"sourceIds\":[\"KB-03\",\"KB-03\"]}",
                "{\"found\":true,\"answer\":\"Ответ\",\"sourceIds\":[3]}",
                "{\"found\":false,\"answer\":\"Ответ\",\"sourceIds\":[]}",
                "{\"found\":false,\"answer\":null,\"sourceIds\":[\"KB-03\"]}",
                "{\"found\":false,\"answer\":null}",
                "{\"found\":false,\"answer\":null,\"sourceIds\":[],\"extra\":1}",
                "{\"found\":false,\"found\":false,\"answer\":null,\"sourceIds\":[]}",
                "{\"found\":false,\"answer\":null,\"sourceIds\":[]} {}",
                "```json\n{\"found\":false,\"answer\":null,\"sourceIds\":[]}\n```"
            })
    void invalidAnswerIsFailureWithoutRetry(String raw) {
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(raw)))));
        assertThatThrownBy(() -> provider().answer("Вопрос"))
                .isInstanceOf(ReferenceAnswerException.class);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void answerWithoutSourcesIsMissingKnowledgeWithoutRetry() {
        String raw =
                "{\"found\":true,\"answer\":\"Мне доступны разделы KB-01 по KB-10\",\"sourceIds\":[]}";
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(raw)))));
        var result = provider().answer("Какие материалы и данные тебе доступны?");
        assertThat(result.getFound()).isFalse();
        assertThat(result.getAnswer()).isNull();
        assertThat(result.getSourceIds()).isEmpty();
        verify(model).call(any(Prompt.class));
    }

    @Test
    void transportFailureIsNotMissingKnowledge() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("synthetic"));
        assertThatThrownBy(() -> provider().answer("Вопрос"))
                .isInstanceOf(ReferenceAnswerException.class);
        verify(model).call(any(Prompt.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \n\t"})
    void emptyKnowledgePreventsStartup(String knowledge) {
        assertThatThrownBy(() -> provider(knowledge)).isInstanceOf(IllegalArgumentException.class);
        verify(model, never()).call(any(Prompt.class));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Раздел KB-00: сведения {{schema}} {{knowledge}} {{message}}. Повтор ссылки KB-00.",
                "{\"id\":\"KB-00\",\"text\":\"Сведения\"}",
                "## KB-00. Сведения\n## KB-00. Повтор ссылки"
            })
    void passesKnowledgeUnchangedWithoutRequiringHeadingsOrGeneratingCatalog(String knowledge) {
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        new ChatResponse(
                                List.of(
                                        new Generation(
                                                new AssistantMessage(
                                                        "{\"found\":true,\"answer\":\"Сведения\",\"sourceIds\":[\"KB-00\"]}")))));
        assertThat(provider(knowledge).answer("Вопрос").getSourceIds()).containsExactly("KB-00");
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText()).startsWith(knowledge + "\n{");
    }

    @ParameterizedTest
    @ValueSource(strings = {"KB-01", "KB-010x", "xKB-010", "KB-0100"})
    void rejectsSourcesThatAreNotWholeIdentifiers(String source) {
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        new ChatResponse(
                                List.of(
                                        new Generation(
                                                new AssistantMessage(
                                                        "{\"found\":true,\"answer\":\"Ответ\",\"sourceIds\":[\""
                                                                + source
                                                                + "\"]}")))));
        var reference = provider("KB-010: сведения; xKB-010 и KB-010x — не метки.");
        assertThatThrownBy(() -> reference.answer("Вопрос"))
                .isInstanceOf(ReferenceAnswerException.class);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void loadsKnowledgeWithoutSourcesAndReturnsMissingKnowledge() {
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        new ChatResponse(
                                List.of(
                                        new Generation(
                                                new AssistantMessage(
                                                        "{\"found\":false,\"answer\":null,\"sourceIds\":[]}")))));
        assertThat(provider("Текст без меток").answer("Вопрос").getFound()).isFalse();
        verify(model).call(any(Prompt.class));
    }
}
