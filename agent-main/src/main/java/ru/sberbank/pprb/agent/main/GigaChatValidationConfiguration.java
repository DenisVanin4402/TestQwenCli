package ru.sberbank.pprb.agent.main;

import chat.giga.springai.api.auth.GigaChatApiScope;
import java.net.URI;
import java.time.Duration;
import java.util.function.Predicate;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StringUtils;

/** Проверяет явное включение GigaChat до создания клиента, не выводя значения настроек. */
@Configuration(proxyBeanMethods = false)
public class GigaChatValidationConfiguration {
    private static final String PREFIX = "spring.ai.gigachat.";

    /** Не выполняет сетевых проверок и не требует ключей при выключенной интеграции. */
    @Bean
    static BeanFactoryPostProcessor validateGigaChatConfiguration(Environment environment) {
        return beanFactory -> {
            String mode = environment.getProperty("spring.ai.model.chat", "none");
            if ("none".equals(mode)) {
                return;
            }
            if (!"gigachat".equals(mode)) {
                throw invalid("spring.ai.model.chat");
            }
            if (environment.acceptsProfiles(Profiles.of("bootstrap"))) {
                throw invalid("spring.profiles.active: bootstrap несовместим с GigaChat");
            }
            require(
                    environment,
                    PREFIX + "auth.bearer.api-key",
                    String.class,
                    StringUtils::hasText);
            require(environment, PREFIX + "chat.options.model", String.class, StringUtils::hasText);
            require(environment, PREFIX + "auth.scope", GigaChatApiScope.class, value -> true);
            require(
                    environment,
                    PREFIX + "base-url",
                    String.class,
                    GigaChatValidationConfiguration::httpUrl);
            require(
                    environment,
                    PREFIX + "auth.bearer.url",
                    String.class,
                    GigaChatValidationConfiguration::httpUrl);
            require(
                    environment,
                    PREFIX + "internal.connect-timeout",
                    Duration.class,
                    value -> value.isPositive());
            require(
                    environment,
                    PREFIX + "internal.read-timeout",
                    Duration.class,
                    value -> value.isPositive());
            require(
                    environment,
                    PREFIX + "chat.options.max-tokens",
                    Integer.class,
                    value -> value > 0);
            require(
                    environment,
                    "spring.ai.retry.max-attempts",
                    Integer.class,
                    value -> value == 1);
            require(environment, "spring.ai.model.image", String.class, "none"::equals);
            require(environment, PREFIX + "embedding.enabled", Boolean.class, value -> !value);
            require(environment, PREFIX + "auth.unsafe-ssl", Boolean.class, value -> !value);
            require(
                    environment,
                    PREFIX + "chat.options.internal-tool-execution-enabled",
                    Boolean.class,
                    value -> !value);
            String ca = environment.getProperty(PREFIX + "auth.certs.ca-certs");
            if (ca != null) {
                try {
                    if (!StringUtils.hasText(ca)
                            || !new DefaultResourceLoader().getResource(ca).isReadable()) {
                        throw invalid(PREFIX + "auth.certs.ca-certs");
                    }
                } catch (RuntimeException exception) {
                    throw invalid(PREFIX + "auth.certs.ca-certs");
                }
            }
        };
    }

    /** Ошибка преобразования остаётся ошибкой настройки, без исходного значения и cause. */
    private static <T> void require(
            Environment environment, String name, Class<T> type, Predicate<T> valid) {
        T value;
        try {
            value = Binder.get(environment).bind(name, type).orElse(null);
        } catch (RuntimeException exception) {
            throw invalid(name);
        }
        if (value == null || !valid.test(value)) {
            throw invalid(name);
        }
    }

    /** Разрешает HTTPS сервиса и HTTP локального стенда, но не credentials внутри URL. */
    private static boolean httpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && StringUtils.hasText(uri.getHost())
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** В сообщении присутствует только известное имя настройки. */
    private static IllegalStateException invalid(String property) {
        return new IllegalStateException("GigaChat: некорректное свойство " + property);
    }
}
