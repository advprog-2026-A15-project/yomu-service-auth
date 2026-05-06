package id.ac.ui.cs.advprog.yomu.auth.internal.dto;

import lombok.Data;

@Data
public class GoogleSsoRequest {
    private String email;
    private String username;
    private String displayName;
    // Real implementation would pass a Google id_token here.
    // For mock/development, we just accept user data.
}
