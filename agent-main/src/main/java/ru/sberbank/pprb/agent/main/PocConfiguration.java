package ru.sberbank.pprb.agent.main;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.sberbank.pprb.agent.service.port.out.ReferenceAnswerException;
import ru.sberbank.pprb.agent.service.port.out.ReferenceAnswerProvider;
import ru.sberbank.pprb.agent.service.port.out.TextAnalysisException;
import ru.sberbank.pprb.agent.service.port.out.TextMessageClassifier;

/** Подключает входной API, общий сценарий, corr и хранение для обработки HTTP-запросов. */
@Configuration(proxyBeanMethods = false)
@Profile("!bootstrap")
@ComponentScan(
        basePackages = {
            "ru.sberbank.pprb.agent.db",
            "ru.sberbank.pprb.agent.service",
            "ru.sberbank.pprb.agent.gigaassistant",
            "ru.sberbank.pprb.agent.gigachat",
            "ru.sberbank.pprb.agent.chatui",
            "ru.sberbank.pprb.agent.investcorr"
        })
public class PocConfiguration {
    /** Отключённая справка не читает ресурс и не подменяет ответ модели. */
    @Bean
    @ConditionalOnProperty(
            name = "spring.ai.model.chat",
            havingValue = "none",
            matchIfMissing = true)
    ReferenceAnswerProvider disabledReferenceProvider() {
        return question -> {
            throw new ReferenceAnswerException("GigaChat выключен");
        };
    }

    /** Выключенная модель даёт явный технический отказ, не подменяя распознавание заглушкой. */
    @Bean
    @ConditionalOnProperty(
            name = "spring.ai.model.chat",
            havingValue = "none",
            matchIfMissing = true)
    TextMessageClassifier disabledTextClassifier() {
        return (text, operations) -> {
            throw new TextAnalysisException("GigaChat выключен");
        };
    }
}
