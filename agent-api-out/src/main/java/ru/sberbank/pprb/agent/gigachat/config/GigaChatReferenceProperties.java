package ru.sberbank.pprb.agent.gigachat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/** Шаблоны справки и полный локальный ресурс знаний для включённой модели. */
@ConfigurationProperties("gigachat.prompts.reference")
public record GigaChatReferenceProperties(String system, String user, Resource knowledgeResource) {
    public GigaChatReferenceProperties {
        if (system == null
                || !system.contains("{{knowledge}}")
                || !system.contains("{{schema}}")
                || user == null
                || !user.contains("{{message}}")
                || knowledgeResource == null) {
            throw new IllegalArgumentException("Не заданы шаблоны справки или ресурс знаний");
        }
    }
}
