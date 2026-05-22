# Auth Monitoring

## Scope

This monitoring change instruments `service-auth` with custom Micrometer
metrics in addition to the default Spring Boot Actuator metrics already exposed
at `/actuator/prometheus`.

## Design Justification

The service uses Spring Actuator, Micrometer, Prometheus, and Grafana to maintain
consistency with the rest of the Yomu microservices stack. Monitoring is focused
on security events (login, register, rate-limiting) which are critical for an
Authentication service.

Custom metrics are low-cardinality to prevent Prometheus series explosion.
Tags are used for outcomes (`success`, `failure`) and providers (`local`, `google`).

## Metrics

| Metric | Type | Labels | Purpose |
| :-- | :-- | :-- | :-- |
| `yomu_auth_login_total` | Counter | `outcome`, `provider` | Counts login attempts and results. |
| `yomu_auth_register_total` | Counter | `outcome` | Counts user registration attempts. |
| `yomu_auth_token_refresh_total` | Counter | `outcome` | Tracks JWT refresh token usage. |
| `yomu_auth_rate_limit_hits_total` | Counter | - | Counts requests blocked by the rate limiter. |
| `yomu_auth_password_hashing_duration_seconds` | Timer | - | Measures CPU time spent on BCrypt hashing. |

## Example Usage

Check Prometheus endpoint:

```powershell
Invoke-WebRequest http://localhost:8081/actuator/prometheus
```

PromQL examples:

```promql
# Login failure rate
rate(yomu_auth_login_total{outcome="failure"}[5m])
```

```promql
# Rate limit hits in the last hour
increase(yomu_auth_rate_limit_hits_total[1h])
```

```promql
# Average password hashing time
rate(yomu_auth_password_hashing_duration_seconds_sum[5m]) / rate(yomu_auth_password_hashing_duration_seconds_count[5m])
```

## Expected Operational Signals

- A spike in `yomu_auth_login_total{outcome="failure"}` might indicate a brute-force attack or a credential stuffing attempt.
- High `yomu_auth_rate_limit_hits_total` suggests that the rate limits might be too tight or a specific IP is spamming.
- Increases in `yomu_auth_password_hashing_duration_seconds` could indicate CPU contention or a change in BCrypt cost factor.
- A sudden drop in `yomu_auth_register_total` could mean issues with the database or the downstream `UserRegisteredEvent` publication.

## SLI And SLA

| Area | SLI | SLA target |
| :-- | :-- | :-- |
| Availability | Uptime of `/actuator/health`. | `>= 99.9%` |
| Login Success | Ratio of successful logins to total attempts (excluding invalid credentials). | `>= 95%` |
| Response Latency | p95 of `/api/auth/login`. | `< 800ms` (high due to BCrypt) |
