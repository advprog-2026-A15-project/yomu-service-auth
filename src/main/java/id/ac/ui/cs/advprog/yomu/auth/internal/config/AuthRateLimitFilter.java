package id.ac.ui.cs.advprog.yomu.auth.internal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_SECONDS = 60;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isSensitiveAuthEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRequestURI() + ":" + clientIp(request);
        WindowCounter counter = counters.compute(key, (ignored, current) -> {
            long now = Instant.now().getEpochSecond();
            if (current == null || now - current.windowStartedAt() >= WINDOW_SECONDS) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(current.windowStartedAt(), current.count() + 1);
        });

        if (counter.count() > MAX_REQUESTS_PER_WINDOW) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Terlalu banyak request. Coba lagi nanti.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSensitiveAuthEndpoint(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/auth/login".equals(path)
            || "/api/auth/register".equals(path)
            || "/api/auth/google".equals(path)
            || "/api/auth/refresh".equals(path);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record WindowCounter(long windowStartedAt, int count) {
    }
}
