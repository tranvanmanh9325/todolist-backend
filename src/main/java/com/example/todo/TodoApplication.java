package com.example.todo;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // thêm để bật scheduling

@SpringBootApplication
@EnableScheduling // bật scheduling cho toàn bộ app (cần cho OtpCleanupService)
public class TodoApplication {
    public static void main(String[] args) {
        // Nạp file .env từ thư mục gốc
        Dotenv dotenv = Dotenv.configure()
                .directory("./") // ✅ giữ lại để chắc chắn tìm .env trong thư mục gốc project
                .ignoreIfMissing() // Không lỗi nếu file .env không tồn tại
                .load();

        // Đặt các biến môi trường từ file .env vào System properties
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        // Khởi động ứng dụng Spring Boot
        SpringApplication.run(TodoApplication.class, args);
    }
}