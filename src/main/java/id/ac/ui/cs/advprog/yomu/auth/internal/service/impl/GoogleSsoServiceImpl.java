package id.ac.ui.cs.advprog.yomu.auth.internal.service.impl;

import id.ac.ui.cs.advprog.yomu.auth.internal.service.GoogleSsoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class GoogleSsoServiceImpl implements GoogleSsoService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, String> verifyToken(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> payload = response.getBody();
            if (payload != null && payload.containsKey("email")) {
                Map<String, String> result = new HashMap<>();
                result.put("email", (String) payload.get("email"));
                result.put("name", (String) payload.get("name"));
                return result;
            } else {
                throw new IllegalArgumentException("Token Google tidak valid atau tidak memuat email");
            }
        } catch (Exception e) {
            log.error("Gagal memverifikasi token Google", e);
            throw new IllegalArgumentException("Gagal memverifikasi akun Google Anda");
        }
    }
}
