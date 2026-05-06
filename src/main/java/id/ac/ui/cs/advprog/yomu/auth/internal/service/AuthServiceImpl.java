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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional("authTransactionManager")
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
        
        // Publish event for Modulith
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getUsername(), user.getEmail(), Instant.now()));

        String jwtToken = generateTokenForUser(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .user(mapToDto(user))
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.getIdentifier())
                .orElseThrow(() -> new IllegalArgumentException("Kredensial tidak valid"));

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new IllegalArgumentException("Harap login menggunakan " + user.getProvider().name());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Kredensial tidak valid");
        }

        String jwtToken = generateTokenForUser(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .user(mapToDto(user))
                .build();
    }

    @Override
    @Transactional("authTransactionManager")
    public AuthResponse googleSsoLogin(GoogleSsoRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.getEmail())
                .orElseGet(() -> {
                    // Create new user if not exists
                    User newUser = User.builder()
                            .id(UUID.randomUUID())
                            .username(request.getUsername() != null ? request.getUsername() : request.getEmail().split("@")[0])
                            .email(request.getEmail())
                            .displayName(request.getDisplayName())
                            .role(Role.PELAJAR)
                            .provider(AuthProvider.GOOGLE)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    userRepository.save(newUser);
                    eventPublisher.publishEvent(new UserRegisteredEvent(newUser.getId(), newUser.getUsername(), newUser.getEmail(), Instant.now()));
                    return newUser;
                });

        String jwtToken = generateTokenForUser(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .user(mapToDto(user))
                .build();
    }

    @Override
    @Transactional("authTransactionManager")
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
        return AuthResponse.builder()
                .token(generateTokenForUser(user))
                .user(mapToDto(user))
                .build();
    }

    @Override
    @Transactional("authTransactionManager")
    public void deleteAccount(UUID userId) {
        userRepository.deleteById(userId);
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
        return jwtService.generateToken(user.getUsername(), extraClaims);
    }
}
