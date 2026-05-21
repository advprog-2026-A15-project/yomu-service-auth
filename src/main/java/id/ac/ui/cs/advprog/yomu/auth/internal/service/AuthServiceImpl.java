package id.ac.ui.cs.advprog.yomu.auth.internal.service;

import id.ac.ui.cs.advprog.yomu.shared.dto.UserDto;
import id.ac.ui.cs.advprog.yomu.shared.event.UserRegisteredEvent;
import id.ac.ui.cs.advprog.yomu.shared.security.JwtService;
import id.ac.ui.cs.advprog.yomu.auth.internal.dto.*;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.AuthProvider;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.Role;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.User;
import id.ac.ui.cs.advprog.yomu.auth.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final java.util.Set<String> ADMIN_EMAILS = java.util.Set.of(
            "christna.yosua@ui.ac.id",
            "tirta.rendy@ui.ac.id",
            "nathanael.leander@ui.ac.id"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username sudah terdaftar");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email sudah terdaftar");
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .displayName(request.getDisplayName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.PELAJAR) // Default role
                .provider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        try {
            rabbitTemplate.convertAndSend("yomu.user.registered", new UserRegisteredEvent(user.getId(), user.getUsername(), user.getEmail(), Instant.now()));
        } catch (Exception e) {
            logger.warn("Failed to publish UserRegisteredEvent for userId={}: {}", user.getId(), e.getMessage());
        }

        logger.info("Manual registration succeeded for userId={}", user.getId());
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByIdentifier(request.getIdentifier())
                .orElseThrow(() -> new IllegalArgumentException("Kredensial tidak valid"));

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new IllegalArgumentException("Harap login menggunakan " + user.getProvider().name());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Kredensial tidak valid");
        }

        logger.info("Manual login succeeded for userId={}", user.getId());
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse googleSsoLogin(GoogleSsoRequest request) {
        if (request.getAccessToken() == null || request.getAccessToken().isEmpty()) {
            throw new IllegalArgumentException("Access token Google tidak boleh kosong");
        }

        // Verify with Google UserInfo API
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(request.getAccessToken());
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>("parameters", headers);

        String email = null;
        String name = null;

        try {
            org.springframework.http.ResponseEntity<java.util.Map> response = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    java.util.Map.class
            );

            java.util.Map<String, Object> payload = response.getBody();
            if (payload != null && payload.containsKey("email")) {
                email = (String) payload.get("email");
                name = (String) payload.get("name");
            } else {
                throw new IllegalArgumentException("Token Google tidak valid atau tidak memuat email");
            }
        } catch (Exception e) {
            logger.error("Gagal memverifikasi token Google", e);
            throw new IllegalArgumentException("Gagal memverifikasi akun Google Anda");
        }

        String finalEmail = email;
        String finalName = name;

        boolean isAdmin = ADMIN_EMAILS.stream().anyMatch(e -> e.equalsIgnoreCase(finalEmail));
        Role defaultRole = isAdmin ? Role.ADMIN : Role.PELAJAR;
        User user = userRepository.findByIdentifier(finalEmail)
                .map(existingUser -> {
                    if (isAdmin && existingUser.getRole() != Role.ADMIN) {
                        existingUser.setRole(Role.ADMIN);
                        userRepository.update(existingUser);
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    // Create new user if not exists
                    User newUser = User.builder()
                            .id(UUID.randomUUID())
                            .username(finalEmail.split("@")[0])
                            .email(finalEmail)
                            .displayName(finalName != null ? finalName : "Google User")
                            .role(defaultRole)
                            .provider(AuthProvider.GOOGLE)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    userRepository.save(newUser);
                    rabbitTemplate.convertAndSend("yomu.user.registered", new UserRegisteredEvent(newUser.getId(), newUser.getUsername(), newUser.getEmail(), Instant.now()));
                    return newUser;
                });

        logger.info("Google SSO login succeeded for userId={}", user.getId());
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtService.isRefreshTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Refresh token tidak valid");
        }

        UUID userId = UUID.fromString(jwtService.extractUserId(refreshToken));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan"));

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan"));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Username sudah terdaftar");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email sudah terdaftar");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.update(user);
        logger.info("Profile updated for userId={}", user.getId());
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        userRepository.deleteById(userId);
        logger.info("Account deleted for userId={}", userId);
    }

    private UserDto mapToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .build();
    }

    private String generateTokenForUser(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", user.getId().toString());
        extraClaims.put("role", user.getRole().name());
        return jwtService.generateAccessToken(user.getUsername(), extraClaims);
    }

    private String generateRefreshTokenForUser(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", user.getId().toString());
        extraClaims.put("role", user.getRole().name());
        return jwtService.generateRefreshToken(user.getUsername(), extraClaims);
    }

    private AuthResponse buildAuthResponse(User user) {
        String jwtToken = generateTokenForUser(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .refreshToken(generateRefreshTokenForUser(user))
                .expiresAt(jwtService.extractExpirationInstant(jwtToken))
                .user(mapToDto(user))
                .build();
    }
}
