package id.ac.ui.cs.advprog.yomu.auth.internal.service;

import id.ac.ui.cs.advprog.yomu.auth.internal.dto.*;

import java.util.UUID;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse googleSsoLogin(GoogleSsoRequest request);
    AuthResponse updateProfile(UUID userId, UpdateProfileRequest request);
    void deleteAccount(UUID userId);
}
