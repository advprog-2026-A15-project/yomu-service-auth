package id.ac.ui.cs.advprog.yomu.auth.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoogleSsoRequest {
    private String accessToken;
    // Real implementation would pass a Google id_token here.
    // For mock/development, we just accept user data.
}
