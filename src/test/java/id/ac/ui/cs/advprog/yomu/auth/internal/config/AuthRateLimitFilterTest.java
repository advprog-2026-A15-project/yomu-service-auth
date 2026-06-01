package id.ac.ui.cs.advprog.yomu.auth.internal.config;

import id.ac.ui.cs.advprog.yomu.auth.internal.monitoring.AuthMetrics;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthRateLimitFilterTest {

    private AuthRateLimitFilter filter;
    private AuthMetrics authMetrics;

    @BeforeEach
    void setUp() {
        authMetrics = mock(AuthMetrics.class);
        filter = new AuthRateLimitFilter(authMetrics);
    }

    @Test
    void nonAuthEndpoint_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void loginUnderLimit_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void postToNonAuthEndpoint_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/data/something");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getToAuthEndpoint_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void registerEndpoint_isRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr("10.0.0.1");
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 21; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            if (i == 20) {
                assertThat(response.getStatus()).isEqualTo(429);
            }
        }
    }

    @Test
    void googleEndpoint_isRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/google");
        request.setRemoteAddr("10.0.0.2");
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 21; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            if (i == 20) {
                assertThat(response.getStatus()).isEqualTo(429);
            }
        }
    }

    @Test
    void refreshEndpoint_isRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setRemoteAddr("10.0.0.3");
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 21; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            if (i == 20) {
                assertThat(response.getStatus()).isEqualTo(429);
            }
        }
    }

    @Test
    void xForwardedForHeader_usedAsClientIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("X-Forwarded-For", "192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void xForwardedForMultipleIps_usesFirst() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 172.16.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void loginExceedsLimit_returns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.99");
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 21; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            if (i < 20) {
                assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            } else {
                assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
                assertThat(response.getContentAsString()).contains("Terlalu banyak");
                verify(authMetrics).recordRateLimitHit();
            }
        }
    }
}
