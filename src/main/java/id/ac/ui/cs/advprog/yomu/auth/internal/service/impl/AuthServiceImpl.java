package id.ac.ui.cs.advprog.yomu.auth.internal.service.impl;

import id.ac.ui.cs.advprog.yomu.auth.internal.dto.*;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.AuthProvider;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.Role;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.User;
import id.ac.ui.cs.advprog.yomu.auth.internal.repository.UserRepository;
import id.ac.ui.cs.advprog.yomu.auth.internal.service.AuthService;
import id.ac.ui.cs.advprog.yomu.auth.internal.service.GoogleSsoService;
import id.ac.ui.cs.advprog.yomu.shared.dto.UserDto;
import id.ac.ui.cs.advprog.yomu.shared.event.UserRegisteredEvent;
import id.ac.ui.cs.advprog.yomu.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final java.util.Set<String> ADMIN_EMAILS = java.util.Set.of(
            "christna.yosua@ui.ac.id",
            "tirta.rendy@ui.ac.id",
            "nathanael.leander@ui.ac.id",
            "m.adella@ui.ac.id"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RabbitTemplate rabbitTemplate;
    private final GoogleSsoService googleSsoService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateNewUser(request.getUsername(), request.getEmail());

        User user = User.builder()
                .id(UUID.randomUUID())
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .displayName(request.getDisplayName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.PELAJAR)
                .provider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        publishUserRegisteredEvent(user);

        log.info("Manual registration succeeded for userId={}", user.getId());
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

        log.info("Manual login succeeded for userId={}", user.getId());
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse googleSsoLogin(GoogleSsoRequest request) {
        Map<String, String> userInfo = googleSsoService.verifyToken(request.getAccessToken());
        String email = userInfo.get("email");
        String name = userInfo.get("name");

        boolean isAdmin = ADMIN_EMAILS.stream().anyMatch(e -> e.equalsIgnoreCase(email));
        Role defaultRole = isAdmin ? Role.ADMIN : Role.PELAJAR;

        User user = userRepository.findByIdentifier(email)
                .map(existingUser -> handleExistingGoogleUser(existingUser, isAdmin))
                .orElseGet(() -> createNewGoogleUser(email, name, defaultRole));

        log.info("Google SSO login succeeded for userId={}", user.getId());
        return buildAuthResponse(user);
    }

    private User handleExistingGoogleUser(User existingUser, boolean shouldBeAdmin) {
        if (shouldBeAdmin && existingUser.getRole() != Role.ADMIN) {
            existingUser.setRole(Role.ADMIN);
            userRepository.update(existingUser);
        }
        return existingUser;
    }

    private User createNewGoogleUser(String email, String name, Role role) {
        User newUser = User.builder()
                .id(UUID.randomUUID())
                .username(email.split("@")[0])
                .email(email)
                .displayName(name != null ? name : "Google User")
                .role(role)
                .provider(AuthProvider.GOOGLE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(newUser);
        publishUserRegisteredEvent(newUser);
        return newUser;
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

        updateUserDetails(user, request);
        userRepository.update(user);
        
        log.info("Profile updated for userId={}", user.getId());
        return buildAuthResponse(user);
    }

    private void updateUserDetails(User user, UpdateProfileRequest request) {
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

        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        userRepository.deleteById(userId);
        log.info("Account deleted for userId={}", userId);
    }

    private void validateNewUser(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username sudah terdaftar");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email sudah terdaftar");
        }
    }

    private void publishUserRegisteredEvent(User user) {
        try {
            rabbitTemplate.convertAndSend("yomu.user.registered", 
                new UserRegisteredEvent(user.getId(), user.getUsername(), user.getEmail(), Instant.now()));
        } catch (Exception e) {
            log.warn("Failed to publish UserRegisteredEvent for userId={}: {}", user.getId(), e.getMessage());
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String jwtToken = generateToken(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .refreshToken(generateRefreshToken(user))
                .expiresAt(jwtService.extractExpirationInstant(jwtToken))
                .user(mapToDto(user))
                .build();
    }

    private String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId().toString());
        claims.put("role", user.getRole().name());
        return jwtService.generateAccessToken(user.getUsername(), claims);
    }

    private String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId().toString());
        claims.put("role", user.getRole().name());
        return jwtService.generateRefreshToken(user.getUsername(), claims);
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
}
