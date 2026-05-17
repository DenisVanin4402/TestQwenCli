package com.example.testqwencli;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HomeController {

	@GetMapping("/")
	String home() {
		return "TestQwenCli is running";
	}
}
