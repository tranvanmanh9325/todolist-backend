package com.example.todo.controller;

import com.example.todo.dto.ErrorResponse;
import com.example.todo.dto.LoginRequest;
import com.example.todo.dto.LoginResponse;
import com.example.todo.dto.SignUpRequest;
import com.example.todo.entity.User;
import com.example.todo.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");
    private static final String JWT_SECRET = "your-secure-secret-key-32-chars-long-min";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

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

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        logger.info("Reset password request for email: {}", request.getEmail());

        if (!userRepository.existsByEmail(request.getEmail())) {
            logger.warn("Reset password failed: Email not found: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Email not found"));
        }

        // TODO: Implement logic to send reset password email (e.g., generate token, send via SMTP)
        logger.info("Reset password link sent for email: {}", request.getEmail());
        return ResponseEntity.ok("Reset password link sent to your email");
    }

    // Inner class for reset password request
    @Setter
    @Getter
    public static class ResetPasswordRequest {
        private String email;

    }
}