package id.ac.ui.cs.advprog.yomu.auth.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Username tidak boleh kosong")
    private String username;

    @Email(message = "Format email tidak valid")
    private String email;

    private String phone;

    @NotBlank(message = "Display name tidak boleh kosong")
    private String displayName;

    @NotBlank(message = "Password tidak boleh kosong")
    private String password;
}
