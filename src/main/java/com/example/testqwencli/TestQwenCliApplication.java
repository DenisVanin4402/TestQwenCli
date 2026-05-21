package com.example.testqwencli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TestQwenCliApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestQwenCliApplication.class, args);
	}
}
