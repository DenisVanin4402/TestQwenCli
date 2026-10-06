package ru.sberbank.pprb.agent.db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.statemachine.data.jpa.JpaRepositoryStateMachine;
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository;

/** Собирает одну persistence unit приложения и только нужную native entity SSM. */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = "ru.sberbank.pprb.agent.db.repository")
public class DatabaseConfiguration {
    /** Явный список исключает ненужные native таблицы хранения графа FSM. */
    @Bean
    PersistenceManagedTypes persistenceManagedTypes() {
        return PersistenceManagedTypes.of(
                "ru.sberbank.pprb.agent.model.entity.persistence.session.SessionEntity",
                JpaRepositoryStateMachine.class.getName());
    }

    /** Регистрирует только repository native checkpoint, без репозиториев графа. */
    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(
            basePackageClasses = JpaStateMachineRepository.class,
            includeFilters =
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = JpaStateMachineRepository.class))
    static class NativeRepositoriesConfiguration {}
}
