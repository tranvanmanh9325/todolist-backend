package com.example.todo.controller;

import com.example.todo.dto.ErrorResponse;
import com.example.todo.dto.LoginRequest;
import com.example.todo.dto.LoginResponse;
import com.example.todo.dto.SignUpRequest;
import com.example.todo.entity.Otp;
import com.example.todo.entity.User;
import com.example.todo.repository.OtpRepository;
import com.example.todo.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");
    private static final String JWT_SECRET = "your-secure-secret-key-32-chars-long-min";

    @Value("${google.client-id}")
    private String googleClientId;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JavaMailSender mailSender;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Failed login attempt for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password"));
        }

        String token = Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                .compact();

        logger.info("Successful login for email: {}", request.getEmail());
        return ResponseEntity.ok(new LoginResponse(user.getId(), user.getName(), user.getEmail(), token));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignUpRequest request) {
        logger.info("Signup attempt for email: {}", request.getEmail());

        if (!request.getPassword().equals(request.getConfirm())) {
            logger.warn("Signup failed: Passwords do not match for email: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Passwords do not match"));
        }

        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            logger.warn("Signup failed: Weak password for email: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Password must be at least 8 characters long, contain at least one uppercase letter, one lowercase letter, and one digit"));
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            logger.warn("Signup failed: Email already exists: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Email already exists"));
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        logger.info("Successful signup for email: {}", request.getEmail());
        return ResponseEntity.ok("Signup successful");
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        logger.info("Google login attempt for idToken");

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) {
                logger.warn("Google login failed: Invalid ID token");
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Invalid Google ID token"));
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setName(name);
                user.setPassword(passwordEncoder.encode("google-user-" + System.currentTimeMillis())); // Fake password
                userRepository.save(user);
                logger.info("Created new user for Google login: {}", email);
            }

            String token = Jwts.builder()
                    .subject(user.getEmail())
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 86400000))
                    .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                    .compact();

            logger.info("Successful Google login for email: {}", email);
            return ResponseEntity.ok(new LoginResponse(user.getId(), user.getName(), user.getEmail(), token));
        } catch (Exception e) {
            logger.error("Google login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Google login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        logger.info("Reset password request for email: {}", request.getEmail());

        if (!userRepository.existsByEmail(request.getEmail())) {
            logger.warn("Reset password failed: Email not found: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Email not found"));
        }

        String otpCode = String.format("%06d", new Random().nextInt(999999));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(5);

        otpRepository.deleteByEmail(request.getEmail());
        Otp otp = new Otp();
        otp.setEmail(request.getEmail());
        otp.setOtpCode(otpCode);
        otp.setCreatedAt(now);
        otp.setExpiresAt(expiresAt);
        otpRepository.save(otp);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(request.getEmail());
            message.setSubject("Your OTP for Password Reset");
            message.setText("Your OTP is: " + otpCode + "\nThis OTP is valid for 5 minutes.");
            mailSender.send(message);
            logger.info("OTP sent successfully to email: {}", request.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send OTP to email: {}. Error: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to send OTP. Please try again."));
        }

        return ResponseEntity.ok("OTP sent to your email");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        logger.info("Verify OTP attempt for email: {}", request.getEmail());

        Optional<Otp> otpOptional = otpRepository.findByEmailAndOtpCode(request.getEmail(), request.getOtpCode());
        if (otpOptional.isEmpty()) {
            logger.warn("Verify OTP failed: Invalid OTP for email: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid OTP"));
        }

        Otp otp = otpOptional.get();
        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            logger.warn("Verify OTP failed: OTP expired for email: {}", request.getEmail());
            otpRepository.delete(otp);
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("OTP has expired"));
        }

        logger.info("OTP verified for email: {}", request.getEmail());
        otpRepository.delete(otp);
        return ResponseEntity.ok("OTP verified successfully");
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        logger.info("Change password attempt for email: {}", request.getEmail());

        if (!request.getPassword().equals(request.getConfirm())) {
            logger.warn("Change password failed: Passwords do not match for email: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Passwords do not match"));
        }

        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            logger.warn("Change password failed: Weak password for email: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Password must be at least 8 characters long, contain at least one uppercase letter, one lowercase letter, and one digit"));
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);
        if (user == null) {
            logger.warn("Change password failed: Email not found: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Email not found"));
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        otpRepository.deleteByEmail(request.getEmail());

        logger.info("Password changed successfully for email: {}", request.getEmail());
        return ResponseEntity.ok("Password changed successfully");
    }

    // Inner classes for request DTOs
    @Setter
    @Getter
    public static class ResetPasswordRequest {
        private String email;
    }

    @Setter
    @Getter
    public static class VerifyOtpRequest {
        private String email;
        private String otpCode;
    }

    @Setter
    @Getter
    public static class ChangePasswordRequest {
        private String email;
        private String password;
        private String confirm;
    }

    @Setter
    @Getter
    public static class GoogleLoginRequest {
        private String idToken;
    }
}