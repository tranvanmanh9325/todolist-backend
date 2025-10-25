package com.example.todo.repository;

import com.example.todo.entity.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findByEmailAndOtpCode(String email, String otpCode);
    void deleteByEmail(String email);

    // Thêm method để xoá tất cả OTP đã hết hạn với @Modifying và @Query
    @Modifying
    @Query("DELETE FROM Otp o WHERE o.expiresAt < :dateTime")
    int deleteAllByExpiresAtBefore(@Param("dateTime") LocalDateTime dateTime);
}