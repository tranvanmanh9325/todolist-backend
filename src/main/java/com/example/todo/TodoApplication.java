package com.example.todo;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TodoApplication {

	public static void main(String[] args) {
		// Load variables from .env and set them as system properties
		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing() // Không báo lỗi nếu không có file .env
				.load();

		dotenv.entries().forEach(entry ->
				System.setProperty(entry.getKey(), entry.getValue())
		);

		// Start Spring Boot
		SpringApplication.run(TodoApplication.class, args);
	}
}