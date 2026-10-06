package ru.sberbank.pprb.agent.gigachat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Полные шаблоны классификации загружаются из YAML при включении модели. */
@ConfigurationProperties("gigachat.prompts.classification")
public record GigaChatPromptProperties(String system, String user) {
    public GigaChatPromptProperties {
        if (system == null
                || !system.contains("{{operations}}")
                || !system.contains("{{schema}}")
                || user == null
                || !user.contains("{{message}}")) {
            throw new IllegalArgumentException(
                    "Не заданы шаблоны классификации и обязательные переменные");
        }
    }
}
