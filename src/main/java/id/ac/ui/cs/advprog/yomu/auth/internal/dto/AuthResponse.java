package id.ac.ui.cs.advprog.yomu.auth.internal.dto;

import id.ac.ui.cs.advprog.yomu.shared.dto.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private Instant expiresAt;
    private UserDto user;
}
