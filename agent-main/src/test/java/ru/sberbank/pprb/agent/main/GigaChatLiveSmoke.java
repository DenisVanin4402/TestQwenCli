package ru.sberbank.pprb.agent.main;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.models.ModelDescription;
import chat.giga.springai.autoconfigure.GigaChatAutoConfiguration;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.StringUtils;
import ru.sberbank.pprb.agent.gigachat.config.GigaChatConfiguration;

/** Реальная проверка выбирается только профилем gigachat-smoke; ключ и ответы не печатаются. */
class GigaChatLiveSmoke {
    /** Получает доступные модели через SDK, затем делает один синтетический текстовый запрос. */
    @Test
    void listsModelsAndGeneratesText() {
        // ContextRunner не запускает LoggingApplicationListener: применяем запрет body-логов явно.
        LoggingSystem.get(getClass().getClassLoader())
                .setLogLevel(
                        "org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration",
                        LogLevel.OFF);
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(
                        AutoConfigurations.of(
                                JacksonAutoConfiguration.class,
                                HttpMessageConvertersAutoConfiguration.class,
                                RestClientAutoConfiguration.class,
                                SpringAiRetryAutoConfiguration.class,
                                GigaChatAutoConfiguration.class,
                                ChatClientAutoConfiguration.class))
                .withUserConfiguration(
                        GigaChatConfiguration.class, GigaChatValidationConfiguration.class)
                .withPropertyValues("GIGACHAT_MODE=gigachat")
                .run(
                        context -> {
                            if (context.getStartupFailure() != null) {
                                throw new AssertionError(
                                        "GigaChat smoke: ошибка конфигурации; проверьте .env и TLS CA");
                            }
                            String phase = "models";
                            try {
                                for (String property :
                                        new String[] {
                                            "spring.ai.gigachat.base-url",
                                            "spring.ai.gigachat.auth.bearer.url"
                                        }) {
                                    if (!"https"
                                            .equals(
                                                    URI.create(
                                                                    context.getEnvironment()
                                                                            .getRequiredProperty(
                                                                                    property))
                                                            .getScheme())) {
                                        throw new AssertionError("GigaChat smoke требует HTTPS");
                                    }
                                }
                                String model =
                                        context.getEnvironment()
                                                .getRequiredProperty(
                                                        "spring.ai.gigachat.chat.options.model");
                                var models = context.getBean(GigaChatApi.class).models().getBody();
                                if (models == null || models.getData() == null) {
                                    throw new AssertionError(
                                            "GigaChat smoke: пустой список моделей");
                                }
                                List<String> ids =
                                        models.getData().stream()
                                                .map(ModelDescription::getId)
                                                .toList();
                                System.out.println("GigaChat models: " + ids);
                                if (!ids.contains(model)) {
                                    throw new AssertionError(
                                            "GigaChat smoke: выбранная модель отсутствует в /v1/models");
                                }
                                phase = "generation";
                                String text =
                                        context.getBean("gigaChatClient", ChatClient.class)
                                                .prompt()
                                                .system("Отвечай кратко, одним словом.")
                                                .user("Напиши слово Привет.")
                                                .call()
                                                .content();
                                if (!StringUtils.hasText(text)) {
                                    throw new AssertionError("GigaChat smoke: пустой ответ");
                                }
                                System.out.println("GigaChat smoke OK; model=" + model);
                            } catch (RuntimeException exception) {
                                Throwable root = exception;
                                while (root.getCause() != null && root.getCause() != root) {
                                    root = root.getCause();
                                }
                                throw new AssertionError(
                                        "GigaChat smoke: "
                                                + phase
                                                + "; "
                                                + exception.getClass().getSimpleName()
                                                + "/"
                                                + root.getClass().getSimpleName());
                            }
                        });
    }
}
