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
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");
    private static final String JWT_SECRET = "your-secure-secret-key-32-chars-long-min";

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

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Failed login attempt for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid email or password"));
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
        userRepository.save(user);

        return ResponseEntity.ok("Signup successful");
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> handleGoogleLogin(@RequestBody CodeRequest codeRequest) {
        try {
            logger.info("Received Google OAuth code from frontend");

            String code = codeRequest.getCode();
            if (code == null || code.isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Missing code"));
            }

            String tokenEndpoint = "https://oauth2.googleapis.com/token";
            String redirectUri = "http://localhost:5173/google-callback";

            String body = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(googleClientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(googleClientSecret, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(tokenResponse.body());

            if (!jsonNode.has("id_token")) {
                logger.error("Failed to retrieve ID token from Google: {}", jsonNode);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Failed to retrieve Google ID token"));
            }

            String idToken = jsonNode.get("id_token").asText();

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                logger.warn("Invalid Google ID token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid Google token"));
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                try {
                    user = new User();
                    user.setEmail(email);
                    user.setName(name);
                    user.setPassword(passwordEncoder.encode("google-user-" + System.currentTimeMillis()));
                    user = userRepository.save(user);
                    logger.info("Created new user via Google: {}", email);
                } catch (Exception e) {
                    logger.warn("Duplicate insert? Retrying load: {}", e.getMessage());
                    user = userRepository.findByEmail(email).orElseThrow();
                }
            }

            String jwt = Jwts.builder()
                    .subject(user.getEmail())
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 86400000))
                    .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                    .compact();

            logger.info("Generated JWT for Google login user: {}", email);
            return ResponseEntity.ok(new LoginResponse(user.getId(), user.getName(), user.getEmail(), jwt));

        } catch (Exception e) {
            logger.error("Error during Google login: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Google login failed"));
        }
    }

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

    @Getter @Setter
    public static class CodeRequest {
        private String code;
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
}