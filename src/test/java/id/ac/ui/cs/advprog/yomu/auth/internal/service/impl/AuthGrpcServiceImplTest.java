package id.ac.ui.cs.advprog.yomu.auth.internal.service.impl;

import id.ac.ui.cs.advprog.yomu.auth.internal.model.AuthProvider;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.Role;
import id.ac.ui.cs.advprog.yomu.auth.internal.model.User;
import id.ac.ui.cs.advprog.yomu.auth.internal.repository.UserRepository;
import id.ac.ui.cs.advprog.yomu.shared.grpc.GetUserByIdRequest;
import id.ac.ui.cs.advprog.yomu.shared.grpc.ValidateTokenRequest;
import id.ac.ui.cs.advprog.yomu.shared.grpc.ValidateTokenResponse;
import id.ac.ui.cs.advprog.yomu.shared.security.JwtService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthGrpcServiceImplTest {

    private static final String TEST_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final UUID USER_ID = UUID.fromString("0114b813-a5ef-4c2b-a583-bf2900e4ceea");
    private static final String USERNAME = "testuser";

    @Mock
    private UserRepository userRepository;

    private JwtService jwtService;
    private AuthGrpcServiceImpl authGrpcService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86_400_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L);
        authGrpcService = new AuthGrpcServiceImpl(jwtService, userRepository);
    }

    @Test
    void validateToken_validAccessToken_returnsValidWithClaims() {
        String token = jwtService.generateAccessToken(USERNAME, tokenClaims());
        CapturingStreamObserver<ValidateTokenResponse> observer = new CapturingStreamObserver<>();

        authGrpcService.validateToken(
                ValidateTokenRequest.newBuilder().setToken(token).build(),
                observer);

        assertThat(observer.getValue().getValid()).isTrue();
        assertThat(observer.getValue().getUserId()).isEqualTo(USER_ID.toString());
        assertThat(observer.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(observer.getValue().getRole()).isEqualTo("PELAJAR");
        assertThat(observer.isCompleted()).isTrue();
    }

    @Test
    void validateToken_invalidToken_returnsInvalid() {
        CapturingStreamObserver<ValidateTokenResponse> observer = new CapturingStreamObserver<>();

        authGrpcService.validateToken(
                ValidateTokenRequest.newBuilder().setToken("not.a.jwt").build(),
                observer);

        assertThat(observer.getValue().getValid()).isFalse();
        assertThat(observer.getValue().getUserId()).isEmpty();
        assertThat(observer.isCompleted()).isTrue();
    }

    @Test
    void validateToken_refreshToken_returnsInvalid() {
        String refreshToken = jwtService.generateRefreshToken(USERNAME, tokenClaims());
        CapturingStreamObserver<ValidateTokenResponse> observer = new CapturingStreamObserver<>();

        authGrpcService.validateToken(
                ValidateTokenRequest.newBuilder().setToken(refreshToken).build(),
                observer);

        assertThat(observer.getValue().getValid()).isFalse();
    }

    @Test
    void getUserById_found_returnsUserResponse() {
        User user = User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email("user@test.com")
                .displayName("Test User")
                .role(Role.PELAJAR)
                .provider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        CapturingStreamObserver<id.ac.ui.cs.advprog.yomu.shared.grpc.UserResponse> observer =
                new CapturingStreamObserver<>();

        authGrpcService.getUserById(
                GetUserByIdRequest.newBuilder().setUserId(USER_ID.toString()).build(),
                observer);

        assertThat(observer.getValue().getId()).isEqualTo(USER_ID.toString());
        assertThat(observer.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(observer.getValue().getEmail()).isEqualTo("user@test.com");
        assertThat(observer.getValue().getRole()).isEqualTo("PELAJAR");
    }

    @Test
    void getUserById_notFound_emitsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        CapturingStreamObserver<id.ac.ui.cs.advprog.yomu.shared.grpc.UserResponse> observer =
                new CapturingStreamObserver<>();

        authGrpcService.getUserById(
                GetUserByIdRequest.newBuilder().setUserId(USER_ID.toString()).build(),
                observer);

        assertThat(observer.getError()).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.NOT_FOUND.getCode());
    }

    @Test
    void getUserById_invalidUuid_emitsInvalidArgument() {
        CapturingStreamObserver<id.ac.ui.cs.advprog.yomu.shared.grpc.UserResponse> observer =
                new CapturingStreamObserver<>();

        authGrpcService.getUserById(
                GetUserByIdRequest.newBuilder().setUserId("not-a-uuid").build(),
                observer);

        assertThat(observer.getError()).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    private static Map<String, Object> tokenClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", USER_ID.toString());
        claims.put("role", "PELAJAR");
        return claims;
    }

    private static class CapturingStreamObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        T getValue() {
            return value;
        }

        Throwable getError() {
            return error;
        }

        boolean isCompleted() {
            return completed;
        }
    }
}
