package com.example.todo.controller;

import com.example.todo.dto.ErrorResponse;
import com.example.todo.dto.LoginRequest;
import com.example.todo.dto.LoginResponse;
import com.example.todo.dto.SignUpRequest;
import com.example.todo.entity.Otp;
import com.example.todo.entity.User;
import com.example.todo.repository.OtpRepository;
import com.example.todo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
// cho dev environment: frontend dev server mặc định là http://localhost:5173
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    // JWT secret
    @Value("${jwt.secret:your-secure-secret-key-32-chars-long-min}")
    private String jwtSecret;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JavaMailSender mailSender;

    /* ==================== LOGIN THƯỜNG ==================== */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Failed login attempt for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password"));
        }

        String token = Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000)) // 1 ngày
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();

        logger.info("Successful login for email: {}", request.getEmail());
        return ResponseEntity.ok(
                new LoginResponse(user.getId(), user.getName(), user.getEmail(), token, user.getAvatar())
        );
    }

    /* ==================== SIGNUP ==================== */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignUpRequest request) {
        logger.info("Signup attempt for email: {}", request.getEmail());

        if (!request.getPassword().equals(request.getConfirm())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Passwords do not match"));
        }

        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Password must be strong"));
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Email already exists"));
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAvatar(null);
        userRepository.save(user);

        return ResponseEntity.ok("Signup successful");
    }

    /* ==================== GOOGLE LOGIN ==================== */
    @PostMapping("/google-login")
    public ResponseEntity<?> handleGoogleLogin(@RequestBody CodeRequest codeRequest) {
        try {
            logger.info("=== Google Login Start ===");
            String code = codeRequest.getCode() != null ? codeRequest.getCode().trim() : null;
            String redirectUri = codeRequest.getRedirectUri();

            logger.info("Received code (present={}): {}", code != null, maskCodeForLog(code));
            logger.info("Using redirectUri for token exchange: {}", redirectUri);

            if (code == null || code.isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Missing code"));
            }

            if (redirectUri == null || redirectUri.isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Missing redirectUri"));
            }

            // 1. Đổi code -> token
            String tokenEndpoint = "https://oauth2.googleapis.com/token";
            String body = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(googleClientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(googleClientSecret, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            int status = tokenResponse.statusCode();
            String responseBody = tokenResponse.body();

            logger.info("Google token endpoint returned status={}, body={}", status, responseBody);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(responseBody);

            if (status != 200 || !jsonNode.has("id_token")) {
                String googleErr = jsonNode.has("error_description")
                        ? jsonNode.get("error_description").asText()
                        : jsonNode.has("error") ? jsonNode.get("error").asText() : "unknown";
                logger.error("Failed to retrieve ID token from Google. status={}, error={}", status, googleErr);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Failed to retrieve Google ID token: " + googleErr));
            }

            // 2. Verify ID token
            String idToken = jsonNode.get("id_token").asText();
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                logger.warn("Invalid Google ID token (verifier returned null)");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid Google token"));
            }

            // 3. Lấy thông tin user từ Google
            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            logger.info("Google user info: email={}, name={}, pictureUrl={}", email, name, pictureUrl);

            // 4. Tìm user theo email, nếu chưa có thì tạo
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setName(name);
                newUser.setPassword(passwordEncoder.encode("google-user-" + System.currentTimeMillis()));
                newUser.setAvatar(pictureUrl);
                try {
                    return userRepository.save(newUser);
                } catch (DataIntegrityViolationException e) {
                    logger.warn("Race condition: user with email {} already exists", email);
                    return userRepository.findByEmail(email).orElseThrow();
                }
            });

            if (user.getAvatar() == null && pictureUrl != null) {
                user.setAvatar(pictureUrl);
                userRepository.save(user);
            }

            // 5. Tạo JWT
            String jwt = Jwts.builder()
                    .subject(user.getEmail())
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 86400000)) // 1 day
                    .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .compact();

            logger.info("Generated JWT for Google login user: {}", email);
            logger.info("=== Google Login End ===");

            return ResponseEntity.ok(
                    new LoginResponse(user.getId(), user.getName(), user.getEmail(), jwt, user.getAvatar())
            );

        } catch (Exception e) {
            logger.error("Error during Google login: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Google login failed: " + e.getMessage()));
        }
    }

    /* ==================== PASSWORD RESET + OTP ==================== */
    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        if (!userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Email not found"));
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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to send OTP"));
        }

        return ResponseEntity.ok("OTP sent to your email");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        Optional<Otp> otpOptional = otpRepository.findByEmailAndOtpCode(request.getEmail(), request.getOtpCode());
        if (otpOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid OTP"));
        }

        Otp otp = otpOptional.get();
        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            otpRepository.delete(otp);
            return ResponseEntity.badRequest().body(new ErrorResponse("OTP has expired"));
        }

        otpRepository.delete(otp);
        return ResponseEntity.ok("OTP verified successfully");
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirm())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Passwords do not match"));
        }

        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Password must be strong"));
        }

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Email not found"));
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        otpRepository.deleteByEmail(request.getEmail());

        return ResponseEntity.ok("Password changed successfully");
    }

    /* ==================== DTOs nội bộ ==================== */
    @Getter @Setter
    public static class CodeRequest {
        private String code;
        private String redirectUri;
    }

    @Getter @Setter
    public static class ResetPasswordRequest {
        private String email;
    }

    @Getter @Setter
    public static class VerifyOtpRequest {
        private String email;
        private String otpCode;
    }

    @Getter @Setter
    public static class ChangePasswordRequest {
        private String email;
        private String password;
        private String confirm;
    }

    // helper: mask code in logs a bit (optional)
    private static String maskCodeForLog(String code) {
        if (code == null) return null;
        if (code.length() <= 8) return "****";
        return code.substring(0, 4) + "****" + code.substring(code.length() - 4);
    }
}