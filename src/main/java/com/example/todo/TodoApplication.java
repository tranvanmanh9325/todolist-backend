package com.example.todo;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TodoApplication {
    public static void main(String[] args) {
        // Nạp file .env từ thư mục gốc
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing() // Không lỗi nếu file .env không tồn tại
                .load();
        // Đặt các biến môi trường từ file .env vào System properties
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        // Khởi động ứng dụng Spring Boot
        SpringApplication.run(TodoApplication.class, args);
    }
}