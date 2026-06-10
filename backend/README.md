# Athenyx Ward — Backend

Spring Boot 3.5 REST API powering **Athenyx Ward**, a SaaS platform that analyses Gmail
messages for phishing, spoofing, malware, and other digital threats using local AI
and heuristic rules.

This module is part of a monorepo. See the root `AGENTS.md` and `SPEC.md` for
high-level context, sprint planning, and shared conventions.

---

## Stack

- Java 21, Spring Boot 3.5.14
- Spring Security with OAuth2 Client (Google) and JWT
- Spring AI (Ollama backend, disabled in Sprint 1, enabled in Sprint 3)
- Spring Data JPA / Hibernate, MySQL 8
- Google API Client + Gmail API (`gmail.readonly` scope)
- Lombok, Jakarta Bean Validation
- JUnit 5 + Mockito + Spring Security Test

---

## Quick start

```bash
# 1. Make sure MySQL is running on localhost:3306 and Ollama is up
#    (Ollama is only required once Sprint 3 is active).

# 2. Configure secrets/credentials
src/main/resources/application.properties       # defaults (dev only)
# Override for your machine with environment variables, e.g.:
#   SPRING_DATASOURCE_PASSWORD=...
#   SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=...
#   SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=...

# 3. Build & run
./mvnw spring-boot:run
# Backend listens on http://localhost:8080
```

The first run creates the `athenyx_ward` schema automatically
(`createDatabaseIfNotExist=true`) and applies JPA `update` DDL.

### Production override

For production, set:

```properties
server.force-https=true
app.auth.cookie-secure=true
spring.jpa.hibernate.ddl-auto=validate
```

…and provide a strong, externally-generated `app.jwt.secret` (Base64-encoded,
≥ 64 bytes recommended).

---

## Maven commands

```bash
./mvnw spring-boot:run            # Dev server (port 8080)
./mvnw clean install              # Build executable JAR
./mvnw test                       # Run all tests
./mvnw test -Dtest=ClassName      # Run a single test class
./mvnw test -Dtest=ClassName#methodName
```

The built artifact is `target/backend-0.0.1-SNAPSHOT.jar`.

---

## Configuration

All settings live in `src/main/resources/application.properties`.

| Property | Purpose | Default |
| --- | --- | --- |
| `server.port` | HTTP port | `8080` |
| `server.force-https` | Force HTTPS (production only) | `false` |
| `spring.datasource.url` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/athenyx_ward` |
| `spring.jpa.hibernate.ddl-auto` | JPA schema mode | `update` |
| `app.frontend.url` | CORS allow-list (frontend origin) | `http://localhost:4200` |
| `app.jwt.secret` | HMAC-SHA key (Base64) | dev only — **rotate in prod** |
| `app.jwt.expiration-ms` | Access token TTL | `900000` (15 min) |
| `app.jwt.refresh-expiration-ms` | Sliding refresh TTL | `2592000000` (30 d) |
| `app.jwt.refresh-absolute-expiration-ms` | Hard cap per family | `7776000000` (90 d) |
| `app.auth.refresh-cookie-name` | Refresh cookie name | `athenyx_refresh` |
| `app.auth.cookie-secure` | Set `Secure` on cookies | `false` |
| `spring.ai.ollama.chat.enabled` | Toggle Spring AI | `false` (Sprint 1) |
| `spring.ai.ollama.base-url` | Ollama endpoint | `http://localhost:11434` |
| `spring.ai.ollama.chat.model` | Model name | `llama3` |

---

## Architecture

The application is a client-server REST API. Auth uses **stateless JWTs** in
HttpOnly cookies, paired with a **rotating refresh-token family** persisted in
MySQL.

```
┌──────────┐   /oauth2/authorization/google   ┌────────────────────┐
│ Browser  │ ───────────────────────────────▶ │ Spring Security    │
│ (SPA)    │ ◀─────── 302 to Google ──────────│ OAuth2 Client      │
└──────────┘                                  └─────────┬──────────┘
       │                                                  │
       │  Set-Cookie: athenyx_token, athenyx_refresh      │
       │  302 → http://localhost:4200/home                 ▼
       │                                       ┌────────────────────┐
       │                                       │ OAuth2LoginSuccess │
       │                                       │ Handler            │
       │                                       │ (issues JWT + RT)  │
       │                                       └─────────┬──────────┘
       │                                                 │
       ▼                                                 ▼
┌──────────┐   GET /api/...       ┌─────────────────────────────────────┐
│ SPA      │ ───────────────────▶ │  JwtAuthenticationFilter (cookies)  │
│          │                      │  RefreshOriginFilter  (CSRF/origin) │
│          │ ◀─── 401/403/200 ── │  SecurityFilterChain                 │
└──────────┘                      └─────────────────────────────────────┘
```

