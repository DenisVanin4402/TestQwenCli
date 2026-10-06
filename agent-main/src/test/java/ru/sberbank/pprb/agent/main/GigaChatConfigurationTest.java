package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.assertThat;

import chat.giga.springai.GigaChatModel;
import chat.giga.springai.api.auth.GigaChatAuthProperties;
import chat.giga.springai.autoconfigure.GigaChatAutoConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.sberbank.pprb.agent.gigachat.config.GigaChatConfiguration;

/** Проверяет реальные YAML и импорт .env без обращений к внешнему сервису. */
class GigaChatConfigurationTest {
    @TempDir Path directory;

    /** Профили без модели не требуют даже синтаксически корректных credentials. */
    @Test
    void disabledAndBootstrapDoNotRequireCredentials() {
        for (String profile : new String[] {"h2", "bootstrap"}) {
            context()
                    .withPropertyValues(
                            "GIGACHAT_MODE=none",
                            "GIGACHAT_API_KEY=",
                            "spring.profiles.active=" + profile)
                    .run(
                            context -> {
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(ChatClient.class)
                                        .doesNotHaveBean(GigaChatModel.class)
                                        .doesNotHaveBean(GigaChatAuthProperties.class);
                            });
        }
    }

    /** Файл читается штатным механизмом Spring; токен не подменяет OAuth-ключ. */
    @Test
    void importsEnvUsesOnlyKeyAndAllowsHigherPriorityOverride() throws Exception {
        Path envFile = directory.resolve(".env");
        Files.writeString(
                envFile,
                "GIGACHAT_MODE=gigachat\nGIGACHAT_API_KEY=synthetic-env-key\nGIGACHAT_ACCESS_TOKEN=unused-token\nGIGACHAT_MODEL=env-model\n");
        realContext()
                .withPropertyValues(
                        "spring.config.import=classpath:config/gigachat-prompts.yaml,"
                                + envFile.toUri()
                                + "[.properties]")
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(ChatClient.class)
                                    .hasSingleBean(GigaChatModel.class)
                                    .doesNotHaveBean(EmbeddingModel.class)
                                    .doesNotHaveBean(ImageModel.class);
                            assertThat(context.getBean(GigaChatAuthProperties.class).getApiKey())
                                    .isEqualTo("synthetic-env-key");
                            assertThat(
                                            context.getBean(GigaChatModel.class)
                                                    .getDefaultOptions()
                                                    .getModel())
                                    .isEqualTo("env-model");
                        });
        realContext()
                .withPropertyValues(
                        "spring.config.import=classpath:config/gigachat-prompts.yaml,"
                                + envFile.toUri()
                                + "[.properties]",
                        "GIGACHAT_API_KEY=override-key",
                        "GIGACHAT_MODEL=override-model")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(GigaChatAuthProperties.class).getApiKey())
                                    .isEqualTo("override-key");
                            assertThat(
                                            context.getBean(GigaChatModel.class)
                                                    .getDefaultOptions()
                                                    .getModel())
                                    .isEqualTo("override-model");
                        });
    }

    /** Ошибка любой обязательной настройки обнаруживается раньше библиотечных beans. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "GIGACHAT_MODE=typo",
                "GIGACHAT_API_KEY=",
                "GIGACHAT_MODEL=",
                "GIGACHAT_CONNECT_TIMEOUT=0s",
                "GIGACHAT_READ_TIMEOUT=invalid",
                "GIGACHAT_MAX_TOKENS=0",
                "GIGACHAT_SCOPE=UNKNOWN",
                "GIGACHAT_BASE_URL=not-a-url",
                "GIGACHAT_AUTH_URL=http://user:password@localhost/oauth",
                "spring.ai.retry.max-attempts=2",
                "spring.ai.gigachat.auth.unsafe-ssl=true",
                "spring.ai.model.image=gigachat",
                "spring.ai.gigachat.embedding.enabled=true",
                "spring.ai.gigachat.chat.options.internal-tool-execution-enabled=true",
                "spring.profiles.active=bootstrap"
            })
    void rejectsInvalidConfigurationWithoutExposingKey(String property) {
        realContext()
                .withPropertyValues(
                        "GIGACHAT_API_KEY=synthetic-secret",
                        "GIGACHAT_ACCESS_TOKEN=not-a-key",
                        property)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("GigaChat:")
                                    .hasMessageNotContaining("synthetic-secret")
                                    .hasMessageNotContaining("not-a-key");
                        });
    }

    /** Включает только модель, без запуска POC и БД. */
    private ApplicationContextRunner realContext() {
        return context().withPropertyValues("GIGACHAT_MODE=gigachat");
    }

    /** Загружает YAML приложения, изолируя локальный пользовательский .env. */
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
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
                        GigaChatValidationConfiguration.class, GigaChatConfiguration.class)
                .withPropertyValues(
                        "spring.config.import=classpath:config/gigachat-prompts.yaml,optional:classpath:/absent-test-env.properties",
                        "spring.profiles.active=h2");
    }
}
