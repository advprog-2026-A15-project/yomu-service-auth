package id.ac.ui.cs.advprog.yomu.auth.internal.service.impl;

import id.ac.ui.cs.advprog.yomu.auth.internal.service.GoogleSsoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class GoogleSsoServiceImpl implements GoogleSsoService {

    private static final String EMAIL = "email";

    private final RestTemplate restTemplate;

    @Autowired
    public GoogleSsoServiceImpl() {
        this(new RestTemplate());
    }

    GoogleSsoServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Map<String, String> verifyToken(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> payload = response.getBody();
            if (payload != null && payload.containsKey(EMAIL)) {
                Map<String, String> result = new HashMap<>();
                result.put(EMAIL, (String) payload.get(EMAIL));
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