### Package layout (`com.athenyx.backend`)

| Package | Responsibility |
| --- | --- |
| `BackendApplication` | Boot entry point |
| `config/` | `SecurityConfig`, `GlobalExceptionHandler` |
| `controller/` | REST endpoints (`AuthController`, `GmailController`) |
| `dto/` | Request/response records (no JPA leakage) |
| `entity/` | JPA entities (`User`, `Email`, `RefreshToken`, …) |
| `repository/` | Spring Data JPA interfaces |
| `service/` | Business logic (`AuthService`, `RefreshTokenService`) |
| `security/` | Filters, OAuth2 success handler, token encryption |
| `util/` | Pure helpers (`JwtUtil`) |
| `gmail/` | Gmail API integration (`GmailService`) |
| `ai/` | Spring AI/Ollama layer (Sprint 3) |
| `heuristics/` | Threat detection rules (Sprint 2) |

---

## Security model

### Authentication

1. The SPA redirects to `GET /oauth2/authorization/google`.
2. Spring Security handles the Google dance. On success,
   `OAuth2LoginSuccessHandler`:
   - Upserts the `User` (encrypted Google access/refresh tokens, role
     defaults to `TRIAL`, 30-day trial window).
   - Bumps `User.tokenVersion` for returning users (invalidates prior
     access JWTs).
   - Generates a short-lived JWT and a new refresh-token family.
   - Sets `athenyx_token` + `athenyx_refresh` HttpOnly cookies and 302s to
     `${app.frontend.url}/home`.

### Authorization

- `JwtAuthenticationFilter` runs on every request, extracting the JWT
  from `Authorization: Bearer …` or the `athenyx_token` cookie.
- The filter verifies signature, expiration, and `tokenVersion` against
  the database — stale tokens are rejected as 401 (`"Token revoked"`).
- Skips public auth endpoints (`/api/auth/refresh`, `/api/auth/logout`,
  `/api/auth/login-url`) so the refresh flow can run unauthenticated.
- `RefreshOriginFilter` is a CSRF defence for `POST /api/auth/refresh`:
  it rejects requests whose `Origin` or `Referer` host does not match
  `app.frontend.url`.

### Refresh-token rotation

Implemented in `RefreshTokenService`:

- Each family is identified by a UUID. A row stores the SHA-256 hash
  (never the raw token), `expiresAt` (sliding), and `absoluteExpiresAt`
  (hard cap).
- On every successful refresh the old row is marked `REPLACED` and a
  successor row is inserted in the same family. The `User.tokenVersion`
  is incremented, invalidating previously-issued access JWTs.
- Presenting an **already-rotated** token triggers
  `RefreshTokenException(REUSE_DETECTED)`, which revokes the entire
  family — this stops token-theft replay attacks.
- Absolute and sliding expirations are checked separately and revoke
  the family when the hard cap is hit.

### Token encryption at rest

`TokenEncryptionService` AES-GCM-encrypts the Google OAuth tokens before
persistence, key derived from `app.jwt.secret` via SHA-256. Stored values
use the `v1:` prefix so a future key-rotation scheme can detect legacy
plaintext.

### Roles

`enum Role { ADMIN, PREMIUM, TRIAL }`. The `ADMIN` role is required to
access `/admin/*` routes (enforced via the Angular `adminGuard`; backend
authorization checks are added per controller as features land).

### Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps:

- `AuthenticationException` → 401 `No autenticado`
- `AccessDeniedException` → 403 `Acceso denegado`
- `RefreshTokenException` → 401 with the exception message
- `LazyInitializationException` → 500 (server-side bug, not a 4xx)
- `RuntimeException` mentioning `token`/`auth` → 401
- All other `Exception` → 500 `Error interno del servidor`

---

## REST API

All endpoints below require a valid `athenyx_token` cookie (or
`Authorization: Bearer <jwt>`) unless marked **public**. The
`User`/`Principal` is the user id (`Long`).

### `GET /api/auth/me`

Returns the current `UserInfo` DTO.

