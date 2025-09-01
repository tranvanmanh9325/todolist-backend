package com.example.todo.service;

import com.example.todo.repository.OtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class OtpCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(OtpCleanupService.class);

    private final OtpRepository otpRepository;

    public OtpCleanupService(OtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    /**
     * Job dọn dẹp OTP hết hạn
     * Chạy mỗi phút (60000 ms)
     */
    @Scheduled(fixedRate = 60000)
    @Transactional // cần annotation này để JPA có transaction khi xoá
    public void cleanupExpiredOtps() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        try {
            int deletedCount = otpRepository.deleteAllByExpiresAtBefore(now); // đếm số OTP xoá
            if (deletedCount > 0) {
                logger.info("Đã dọn {} OTP hết hạn lúc {} (Asia/Ho_Chi_Minh)", deletedCount, now);
            } else {
                logger.debug("Không có OTP nào hết hạn lúc {} (Asia/Ho_Chi_Minh)", now);
            }
        } catch (Exception e) {
            logger.error("Lỗi khi dọn dẹp OTP: {}", e.getMessage(), e);
        }
    }
}