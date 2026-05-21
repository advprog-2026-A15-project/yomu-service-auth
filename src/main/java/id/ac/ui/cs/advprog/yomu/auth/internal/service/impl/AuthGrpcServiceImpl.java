package id.ac.ui.cs.advprog.yomu.auth.internal.service.impl;

import id.ac.ui.cs.advprog.yomu.auth.internal.model.User;
import id.ac.ui.cs.advprog.yomu.auth.internal.repository.UserRepository;
import id.ac.ui.cs.advprog.yomu.shared.grpc.*;
import id.ac.ui.cs.advprog.yomu.shared.security.JwtService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class AuthGrpcServiceImpl extends AuthServiceGrpcGrpc.AuthServiceGrpcImplBase {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public void validateToken(ValidateTokenRequest request, StreamObserver<ValidateTokenResponse> responseObserver) {
        String token = request.getToken();
        boolean isValid = jwtService.isAccessTokenValid(token);

        ValidateTokenResponse.Builder responseBuilder = ValidateTokenResponse.newBuilder()
                .setValid(isValid);

        if (isValid) {
            responseBuilder.setUserId(jwtService.extractUserId(token))
                    .setUsername(jwtService.extractUsername(token))
                    .setRole(jwtService.extractRole(token));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUserById(GetUserByIdRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            userRepository.findById(userId).ifPresentOrElse(
                    user -> {
                        UserResponse response = UserResponse.newBuilder()
                                .setId(user.getId().toString())
                                .setUsername(user.getUsername())
                                .setEmail(user.getEmail() != null ? user.getEmail() : "")
                                .setDisplayName(user.getDisplayName())
                                .setRole(user.getRole().name())
                                .build();
                        responseObserver.onNext(response);
                    },
                    () -> responseObserver.onError(new io.grpc.StatusRuntimeException(io.grpc.Status.NOT_FOUND))
            );
        } catch (IllegalArgumentException e) {
            responseObserver.onError(new io.grpc.StatusRuntimeException(io.grpc.Status.INVALID_ARGUMENT));
        }
        responseObserver.onCompleted();
    }
}
