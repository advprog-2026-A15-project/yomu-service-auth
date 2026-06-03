package id.ac.ui.cs.advprog.yomu.auth.internal.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AuthMetrics {

    private static final String OUTCOME = "outcome";

    private final MeterRegistry meterRegistry;

    private final Counter rateLimitHitsCounter;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.rateLimitHitsCounter = Counter.builder("yomu_auth_rate_limit_hits_total")
                .description("Total number of requests blocked by rate limiting")
                .register(meterRegistry);
    }

    public void recordLogin(String outcome, String provider) {
        Counter.builder("yomu_auth_login_total")
                .description("Total number of login attempts")
                .tag(OUTCOME, outcome)
                .tag("provider", provider)
                .register(meterRegistry)
                .increment();
    }

    public void recordRegister(String outcome) {
        Counter.builder("yomu_auth_register_total")
                .description("Total number of registration attempts")
                .tag(OUTCOME, outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordTokenRefresh(String outcome) {
        Counter.builder("yomu_auth_token_refresh_total")
                .description("Total number of token refresh attempts")
                .tag(OUTCOME, outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordRateLimitHit() {
        rateLimitHitsCounter.increment();
    }

    public void recordPasswordHashing(long durationNanos) {
        Timer.builder("yomu_auth_password_hashing_duration_seconds")
                .description("Time taken to hash passwords using BCrypt")
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
