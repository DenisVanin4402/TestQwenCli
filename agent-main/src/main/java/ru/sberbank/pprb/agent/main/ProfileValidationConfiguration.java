package ru.sberbank.pprb.agent.main;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Проверяет явный выбор БД до создания соединений: случайного переключения PostgreSQL на H2 нет.
 */
@Configuration(proxyBeanMethods = false)
public class ProfileValidationConfiguration {
    /**
     * Отклоняет несовместимые профили до инициализации DataSource и миграций. Каркасный bootstrap
     * работает без хранения; обычному приложению нужен ровно один профиль БД.
     */
    @Bean
    static BeanFactoryPostProcessor validateDatabaseProfile(Environment environment) {
        return beanFactory -> {
            boolean h2 = environment.acceptsProfiles(Profiles.of("h2"));
            boolean postgres = environment.acceptsProfiles(Profiles.of("postgres"));
            boolean bootstrap = environment.acceptsProfiles(Profiles.of("bootstrap"));
            if (bootstrap && (h2 || postgres)) {
                throw new IllegalStateException(
                        "Нельзя совмещать профиль bootstrap с профилями БД h2 или postgres");
            }
            if (!bootstrap && h2 == postgres) {
                throw new IllegalStateException("Выберите ровно один профиль БД: h2 или postgres");
            }
        };
    }
}
