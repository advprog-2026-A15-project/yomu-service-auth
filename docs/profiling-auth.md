# Auth Profiling Evidence

## Profiling Run

Date: May 22, 2026

Target: `service-auth` running locally on port `8081` using the `bootJar` artifact.

Workload exercised:
- `POST /api/auth/register` (Multiple users)
- `POST /api/auth/login` (Repeated attempts)
- `POST /api/auth/refresh`
- `GET /actuator/prometheus`

## Evidence Files

- Raw JFR recording: [`profiling/auth-runtime.jfr`](profiling/auth-runtime.jfr)
- Hot methods view: [`profiling/auth-hot-methods.txt`](profiling/auth-hot-methods.txt)
- Prometheus snapshot: [`profiling/auth-prometheus-snapshot.txt`](profiling/auth-prometheus-snapshot.txt)

## Process Justification

Java Flight Recorder (JFR) was chosen for its low overhead and deep integration
with the JVM. For an authentication service, CPU usage (password hashing) and
memory allocation (JWT generation) are the primary concerns. JFR allows us to
identify exactly where the CPU cycles are going during high-load login scenarios.

## Observed Results

### Hot Methods (CPU)

The following methods were identified as CPU hotspots:

| Method | Samples | Share |
| :-- | :-- | :-- |
| `org.springframework.security.crypto.bcrypt.BCrypt.hashpw` | 450 | 65.2% |
| `id.ac.ui.cs.advprog.yomu.auth.internal.repository.UserRepository.findByIdentifier` | 80 | 11.6% |
| `java.net.SocketInputStream.socketRead0` | 45 | 6.5% |

As expected, `BCrypt.hashpw` is the dominant CPU consumer. This is by design to
slow down brute-force attacks.

### Average Latency (Prometheus)

| Endpoint | Count | Avg Latency |
| :-- | :-- | :-- |
| `/api/auth/login` | 100 | 450ms |
| `/api/auth/register` | 50 | 480ms |
| `/api/auth/refresh` | 200 | 15ms |

## Analysis And Improvements

1.  **BCrypt Latency**: The 450ms latency is acceptable for security but limits
    throughput. If horizontal scaling is needed, the CPU will be the bottleneck.
    Currently, the cost factor is 10 (default).
2.  **Database Lookup**: `findByIdentifier` performs a triple OR query (`username`, `email`, `phone`). While H2 handles this quickly, on PostgreSQL with millions of rows, this might slow down.
3.  **JWT Signing**: JWT signing (HS256) is fast and doesn't show up as a hotspot.

### Recommended Improvements

1.  **Database Indexes**: Ensure unique indexes exist for `username`, `email`, and `phone`. The current `init()` script already has `UNIQUE` for username and email, but we should verify phone as well.
2.  **Caching**: Consider caching user details (without password) in Redis to speed up token validation and profile lookups, especially for the `AuthFacade.getUserById` path which is called by other services.
3.  **Rate Limiting**: The `AuthRateLimitFilter` is currently in-memory. For a distributed setup, this must be moved to Redis to prevent IP-based attacks across multiple instances.
4.  **Async Events**: `UserRegisteredEvent` is already published via RabbitMQ, which is good as it keeps the registration path fast.
