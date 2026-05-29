package id.ac.ui.cs.advprog.yomu.auth.internal.service.impl;

import id.ac.ui.cs.advprog.yomu.auth.internal.dto.AuthResponse;
import id.ac.ui.cs.advprog.yomu.auth.internal.dto.GoogleSsoRequest;
import id.ac.ui.cs.advprog.yomu.auth.internal.dto.LoginRequest;
import id.ac.ui.cs.advprog.yomu.auth.internal.dto.RefreshTokenRequest;
import id.ac.ui.cs.advprog.yomu.auth.internal.dto.RegisterRequest;
import id.ac.ui.cs.advprog.yomu.auth.internal.dto.UpdateProfileRequest;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.AuthProvider;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.Role;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.User;
import id.ac.ui.cs.advprog.yomu.auth.internal.monitoring.AuthMetrics;
import id.ac.ui.cs.advprog.yomu.auth.internal.repository.UserRepository;
import id.ac.ui.cs.advprog.yomu.auth.internal.service.GoogleSsoService;
import id.ac.ui.cs.advprog.yomu.shared.event.UserRegisteredEvent;
import id.ac.ui.cs.advprog.yomu.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final String TEST_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final UUID USER_ID = UUID.fromString("0114b813-a5ef-4c2b-a583-bf2900e4ceea");
    private static final String USERNAME = "testuser";
    private static final String EMAIL = "user@test.com";
    private static final String PASSWORD = "Test1234!";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private GoogleSsoService googleSsoService;

    private JwtService jwtService;
    private AuthMetrics authMetrics;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86_400_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L);
        authMetrics = new AuthMetrics(new SimpleMeterRegistry());

        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtService, rabbitTemplate, googleSsoService, authMetrics);
        ReflectionTestUtils.setField(authService, "adminEmails", "admin@test.com");

        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded-" + invocation.getArgument(0));
    }

    // ─── register ───

    @Test
    void register_success_returnsTokensAndUser() {
        RegisterRequest request = registerRequest(EMAIL);
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.getUser().getUsername()).isEqualTo(USERNAME);
        assertThat(response.getUser().getEmail()).isEqualTo(EMAIL);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateUsername_throws() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest(EMAIL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username sudah terdaftar");
    }

    @Test
    void register_duplicateEmail_throws() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest(EMAIL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email sudah terdaftar");
    }

    @Test
    void register_adminEmail_assignsAdminRole() {
        RegisterRequest request = registerRequest("admin@test.com");
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        authService.register(request);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void register_publishesUserRegisteredEvent() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        authService.register(registerRequest(EMAIL));

        verify(rabbitTemplate).convertAndSend(eq("yomu.user.registered"), any(UserRegisteredEvent.class));
    }

    @Test
    void register_rabbitFailure_stillReturnsResponse() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        doThrow(new RuntimeException("mq down"))
                .when(rabbitTemplate).convertAndSend(eq("yomu.user.registered"), any(UserRegisteredEvent.class));

        AuthResponse response = authService.register(registerRequest(EMAIL));

        assertThat(response.getToken()).isNotBlank();
    }

    // ─── login ───

    @Test
    void login_success_localUser_returnsTokens() {
        User user = localUser(EMAIL, "encoded-" + PASSWORD);
        when(userRepository.findByIdentifier(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setIdentifier(USERNAME);
        request.setPassword(PASSWORD);

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isNotBlank();
        assertThat(jwtService.isAccessTokenValid(response.getToken())).isTrue();
    }

    @Test
    void login_wrongPassword_throws() {
        User user = localUser(EMAIL, "encoded-wrong");
        when(userRepository.findByIdentifier(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setIdentifier(USERNAME);
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kredensial tidak valid");
    }

    @Test
    void login_userNotFound_throws() {
        when(userRepository.findByIdentifier(USERNAME)).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setIdentifier(USERNAME);
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kredensial tidak valid");
    }

    @Test
    void login_googleProvider_throws() {
        User user = User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email(EMAIL)
                .displayName("Test")
                .password("encoded-" + PASSWORD)
                .role(Role.PELAJAR)
                .provider(AuthProvider.GOOGLE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(userRepository.findByIdentifier(EMAIL)).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setIdentifier(EMAIL);
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GOOGLE");
    }

    @Test
    void login_adminEmail_promotesToAdmin() {
        User user = localUser("admin@test.com", "encoded-" + PASSWORD);
        when(userRepository.findByIdentifier(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setIdentifier(USERNAME);
        request.setPassword(PASSWORD);

        authService.login(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    // ─── refreshToken ───

    @Test
    void refreshToken_valid_returnsNewTokens() {
        User user = localUser(EMAIL, "encoded-" + PASSWORD);
        String refreshToken = jwtService.generateRefreshToken(USERNAME, tokenClaims(user));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        AuthResponse response = authService.refreshToken(request);

        assertThat(response.getToken()).isNotBlank();
        assertThat(jwtService.isAccessTokenValid(response.getToken())).isTrue();
    }

    @Test
    void refreshToken_invalid_throws() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("not.a.valid.token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refresh token tidak valid");
    }

    @Test
    void refreshToken_userNotFound_throws() {
        User user = localUser(EMAIL, "encoded-" + PASSWORD);
        String refreshToken = jwtService.generateRefreshToken(USERNAME, tokenClaims(user));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User tidak ditemukan");
    }

    // ─── googleSsoLogin ───

    @Test
    void googleSsoLogin_newUser_createsUserAndPublishesEvent() {
        when(googleSsoService.verifyToken("google-token")).thenReturn(Map.of(
                "email", "newgoogle@test.com",
                "name", "Google User"));
        when(userRepository.findByIdentifier("newgoogle@test.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("newgoogle")).thenReturn(false);

        GoogleSsoRequest request = new GoogleSsoRequest();
        request.setAccessToken("google-token");

        AuthResponse response = authService.googleSsoLogin(request);

        assertThat(response.getToken()).isNotBlank();
        verify(userRepository).save(any(User.class));
        verify(rabbitTemplate).convertAndSend(eq("yomu.user.registered"), any(UserRegisteredEvent.class));
    }

    @Test
    void googleSsoLogin_existingUser_returnsTokens() {
        User existing = User.builder()
                .id(USER_ID)
                .username("googleuser")
                .email("existing@test.com")
                .displayName("Existing")
                .role(Role.PELAJAR)
                .provider(AuthProvider.GOOGLE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(googleSsoService.verifyToken("google-token")).thenReturn(Map.of(
                "email", "existing@test.com",
                "name", "Existing"));
        when(userRepository.findByIdentifier("existing@test.com")).thenReturn(Optional.of(existing));

        GoogleSsoRequest request = new GoogleSsoRequest();
        request.setAccessToken("google-token");

        AuthResponse response = authService.googleSsoLogin(request);

        assertThat(response.getToken()).isNotBlank();
        verify(userRepository, never()).save(any());
    }

    @Test
    void googleSsoLogin_duplicateUsername_generatesSuffix() {
        when(googleSsoService.verifyToken("google-token")).thenReturn(Map.of(
                "email", "dup@test.com",
                "name", "Dup User"));
        when(userRepository.findByIdentifier("dup@test.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("dup")).thenReturn(true);
        when(userRepository.existsByUsername("dup2")).thenReturn(false);

        GoogleSsoRequest request = new GoogleSsoRequest();
        request.setAccessToken("google-token");

        authService.googleSsoLogin(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("dup2");
    }

    // ─── profile / delete ───

    @Test
    void updateProfile_success_updatesFields() {
        User user = localUser(EMAIL, "encoded-" + PASSWORD);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("newname")).thenReturn(false);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("newname");

        AuthResponse response = authService.updateProfile(USER_ID, request);

        assertThat(response.getUser().getUsername()).isEqualTo("newname");
        verify(userRepository).update(any(User.class));
    }

    @Test
    void updateProfile_duplicateUsername_throws() {
        User user = localUser(EMAIL, "encoded-" + PASSWORD);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("taken");

        assertThatThrownBy(() -> authService.updateProfile(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username sudah terdaftar");
    }

    @Test
    void updateProfile_updateAllFields_success() {
        User user = localUser(EMAIL, "encoded-" + PASSWORD);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("new@example.com");
        request.setPhone("987654321");
        request.setDisplayName("New Name");
        request.setPassword("newpass");

        AuthResponse response = authService.updateProfile(USER_ID, request);

        assertThat(response.getUser().getEmail()).isEqualTo("new@example.com");
        assertThat(response.getUser().getPhone()).isEqualTo("987654321");
        assertThat(response.getUser().getDisplayName()).isEqualTo("New Name");
        verify(userRepository).update(any(User.class));
        verify(passwordEncoder).encode("newpass");
    }

    @Test
    void updateProfile_duplicateEmail_throws() {
        User user = localUser(EMAIL, "encoded-" + PASSWORD);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("taken@example.com");

        assertThatThrownBy(() -> authService.updateProfile(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email sudah terdaftar");
    }

    @Test
    void updateProfile_userNotFound_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest();

        assertThatThrownBy(() -> authService.updateProfile(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User tidak ditemukan");
    }

    @Test
    void deleteAccount_deletesById() {
        authService.deleteAccount(USER_ID);

        verify(userRepository).deleteById(USER_ID);
    }

    @Test
    void buildAuthResponse_accessTokenValidPerPhase1() {
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        AuthResponse response = authService.register(registerRequest(EMAIL));

        assertThat(jwtService.isAccessTokenValid(response.getToken())).isTrue();
        assertThat(jwtService.extractUserId(response.getToken())).isNotBlank();
    }

    private static RegisterRequest registerRequest(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(USERNAME);
        request.setEmail(email);
        request.setDisplayName("Test User");
        request.setPassword(PASSWORD);
        return request;
    }

    private static User localUser(String email, String encodedPassword) {
        return User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email(email)
                .displayName("Test User")
                .password(encodedPassword)
                .role(Role.PELAJAR)
                .provider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static Map<String, Object> tokenClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId().toString());
        claims.put("role", user.getRole().name());
        return claims;
    }
}
