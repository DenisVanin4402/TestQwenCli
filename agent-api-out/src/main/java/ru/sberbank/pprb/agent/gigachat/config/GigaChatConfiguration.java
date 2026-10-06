package ru.sberbank.pprb.agent.gigachat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.sberbank.pprb.agent.gigachat.GigaChatMessageClassifier;
import ru.sberbank.pprb.agent.gigachat.GigaChatResponseAdvisor;
import ru.sberbank.pprb.agent.service.port.out.TextMessageClassifier;

/** Подключает синхронный клиент к модели, созданной штатной автоконфигурацией GigaChat. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "gigachat")
@EnableConfigurationProperties(GigaChatPromptProperties.class)
public class GigaChatConfiguration {
    /**
     * SDK 1.1.2 регистрирует embedding-модель без условия, игнорируя enabled=false. Удаляем только
     * её определение до создания beans, сохраняя штатный chat/OAuth wiring.
     */
    @Bean
    static BeanDefinitionRegistryPostProcessor disableUnusedEmbeddingModel() {
        return registry -> {
            if (registry.containsBeanDefinition("gigaChatEmbeddingModel")) {
                registry.removeBeanDefinition("gigaChatEmbeddingModel");
            }
        };
    }

    /** Не добавляет память, инструменты или повторные обращения к модели. */
    @Bean
    ChatClient gigaChatClient(ChatClient.Builder builder) {
        return builder.defaultAdvisors(new GigaChatResponseAdvisor()).build();
    }

    /** Разбор использует тот же клиент; повторы относятся только к классификации. */
    @Bean
    TextMessageClassifier textMessageClassifier(
            ChatClient gigaChatClient, GigaChatPromptProperties prompts) {
        return new GigaChatMessageClassifier(gigaChatClient, prompts);
    }
}
