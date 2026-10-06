package ru.sberbank.pprb.agent.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Точка запуска исполняемого JAR; конфигурации этого модуля соединяют сценарий с API и БД. */
@SpringBootApplication
public class AgentApplication {
    /** Запускает Spring и применяет выбранные профили и параметры окружения. */
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
