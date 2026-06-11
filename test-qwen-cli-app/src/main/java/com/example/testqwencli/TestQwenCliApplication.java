package com.example.testqwencli;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
		DataSourceAutoConfiguration.class,
		LiquibaseAutoConfiguration.class
})
@EnableScheduling
@OpenAPIDefinition(info = @Info(
		title = "TestQwenCli External Service Gateway API",
		version = "0.1.0",
		description = "Публичный HTTP-контракт external-service-gateway и dashboard API."
))
public class TestQwenCliApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestQwenCliApplication.class, args);
	}
}
