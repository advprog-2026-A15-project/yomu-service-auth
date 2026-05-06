package id.ac.ui.cs.advprog.yomu.auth.internal.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String username;
    private String email;
    private String phone;
    private String displayName;
    private String password; // Optional: only if user wants to change
}
