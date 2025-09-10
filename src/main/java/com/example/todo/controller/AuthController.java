package com.example.todo.controller;

import com.example.todo.dto.ErrorResponse;
import com.example.todo.dto.LoginRequest;
import com.example.todo.dto.LoginResponse;
import com.example.todo.dto.SignUpRequest;
import com.example.todo.entity.Otp;
import com.example.todo.entity.User;
import com.example.todo.repository.OtpRepository;
import com.example.todo.repository.UserRepository;
import com.example.todo.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173") // frontend dev server
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    @Autowired private UserRepository userRepository;
    @Autowired private OtpRepository otpRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JavaMailSender mailSender;
    @Autowired private JwtService jwtService;

    /* ==================== LOGIN ==================== */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Failed login attempt for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password"));
        }

        String token = jwtService.generateToken(user.getEmail());

        return ResponseEntity.ok(
                new LoginResponse(user.getId(), user.getName(), user.getEmail(), token, user.getAvatar())
        );
    }

    /* ==================== SIGNUP ==================== */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignUpRequest request) {
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
            String code = codeRequest.getCode() != null ? codeRequest.getCode().trim() : null;
            String redirectUri = codeRequest.getRedirectUri();

            if (code == null || code.isEmpty()) {
                logger.warn("Google login called without code");
                return ResponseEntity.badRequest().body(new ErrorResponse("Missing code"));
            }
            if (redirectUri == null || redirectUri.isEmpty()) {
                logger.warn("Google login called without redirectUri");
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
            logger.debug("Google token endpoint status: {}", tokenResponse.statusCode());
            logger.debug("Google token response body: {}", tokenResponse.body());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(tokenResponse.body());

            if (tokenResponse.statusCode() != 200 || !jsonNode.has("id_token")) {
                logger.warn("Failed to retrieve Google ID token. Status: {}, body: {}", tokenResponse.statusCode(), tokenResponse.body());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Failed to retrieve Google ID token"));
            }

            // 2. Verify ID token
            String idToken = jsonNode.get("id_token").asText();
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                logger.warn("GoogleIdToken verification returned null");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid Google token"));
            }

            // 3. Lấy thông tin user từ Google
            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            logger.info("Google login payload: email={}, name={}, pictureUrl={}", email, name, pictureUrl);

            // 4. Lấy user từ DB nếu có
            Optional<User> optUser = userRepository.findByEmail(email);
            User user;
            if (optUser.isPresent()) {
                user = optUser.get();
                logger.info("Found existing user in DB: id={}, avatar={}", user.getId(), user.getAvatar());
                // Nếu DB chưa có avatar thì gán pictureUrl (chỉ khi khác null)
                if ((user.getAvatar() == null || user.getAvatar().isBlank()) && pictureUrl != null && !pictureUrl.isBlank()) {
                    logger.info("Updating user.avatar for user id={} with pictureUrl", user.getId());
                    user.setAvatar(pictureUrl);
                    try {
                        userRepository.save(user);
                        logger.info("Updated user avatar saved for id={}", user.getId());
                    } catch (Exception e) {
                        logger.error("Failed to save updated avatar for user id={}: {}", user.getId(), e.getMessage(), e);
                    }
                }
            } else {
                // Tạo mới user
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setName(name != null ? name : email);
                newUser.setPassword(passwordEncoder.encode("google-user-" + System.currentTimeMillis()));
                newUser.setAvatar(pictureUrl); // có thể null
                try {
                    user = userRepository.save(newUser);
                    logger.info("Created new user id={} email={} avatar={}", user.getId(), user.getEmail(), user.getAvatar());
                } catch (Exception e) {
                    logger.error("Error saving new user for google login: {}", e.getMessage(), e);
                    // Nếu save lỗi do race condition -> lấy lại từ DB
                    user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Failed to create or retrieve user after exception"));
                }
            }

            // 5. Tạo JWT bằng JwtService
            String jwt = jwtService.generateToken(user.getEmail());
            logger.info("Google login success for email={}, returning jwt and user id={}", user.getEmail(), user.getId());

            return ResponseEntity.ok(
                    new LoginResponse(user.getId(), user.getName(), user.getEmail(), jwt, user.getAvatar())
            );

        } catch (Exception e) {
            logger.error("Google login failed: {}", e.getMessage(), e);
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

    /* ==================== DTOs ==================== */
    @Getter @Setter
    public static class CodeRequest {
        private String code;
        private String redirectUri;
    }
    @Getter @Setter
    public static class ResetPasswordRequest { private String email; }
    @Getter @Setter
    public static class VerifyOtpRequest { private String email; private String otpCode; }
    @Getter @Setter
    public static class ChangePasswordRequest { private String email; private String password; private String confirm; }
}