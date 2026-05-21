package id.ac.ui.cs.advprog.yomu.auth.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token tidak boleh kosong")
    private String refreshToken;
}
