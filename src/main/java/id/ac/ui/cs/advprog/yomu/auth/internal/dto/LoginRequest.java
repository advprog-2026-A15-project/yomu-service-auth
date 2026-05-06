package id.ac.ui.cs.advprog.yomu.auth.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Identifier (username/email) tidak boleh kosong")
    private String identifier;

    @NotBlank(message = "Password tidak boleh kosong")
    private String password;
}
