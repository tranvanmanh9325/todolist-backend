package com.example.todo.repository;

import com.example.todo.entity.Otp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findByEmailAndOtpCode(String email, String otpCode);
    void deleteByEmail(String email);

    // Thêm method để xoá tất cả OTP đã hết hạn
    int deleteAllByExpiresAtBefore(LocalDateTime dateTime);
}