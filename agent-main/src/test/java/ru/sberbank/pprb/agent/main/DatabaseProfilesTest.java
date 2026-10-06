package ru.sberbank.pprb.agent.main;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Проверяет отказ запуска при неоднозначном выборе БД до создания DataSource и выполнения миграций.
 */
class DatabaseProfilesTest {
    @ParameterizedTest
    @ValueSource(strings = {"poc-local", "h2,postgres", "bootstrap,h2", "bootstrap,postgres"})
    @DisplayName("Запуск требует ровно один профиль БД; bootstrap нельзя совмещать с хранилищем")
    void rejectsMissingOrConflictingDatabaseProfiles(String profiles) {
        new ApplicationContextRunner()
                .withUserConfiguration(ProfileValidationConfiguration.class)
                .withPropertyValues("spring.profiles.active=" + profiles)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("профиль");
                        });
    }
}
