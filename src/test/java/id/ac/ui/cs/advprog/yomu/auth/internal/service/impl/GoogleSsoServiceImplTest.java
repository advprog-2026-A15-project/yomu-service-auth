package id.ac.ui.cs.advprog.yomu.auth.internal.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleSsoServiceImplTest {

    private RestTemplate restTemplate;
    private GoogleSsoServiceImpl service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new GoogleSsoServiceImpl(restTemplate);
    }

    @Test
    void verifyToken_withValidPayload_returnsEmailAndName() {
        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v3/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
            "email", "user@test.com",
            "name", "Test User"
        )));

        Map<String, String> result = service.verifyToken("google-access-token");

        assertEquals("user@test.com", result.get("email"));
        assertEquals("Test User", result.get("name"));
    }

    @Test
    void verifyToken_withoutEmail_throwsIllegalArgumentException() {
        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v3/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("name", "No Email")));

        assertThrows(IllegalArgumentException.class, () -> service.verifyToken("token"));
    }

    @Test
    void verifyToken_whenRestTemplateFails_throwsIllegalArgumentException() {
        when(restTemplate.exchange(
            eq("https://www.googleapis.com/oauth2/v3/userinfo"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenThrow(new RestClientException("network error"));

        assertThrows(IllegalArgumentException.class, () -> service.verifyToken("token"));
    }
}