### `PUT /api/auth/me/accessibility-mode`

Body: `{ "accessibilityMode": boolean }`. Toggles the accessibility flag.

### `POST /api/auth/refresh` — **public**

Reads `athenyx_refresh` cookie, rotates the family, returns a new
access JWT and sets fresh cookies. Requires `Origin` / `Referer` to
match `app.frontend.url`.

### `POST /api/auth/logout` — **public**

Revokes the current refresh-token family member (best effort) and
clears both auth cookies.

### `POST /api/auth/logout-all` — **public**

Revokes **every** active refresh token for the current user and clears
auth cookies. Response includes the count of revoked tokens.

### `GET /api/auth/login-url` — **public**

Returns `{ "url": "/oauth2/authorization/google" }` so the SPA can
trigger login without hard-coding the path.

### `GET /api/emails/fetch?page=0`

Fetches a page of up to 20 emails from Gmail for the current user.
- Uses the stored OAuth2 credentials (refreshing automatically if
  expired).
- Detects Gmail `historyId` changes; on change, clears the
  `gmail_page_tokens` cache for the user.
- Persists a summary row in `emails` for each new message.
- Stores **only analysis-relevant data** (sender, subject, content for
  analysis, extracted URLs, dates, read flag) — full raw bodies are
  **not** kept.

### `GET /api/emails/{emailId}`

Returns the full `EmailDetail` for a message that belongs to the
current user, marking it as read. Returns 404 if not found, 403 if the
email belongs to a different user.

### `GET /oauth2/authorization/google` — **public** (Spring default)

Triggers the OAuth2 login flow. No controller code.

### `GET /actuator/health` — **public**

Standard Spring Boot health endpoint.

---

## Data model

| Table | Purpose | Key fields |
| --- | --- | --- |
| `users` | App users (one per Google account) | `google_id` UNIQUE, `role`, `token_version`, `accessibility_mode`, encrypted `google_access_token` / `google_refresh_token` |
| `emails` | Persisted Gmail messages (analysis-ready subset) | `gmail_id` + `user_id` (logical), `content_for_analysis`, `extracted_urls` |
| `refresh_tokens` | Rotating refresh-token families | `token_hash` UNIQUE, `family_id`, `expires_at`, `absolute_expires_at`, `revoked_at`/`revoked_reason` |
| `gmail_page_tokens` | Gmail pagination cache per user | `(user_id, page)` UNIQUE |

Stored JPA entities use `LocalDateTime` UTC (see Jackson config). Email
content is `LONGTEXT` to support full MIME bodies but the application
**truncates** extracted text to 10 000 chars per `GmailService`.

---

## Testing

The test suite is JUnit 5 + Mockito + Spring Security Test. Each
collaborator of a public class is covered by at least one focused unit
test:

| Class | Test |
| --- | --- |
| `BackendApplication` | `BackendApplicationTests` (context loads) |
| `GmailService` | `GmailServiceParseDateTest` (RFC1123, offsets, garbage) |
| `JwtAuthenticationFilter` | `JwtAuthenticationFilterTest` (cookie, header, version, skip list) |
| `OAuth2LoginSuccessHandler` | `OAuth2LoginSuccessHandlerTest` (cookie set + redirect) |
| `RefreshOriginFilter` | `RefreshOriginFilterTest` (origin/referer allow/deny) |
| `RefreshTokenService` | `RefreshTokenServiceTest` (issue, rotate, reuse, expiry) |

Run everything:

```bash
./mvnw test
```

The `contextLoads()` smoke test requires a reachable MySQL because
`@SpringBootTest` boots the full context. To run unit tests without a
database, prefer the per-class invocation:

```bash
./mvnw test -Dtest='RefreshTokenServiceTest'
```

---

## Internationalisation

All user-facing strings returned by the API are in **Spanish** (the
current supported locale). Adding more languages is intentionally
deferred to a future sprint — see `SPEC.md` § Language Support.

---

## Known limitations (Sprint 1)

- Only Gmail is supported.
- AI analysis (`spring.ai.ollama.chat.enabled=true`) is intentionally
  disabled; heuristics and AI layers land in Sprints 2 and 3.
- DDL is auto-applied via JPA — use `validate` or Flyway in production.
- No CSRF token: the SPA is single-origin and `SameSite=Lax` cookies
  plus the `RefreshOriginFilter` provide sufficient protection. Add a
  proper CSRF token if the API will be consumed by multiple origins.
