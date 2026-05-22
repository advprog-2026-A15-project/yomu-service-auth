package id.ac.ui.cs.advprog.yomu.auth;

import id.ac.ui.cs.advprog.yomu.auth.internal.model.User;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.Role;
import id.ac.ui.cs.advprog.yomu.auth.internal.repository.UserRepository;
import id.ac.ui.cs.advprog.yomu.shared.dto.UserDto;
import id.ac.ui.cs.advprog.yomu.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFacadeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthFacade authFacade;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .phone("123456")
                .displayName("Test User")
                .role(Role.PELAJAR)
                .build();
    }

    @Test
    void testGetUserById_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Optional<UserDto> result = authFacade.getUserById(userId);

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        assertEquals("PELAJAR", result.get().getRole());
    }

    @Test
    void testGetUserById_NotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<UserDto> result = authFacade.getUserById(userId);

        assertFalse(result.isPresent());
    }

    @Test
    void testIsTokenValid_Success() {
        String token = "valid.token.here";
        when(jwtService.extractUsername(token)).thenReturn("testuser");
        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(token)).thenReturn(true);

        assertTrue(authFacade.isTokenValid(token));
    }

    @Test
    void testIsTokenValid_UserNotFound() {
        String token = "valid.token.here";
        when(jwtService.extractUsername(token)).thenReturn("notfound");
        when(userRepository.findByIdentifier("notfound")).thenReturn(Optional.empty());

        assertFalse(authFacade.isTokenValid(token));
    }

    @Test
    void testIsTokenValid_InvalidToken() {
        String token = "invalid.token.here";
        when(jwtService.extractUsername(token)).thenReturn("testuser");
        when(userRepository.findByIdentifier("testuser")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(token)).thenReturn(false);

        assertFalse(authFacade.isTokenValid(token));
    }

    @Test
    void testIsTokenValid_Exception() {
        String token = "bad.token";
        when(jwtService.extractUsername(token)).thenThrow(new RuntimeException("Bad token"));

        assertFalse(authFacade.isTokenValid(token));
    }
}
