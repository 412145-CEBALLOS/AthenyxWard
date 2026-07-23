# Agent Instructions

## Project Structure

Monorepo with two independent packages:
- `backend/` — Spring Boot 3.5.14 REST API (Java 21, Maven)
- `frontend/` — Angular 20.3 SPA with SSR (TypeScript 5.9, npm)

No Docker. Solo developer workflow.

## Commands

### Backend (run from `backend/`)

```bash
./mvnw spring-boot:run          # Start dev server (port 8080 default)
./mvnw clean install            # Build JAR
./mvnw test                     # Run all tests
./mvnw test -Dtest=ClassName    # Run single test class
./mvnw test -Dtest=ClassName#methodName  # Run single test method
```

### Frontend (run from `frontend/`)

```bash
npm start                       # Dev server (http://localhost:4200)
npm test                        # Run tests (Karma + Chrome)
npm run build                   # Production build
npm run serve:ssr:frontend      # Run SSR server (port 4000)
```

## Architecture Notes

**Backend packages** (under `com.athenyx.backend`):
- `entity/` — JPA entities
- `dto/` — Data transfer objects
- `repository/` — Spring Data JPA interfaces
- `service/` — Business logic
  - `service/reminder/` — Reminder CRUD + Premium/Admin gating (US 2.6)
  - `service/audit/` — AuditLog reading and retention purging
- `controller/` — REST endpoints
- `config/` — Spring configuration (SecurityFilterChain, CORS, etc.)
- `security/` — JWT filters, auth providers
- `audit/` — Audit event publisher, listener, correlation ID filter, domain events
- `ai/` — Spring AI + Ollama integration
- `gmail/` — Gmail API integration
- `heuristics/` — Rule-based threat detection (`ThreatScorer`, `HeuristicEngine`, `HeuristicAnalysisService`, 19 rules)
- `metadata/` — MIME-header metadata layer (sender validation, auth results, mass-mailing detection, sender-trust)
- `util/` — Shared utilities

## Frontend structure** (under `src/app/`):
- Standalone components (no NgModules)
- Signals for reactivity (with two-way `model()` for parent-driven state)
- SSR enabled (Express 5 server)
- `utils/risk.util.ts` — shared `RISK_THRESHOLDS` (40/70) + `riskLevelFromPercentage()` helper
- `services/analysis.service.ts` — HTTP wrapper for `POST /api/emails/{id}/analyze` and `GET /api/emails/{id}/analysis` (US 2.8)

## Subscription Management (Angular)

All components that use RxJS observables MUST manage subscriptions properly to prevent memory leaks. Use the `takeUntil` pattern:

```typescript
import { Component, OnDestroy } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';

@Component({...})
export class MyComponent implements OnDestroy {
  private readonly onDestroy = new Subject<void>();

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  someMethod(): void {
    this.someService.getData().pipe(
      takeUntil(this.onDestroy)
    ).subscribe(data => { ... });
  }
}
```

Key rules:
- Always use `takeUntil(this.onDestroy)` for all subscriptions
- Call `this.onDestroy.next()` and `this.onDestroy.complete()` in `ngOnDestroy()`
- Disconnect any observers (ResizeObserver, MutationObserver, etc.) in `ngOnDestroy()`

## Analysis Pipeline (US 2.1 / 2.2 / 2.3 / 2.8)

The SPA is a thin shell over the heuristic engine. The flow for opening
an email is:

```
email-list item click
   └─ home.selectEmail(summary)
        ├─ EmailService.getEmailDetail()   → EmailDetail
        └─ bootstrapAnalysis(detail, summary)
             ├─ TRIAL  → analysisState = 'idle'   (user must click "Analizar este correo")
             └─ PREMIUM/ADMIN + riskLevel != null
                └─ AnalysisService.getLatest() → cached result OR analysisState = 'idle'
   ↓ (user clicks panel-toggle or trial button)
home.onAnalysisRequest()
   └─ AnalysisService.analyze()
        ├─ 200 → analysisResult set, state = 'ready', analysisPanelOpen = true
        ├─ 403 → state = 'unavailable-trial' (TRIAL quota exhausted)
        └─ 5xx → state = 'error'
```

Backend (`HeuristicAnalysisService.analyze`):
- 24 h cache via `findFirstByEmailIdOrderByAnalyzedAtDesc` + 24 h cutoff
- Trial-limit check (`TRIAL_LIMIT = 20`) → `TrialLimitExceededException` → 403
- `@Async("heuristicsExecutor")` — runs in dedicated thread pool
- Persists to `EmailAnalysis` entity, increments `User.analysisCount` for TRIAL users
- Re-analysis (cache miss) **always inserts a new `EmailAnalysis` row** — never updates an existing one, so the history endpoint always shows the full timeline.

## AI Explanation Flow (US 3.2 / US 3.3)

Separate from the heuristic analysis pipeline (above). The "Explicar con IA" button lives
in the email viewer header, next to the kebab menu. It calls the AI explanation endpoint
(`POST /api/emails/{id}/explain`) regardless of whether a heuristic analysis exists.

**Frontend flow:**
```
user clicks "Explicar con IA" button (email-viewer-header)
   └─ home.onExplainRequest()
        ├─ 200 → aiExplanation set, aiState = 'ready' → card with badge + 3 sections
        ├─ 403 → aiState = 'unavailable-trial' + toast
        └─ 5xx / timeout → aiState = 'error' + toast with "Reintentar" action
```

**Response structure:**
`AiExplanationResponse { id, summary, heuristicExplanation, secondOpinion, origin: LLM|FALLBACK, modelName, generatedAt }`

**UI rendering (email-viewer):**
- If all 3 fields are null and origin=LLM → "Análisis anterior no disponible" (legacy rows)
- If all 3 fields are null and origin=FALLBACK → "Error al consultar a la IA"
- Otherwise → render up to 3 sections conditionally, each with title + text (`.ai-section-title`, `.ai-section-text`)

**Section titles:**
- "Resumen del correo" — summary of the email content
- "Por qué el análisis heurístico dio este resultado" — why heuristic gave this percentage
- "Segunda opinión de la IA" — AI's own independent verdict

Key facts:
- `canExplain` input to `email-viewer-header` is `analysis()?.riskLevel != null` — button is
  disabled with tooltip "Analiza primero el correo" until the email has been analyzed.
- Badge shows "Generado por IA" (origin LLM) or "Explicación heurística" (origin FALLBACK).
- Timestamp is rendered via `Intl.RelativeTimeFormat('es')` — no external library.
- Reset on email change: `selectEmail`, `clearSelection`, `openStandaloneEmail` all set
  `aiState = 'idle'` and `aiExplanation = null`.
- `ToastService.error(msg, { action: { label: 'Reintentar', onClick: fn } })` — the
  toast's inline action button re-fires `onExplainRequest`.

Backend (`AiExplanationService.explain`):
- PREMIUM/ADMIN only → 403 for TRIAL users.
- LLM is asked for a structured JSON response with 3 fields (summary, heuristicExplanation, secondOpinion), each 2 paragraphs.
- Prompt includes anti-prompt-injection guardrails: untrusted content wrapped in `<email_body>` delimiters, explicit instruction to ignore any directives found inside.
- `num-predict = 1000` (provides ~2500 chars headroom for 3 sections of 3-4 sentences with Qwen 2.5 7B).

## AI Resilience (US 3.8)

The `AiExplanationService` implements five internal paths (documented in its class-level Javadoc):

| Path | Trigger | Origin | Log |
|------|---------|--------|-----|
| No prior analysis | `latest == null` | FALLBACK | `durationMs=0, origin=FALLBACK` |
| AI disabled | `enabled=false` | FALLBACK | `durationMs, origin=FALLBACK` |
| LLM success | Ollama responds ≤25 s with valid JSON + 3 fields | LLM | `durationMs, origin=LLM` |
| LLM exception | timeout, connection refused, 5xx | FALLBACK | `durationMs, origin=FALLBACK, error=…` |
| Parse fails — invalid JSON | JSON malformed, no truncation pattern | FALLBACK | `durationMs, origin=FALLBACK, error=invalid_json length=N` |
| Parse fails — truncated JSON | Response cut by `num-predict` (pattern or length) | FALLBACK | `durationMs, origin=FALLBACK, error=truncated_json length=N` |
| All fields empty | 3 nulls after successful parse | FALLBACK | `durationMs, origin=FALLBACK, error=empty_sections` |

All paths emit `log.info("ai.explain userId={} emailId={} durationMs={} origin={} [error=…]")` — never propagated to the client. Tests in `AiExplanationServiceTest` cover all paths.

## Reminders (US 2.6)

User-defined reminders attached to a single email. One row per
`(user, email)` pair — enforced at the DB level by the
`uk_reminder_user_email` unique constraint and at the service
level by `ReminderConflictException` (→ 409 Conflict on POST).

Endpoints (`/api/reminders`):
- `POST` — PREMIUM/ADMIN only (`@PreAuthorize("hasAnyRole('PREMIUM', 'ADMIN')")`). TRIAL → 403 (`ReminderPremiumRequiredException`).
- `PATCH /{id}` — any authenticated user; ownership verified in the service. Returns 404 when missing or owned by another user (`ReminderNotFoundException`).
- `DELETE /{id}` — same gating as PATCH.
- `GET ?filter=all|pending|done` — any authenticated user. TRIAL users get an **empty list** (no 403) so the SPA can render the upsell state uniformly.
- `GET /by-email/{emailId}` — used by the email viewer.

DTOs: `CreateReminderRequest`, `UpdateReminderRequest`, `ReminderResponse`, `ReminderSummary`, `RemindersListResponse`.

Enrichment pattern: `EmailSummary` and `EmailDetail` now carry an
optional `reminder: ReminderSummary | null`. `GmailService` calls
`ReminderService.findSummaryByEmail` for the detail view and
`findSummariesForEmails` (single batch query) for the list views
— same pattern used for risk-data enrichment, so there's no N+1.

Frontend flows:
- `ReminderService` (`/api/reminders/*`).
- `ReminderIndicatorComponent` renders the chip in the list row
  (`variant="list"`) and the banner inside the email viewer
  (`variant="banner"`). Done state → muted; upcoming (<24 h) →
  pulse + red highlight.
- `ReminderFormDialogComponent` is the create / edit modal; the
  service handles 409 by reopening the same dialog in edit mode
  with the existing reminder fetched.
- `ConfirmDialogComponent` is the reusable "are you sure?" modal
  used before delete.
- `ToastService` was extended with an optional inline `action`
  button (label + callback) — used in 2.7 for "Marcar hecho" from
  the toast, but available to any future flow.

## Reminder Notifications (US 2.7)

Powers the bell-icon panel and the "your reminder just fired"
toasts. Every 2 minutes the SPA polls
`GET /api/notifications/upcoming` and renders the result.

Endpoint (`/api/notifications`):
- `GET /upcoming` — PREMIUM/ADMIN only. Returns the list of
  non-done reminders for the user whose date falls inside a
  symmetric 24 h window around `now` (i.e. due in the next 24 h
  **plus** overdue by up to 24 h). Each entry includes
  `isOverdue: boolean` so the frontend can decide between
  panel-only and toast.

Backend (`NotificationService.getUpcomingReminders`):
- Reads `Clock` from the context (see `ClockConfig`) so unit
  tests can pin "now" deterministically.
- Single query: `ReminderRepository.findUpcomingForUser(userId, from, to)`.
- Window: `Duration.ofHours(24)` in both directions.

Frontend (`NotificationService` — singleton, `providedIn: 'root'`):
- `startPolling(120_000)` (called by `LayoutComponent` for
  PREMIUM/ADMIN users only). Fires once immediately, then every
  2 minutes. Stops on `ngOnDestroy`.
- `markShown(reminderId)` — adds the id to a memory-only
  `Set<number>` so the toast never re-fires for the same
  reminder in the same session. The set is reset on page
  reload, so a refresh re-shows toasts for still-overdue
  reminders.
- `markDone(notification)` — PATCHes the reminder to
  `done: true`, removes it from the local cache and adds the
  id to `shownIds`. Wired to the bell row's check button and
  to the toast's inline "Marcar hecho" action.
- The bell panel always renders the latest poll (no dedupe
  on the panel) so reopening the bell always shows the
  same list.

UI:
- The panel lives in `HeaderComponent` — a red badge over
  the bell shows the count. Each row shows the email
  subject, the user message, a relative time string
  ("en 2 h" / "hace 5 min") and a green check button to
  mark done. Clicking the row navigates to
  `/home?emailId=...`.
- Toasts are `warning` type with the action button
  (`ToastService.action`) rendered by
  `ToastContainerComponent`. The click invokes
  `markDone` and the toast auto-dismisses.

## Analysis History (US 2.4)

`/history` page renders the timeline of every `EmailAnalysis` row for the current user. Flow:

```
/history load → AnalysisService.getHistory({ page, from?, to? })
   └─ GET /api/analysis/history?page=0&size=20&from=YYYY-MM-DD&to=YYYY-MM-DD
        └─ AnalysisController.getHistory → AnalysisHistoryService.getHistory
             └─ EmailAnalysisRepository.findHistoryByUser (JOIN FETCH ea.email)
                  ORDER BY analyzedAt DESC
        └─ Page<EmailAnalysis> → AnalysisHistoryResponse (items, currentPage, totalPages, totalItems)
   ├─ 200 + items → render cards (sender, subject, risk %, risk badge, summary, analyzedAt)
   ├─ 200 + items=[] → empty state (no analyses yet / no matches in range)
   └─ 5xx → error state
```

Backend (`AnalysisHistoryService.getHistory`):
- Always returns 200 (never 404) — empty result is `items: []`, `totalItems: 0`.
- `from`/`to` are `LocalDate` (`YYYY-MM-DD`); `to` is converted to end-of-day server-side.
- Page size clamped to `[1, 100]`, default 20; `page` clamped to `>= 0`.
- Each item includes `summary` (200-char truncated `aiExplanation`).

Frontend (`AnalysisHistoryComponent`):
- Signals: `items`, `loading`, `error`, `currentPage`, `totalPages`, `totalItems`, `from`, `to`.
- Native `<input type="date">` controls for the range; no date library required.
- Reuses `<app-page-shell>` and `<app-email-paginator>`.
- Click on a card navigates to `/home?emailId={id}` so the existing viewer + analysis panel is reused.

## Audit Log (US 4.2)

Immutable log of security-relevant platform events. Events are persisted
asynchronously after the originating transaction commits, so audit failures
never affect the caller's operation.

### Backend architecture

**Entity**: `AuditLog` (JPA) with columns: `id`, `createdAt`, `actorId` (FK, denormalised `insertable=false, updatable=false`), `actorEmail`, `actorRole`, `actionType` (`AuditActionType` enum, 19 types), `targetType`, `targetId`, `severity` (`AuditSeverity`: INFO/WARNING/CRITICAL), `result` (`AuditResult`: SUCCESS/FAILURE), `payload` (JSON TEXT), `ipAddress`, `userAgent`, `correlationId`.

**Indices**: `idx_audit_created_at` on `created_at`, `idx_audit_actor_id` on `actor_id`, `idx_audit_action_type` on `action_type`, `idx_audit_correlation_id` on `correlation_id`.

**Correlation ID**: `CorrelationIdFilter` (`@Order(0)`, before `JwtAuthenticationFilter`) extracts `X-Correlation-ID` header or generates `UUID.randomUUID()`, sets MDC + request attribute, cleans up in `finally` block.

**Event pattern**:
- Each domain event extends `ApplicationEvent` (e.g. `LoginSuccessEvent`, `PhishingDetectedEvent`, `TokenRefreshFailedEvent`, `EmailMarkedImportantEvent`, etc.)
- `AuditEventPublisher` wraps `ApplicationEventPublisher` with typed helper methods
- `AuditEventListener` consumes via `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` — persists in a **dedicated `REQUIRES_NEW` transaction** so the audit write never interferes with the caller's commit
- Persistence failure is caught and logged but **never propagated** to the caller

> ⚠️ **CRITICAL RULE — `@Transactional` on every publisher**: Every method that calls `auditEventPublisher.publishXxx(...)` **must be annotated `@Transactional`**. Without it, `AFTER_COMMIT` silently discards the event. The `REQUIRES_NEW` on `persist()` ensures the audit row is committed independently of the caller's outcome. The `fallbackExecution = true` is a safety net for edge cases (e.g., calls from non-transactional code); it does **not** replace the need for `@Transactional` on the caller.
>
> ⚠️ **Self-invocation pitfall**: `persist()` is called via `self().persist(...)` (through `ApplicationContext.getBean`) to bypass Spring's self-invocation restriction. Never call `this.persist(...)` from within `AuditEventListener` — it bypasses the CGLIB proxy and the `@Transactional(REQUIRES_NEW)` annotation is ignored, causing the save to run without a transaction context and fail silently.
>
> ⚠️ **Audit context for async services**: `HeuristicAnalysisService.analyze` runs `@Async("heuristicsExecutor")`. Since `AuditContext` reads from `RequestContextHolder` (thread-local request scope), it returns null on the async thread. To preserve `ip_address`, `user_agent` and `correlation_id` in audit rows for phishing events, the controller must capture these from the HTTP request **before** calling the async service and pass them as parameters. `AuditEventListener.onPhishingDetected` uses the event's context fields when present, falling back to `AuditContext`. The same pattern applies to any other `@Async` publisher.

**Retention**: `AuditRetentionService` runs monthly (`@Scheduled(cron = "0 0 3 1 * *")`), purges entries older than `app.audit.retention-days` (default 365, configurable in `application.properties`).

**Endpoints** (`AuditController`, ADMIN only):
- `GET /api/admin/audit` — paginated list with filters: `page`, `size`, `from`, `to`, `actor`, `action`, `severity`, `query` (free-text search)
- `GET /api/admin/audit/export` — streaming CSV download; same filters, no pagination; anti-injection CSV escaping (cells starting with `=`, `+`, `-`, `@`, CR get `'` prefix; cells with commas/quotes/newlines are wrapped in `"`)

### Wired events

| Event | Trigger | Notes |
|-------|---------|-------|
| `LoginSuccessEvent` | `OAuth2LoginSuccessHandler.onAuthenticationSuccess` | |
| `LoginFailedEvent` | `OAuth2LoginFailureHandler` | |
| `LogoutEvent` | `AuthController.logout` | Anonymous actor when no valid refresh token |
| `LogoutEvent` (all devices) | `AuthController.logoutAll` | `payload.revokedCount = N` |
| `TokenRefreshFailedEvent` | `RefreshTokenService.resolveUserForRotation` (MISSING, NOT_RECOGNISED, REUSE_DETECTED, ABSOLUTE_EXPIRED, EXPIRED) | |
| `PhishingDetectedEvent` | `HeuristicAnalysisService` when `ThreatLevel.RED` | |
| `EmailMarkedImportantEvent` | `GmailController.toggleImportant` | |
| `EmailHiddenEvent` | `GmailController.hideEmail` | |
| `EmailUnhiddenEvent` | `GmailController.unhideEmail` | Uses same event class with `unhidden=true` |
| `EmailDeletedEvent` | `GmailController.deleteEmail` | |
| `ExportCsvEvent` | `AuditController.getExportCsv` | |
| `AutoAnalysisCompletedEvent` | Placeholder — hook ready, not yet wired | |
| `RoleChangedEvent`, `UserDeactivatedEvent` | Placeholder — hook ready, wired in US 4.3 | |
| `ConfigUpdateEvent` | Placeholder — hook ready, wired in US 4.4 | |

### Frontend architecture

- `AuditService` — `getEntries(filters)` → `GET /api/admin/audit`, `getExportUrl(filters)` → `GET /api/admin/audit/export`
- `AdminAuditComponent` — standalone page with filters (period preset buttons, date range, actor, action, severity, free-text query with 300ms debounce), paginated table, export CSV button, drawer trigger
- `AuditDetailDrawerComponent` — side drawer showing full entry detail: timestamp, actor, action, target, severity/result badges, IP, user agent, correlation ID, parsed JSON payload
- `environment.auditTopResults: 10` — configurable top-N for ranked free-text search results
- Both components use `DatePipe` and `takeUntil(this.onDestroy)` for subscription management

## Global Config (US 4.4 / US 4.5)

ADMIN panel for 18 platform-wide configuration keys grouped into 7 categories (AI, Análisis Heurístico, Retención de Datos, Cuotas, Rate Limiting, Notificaciones, Seguridad).

### Backend

**Entity**: `AppConfig` with `config_key` (PK), `config_value` (TEXT), `type` (VARCHAR), `category` (VARCHAR), `updated_at`, `updated_by`. Uses `ddl-auto=update`. Column named `config_value` (not `value`) to avoid H2 reserved word conflicts.

**`ConfigKey` enum** (16 keys):
- `AI_ENABLED`, `AI_MODEL` — AI
- `HEURISTIC_RISK_THRESHOLD_LOW` (INT, 0-100), `HEURISTIC_RISK_THRESHOLD_MEDIUM` (INT, 0-100), `HEURISTIC_CACHE_HOURS` — Análisis heurístico
- `AUDIT_RETENTION_DAYS`, `EMAIL_RETENTION_DAYS` — Retención
- `TRIAL_ANALYSIS_LIMIT`, `REMINDER_MAX_PER_USER` — Cuotas
- `NOTIFICATIONS_UPCOMING_WINDOW_HOURS`, `NOTIFICATIONS_POLL_INTERVAL_SECONDS` — Notificaciones
- `RATELIMIT_EXPLAIN_PER_HOUR` — Rate Limiting (`ANALYSIS_PER_HOUR = 60` hardcoded en `RateLimiter`)
- `COPY_SUPPORT_EMAIL` — Copy y contenido
- `OAUTH_ALLOWED_DOMAINS`, `SECURITY_MAX_FAILED_LOGINS`, `SECURITY_IP_BLOCKLIST` — Seguridad

**`ConfigService`**:
- `@Cacheable("config")` on `getEntry`/`getAllGrouped` — Caffeine, `expireAfterWrite=60s`
- `@CacheEvict(allEntries=true)` on `setEntry`, executed via `TransactionSynchronization.afterCommit()` to ensure cache is only evicted after DB commit
- Type validation: `INT` → `Integer.parseInt` + range check; `BOOLEAN` → `Boolean.parseBoolean`; `STRING` → non-blank check
- `toResponse()` converts `ConfigType.valueOf(cfg.getType())` for safe enum lookup

**`ConfigController`** (`/api/admin/config` — ADMIN only):
- `GET /` → grouped categories with entries (all 16 keys)
- `GET /{key}` → single entry
- `PUT /{key}` → update value + validates type/range → returns `ConfigEntryResponse`
- `POST /{key}/purge-now` → for `AUDIT_RETENTION_DAYS` and `EMAIL_RETENTION_DAYS`; calls `AuditRetentionService.purgeNow()` or `EmailRetentionService.purgeNow()`; returns `PurgeResultResponse`
- `GET /api/public/config` → only publicly visible keys (for SPA bootstrap)

**Audit events**: `CONFIG_UPDATE` on every PUT; `CONFIG_PURGE` on every purge-now. `AuditRetentionService.purgeNow()` runs in its own `@Transactional` method.

**Seed**: `ConfigDataInitializer` (ApplicationRunner) inserts all 16 rows only if `repository.count() == 0`.

### Config-driven services

| Service | Config key | Notes |
|---------|-----------|-------|
| `HeuristicAnalysisService` | `TRIAL_ANALYSIS_LIMIT`, `HEURISTIC_CACHE_HOURS` | `TRIAL_LIMIT` field removed; uses `configService.getInt(TRIAL_ANALYSIS_LIMIT)` |
| `AiExplanationService` | `AI_ENABLED` | Runtime check via `FeatureToggleService.isEnabled(ConfigKey.AI_ENABLED)` — toggle takes effect immediately, no restart needed |
| `AiConfig` | `AI_ENABLED` | `AiProperties` bean holds only static values; `enabled` is a placeholder (`true`); real runtime check delegated to `FeatureToggleService` |
| `NotificationService` | `NOTIFICATIONS_UPCOMING_WINDOW_HOURS` | `UPCOMING_WINDOW` field removed; uses `configService.getInt(NOTIFICATIONS_UPCOMING_WINDOW_HOURS)` |
| `ReminderService` | `REMINDER_MAX_PER_USER` | Quota check uses `configService.getInt(REMINDER_MAX_PER_USER)`; throws `ReminderQuotaExceededException` → 409 |
| `GlobalExceptionHandler` | — | Handles `ConfigValidationException` (400), `ConfigNotFoundException` (404), `FeatureDisabledException` (404), `ReminderQuotaExceededException` (409) |

### Login Security (US 4.5)

**`LoginAttemptService`**:
- In-memory `ConcurrentHashMap<String, LoginAttempt>` keyed by IP
- Sliding window: failed attempts tracked for 15 min (configurable via `LOGIN_BLOCK_DURATION_MINUTES`)
- `isBlocked(ip)` → true if attempts ≥ `LOGIN_MAX_ATTEMPTS` within window
- `recordFailedAttempt(ip)`, `recordSuccessfulLogin(ip)`, `getRemainingLockoutTime(ip)`

**`RateLimiter`**:
- Per-user + per-endpoint sliding window (1 hour window)
- `ConcurrentHashMap<String, RateLimitBucket>` where key = `userId:endpoint`
- Bucket: `long[] timestamps` array + count; oldest timestamp evicted on each new request
- `analysis` endpoint: hardcoded 60 requests/hour; `explain` endpoint: configurable via `RATELIMIT_EXPLAIN_PER_HOUR`

**`RateLimitFilter`** (`@Order(20)`, after `JwtAuthenticationFilter`):
- Extracts `userId` from `Authentication.getPrincipal().getName()`
- For anonymous: uses IP address as key
- Returns 429 + `Retry-After` header when limit exceeded

**`OAuth2LoginSuccessHandler`**:
- Injects `LoginAttemptService` + `ConfigService`
- IP block check before redirect: if `loginAttemptService.isBlocked(requestIp)` → clears session, redirects to `/login?blocked`
- Domain whitelist: `configService.getString(OAUTH_ALLOWED_DOMAINS)` → CSV split; empty = allow all; else checks `Google OAuth2 sub` domain suffix

**`OAuth2LoginFailureHandler`**:
- Injects `LoginAttemptService` → calls `recordFailedAttempt(clientIp)` on auth failure

**`FeatureToggleService`**: `isFeatureEnabled(key)` → `configService.getBoolean(key)`. `FeatureDisabledException` extends `RuntimeException` → `404` via `GlobalExceptionHandler`.

### Frontend

- `ConfigService` — HTTP wrapper for all config endpoints
- `models/config.model.ts` — `ConfigEntry`, `ConfigCategory`, `PurgeResult`, `RiskThresholds`; `ConfigType` = `'INT' | 'BOOLEAN' | 'STRING'` (no JSON)
- `AppConfigInitializerService` — singleton signals: `supportEmail`, `pollIntervalSeconds`, `riskThresholds`, `aiEnabled`, `loading`; fetches `/api/public/config` on `load()`; reads `COPY_SUPPORT_EMAIL`, `NOTIFICATIONS_POLL_INTERVAL_SECONDS`, `HEURISTIC_RISK_THRESHOLD_LOW`, `HEURISTIC_RISK_THRESHOLD_MEDIUM`, `AI_ENABLED` (pública para deshabilitar el botón "Explicar con IA" sin necesidad de llamada autenticada)
- `LayoutComponent` — uses `AppConfigInitializerService`; polling interval = `appConfig.pollIntervalSeconds() * 1000`; support email from `appConfig.supportEmail()`
- `AdminConfigComponent` (`/admin/config`): grouped `<details open>` per categoría, cards con inputs por tipo (boolean switch, number input, text input), label en español hardcodeado en `LABELS` (synchronized con claves del backend), badge "Valor actual" prominente, purge confirm dialog para claves de retención
- Sidebar: "Configuración global" nav item (ADMIN only) → `routerLink="admin/config"`

**Stale DB rows**: If `app_config` contains rows from a previous 18-key schema (e.g. `TRIAL_AI_EXPLANATION_LIMIT`, `HEURISTIC_RISK_THRESHOLDS`, `OAUTH_SESSION_HOURS`), `AdminConfigComponent` filters them out automatically (frontend defensive filter). A `console.warn('[AdminConfig] Stale config rows filtered out:', [...])` is emitted in dev mode. To permanently clean the DB, run this SQL once:

```sql
-- Remove stale config rows from previous schema
DELETE FROM app_config WHERE config_key NOT IN (
  'AUDIT_RETENTION_DAYS','EMAIL_RETENTION_DAYS',
  'AI_ENABLED','AI_MODEL',
  'TRIAL_ANALYSIS_LIMIT','REMINDER_MAX_PER_USER',
  'HEURISTIC_RISK_THRESHOLD_LOW','HEURISTIC_RISK_THRESHOLD_MEDIUM',
  'HEURISTIC_CACHE_HOURS',
  'NOTIFICATIONS_UPCOMING_WINDOW_HOURS','NOTIFICATIONS_POLL_INTERVAL_SECONDS',
  'RATELIMIT_EXPLAIN_PER_HOUR',
  'COPY_SUPPORT_EMAIL','OAUTH_ALLOWED_DOMAINS',
  'SECURITY_MAX_FAILED_LOGINS','SECURITY_IP_BLOCKLIST'
);

-- Fix any INT keys that ended up with empty values (defaults shown)
UPDATE app_config SET config_value = '24' WHERE config_key = 'NOTIFICATIONS_UPCOMING_WINDOW_HOURS' AND (config_value IS NULL OR config_value = '');
```

## Statistics (Sprint 4)

Página `/stats` con dashboards separados para usuarios (`/api/stats/user`) y administradores (`/api/stats/admin`). Filtrable por `period=week|month|year`.

### Backend

- **`StatsController`** (`/api/stats`): `GET /user` (autenticado) y `GET /admin` (solo `ADMIN`).
- **`StatsService`**: agrega datos de `EmailAnalysisRepository`, `UserRepository` y `PaymentRepository`.
- **Métricas usuario**: correos analizados, amenazas bloqueadas (RED), tasa de phishing, riesgo medio, evolución diaria de amenazas, distribución semáforo, top categorías, actividad reciente, última amenaza, uso trial.
- **Métricas admin**: usuarios totales, suscripciones activas/canceladas, análisis totales, media análisis/usuario, amenazas globales, evolución diaria, distribución de usuarios por rol, top categorías, split heurística/IA/híbrido, DAU/WAU/MAU, conversión trial→premium, altas por día, heatmap de amenazas por hora.
- **Tendencias**: cada KPI compara el período actual vs. el período anterior inmediato. Los contadores muestran delta absoluto; los porcentajes muestran delta en puntos porcentuales.
- **Categorías de amenaza**: se obtienen parseando el JSON del campo `findings` (`HeuristicFindingDto.rule`), con tope de 5000 filas y mapeo de nombres de regla a etiquetas en español.
- **Definición de "amenaza"**: análisis con `riskLevel = RED`.
- **Consultas nativas**: agrupación por día/hora usa `DATE()`/`HOUR()` sobre `analyzed_at`, compatible con MySQL y H2 (tests).

### Frontend

- **`StatsService`** (`services/stats.service.ts`) y modelo `models/stats.model.ts`.
- **`StatsComponent`**: carga datos reales al iniciar y al cambiar período mediante un `effect` sobre `auth.user()` y `period`. Estados de carga (skeleton), error (reintentar) y vacío.
- **Secciones eliminadas del mock original** por falta de fuente de datos: marcas suplantadas, latencia p50/p95/p99, KPI "tiempo medio de análisis". El KPI de usuario ahora incluye "Riesgo medio".
- **`stats.spec.ts`**: reescrito; los fallos de encoding UTF-8 previos quedan resueltos.

### Pre-existing Test Issues (not from US 4.5)

- `adminGuard.spec.ts`: `auth.checkAuth is not a function` — stub mismatch, pre-existing

## Known Issues

- **Language**: Spanish-only UI and AI responses (i18n architecture planned for future)
- **Email storage**: Store only analysis-relevant data (sender, date, content for analysis, URLs, metadata, threat %, risk classification, AI explanation, timestamps). Never store full raw email bodies permanently.
- **AI**: Local only via Ollama + Llama 3. No cloud AI APIs.
- **Auth**: Google OAuth2 + JWT. No password storage.
- **Accessibility mode**: Enabled by default (large text, high contrast, simplified UI).

## Development Workflow

1. Start MySQL locally (port 3306)
2. **If using AI features (Sprint 3+):** Start Ollama locally with Qwen 2.5 model (`ollama serve && ollama pull qwen2.5:7b-instruct`)
3. Configure `backend/src/main/resources/application.properties`
4. Run backend: `./mvnw spring-boot:run`
   - **Sin Ollama instalado:** set env var `OLLAMA_ENABLED=false` (o en `.env`) — el backend arranca igual en modo lazy sin contactar Ollama
5. Run frontend: `npm start` (from `frontend/`)
6. Frontend dev server proxies API calls to backend (needs `proxy.conf.json`)

## Testing

- Backend: JUnit 5 + Mockito + Spring Security Test. Integration tests use H2 (in-memory) via `src/test/resources/application-test.properties`.
- Frontend: Jasmine + Karma (requires Chrome)
- Run focused tests with the single-test commands above

## Code Style

- Backend: Lombok for boilerplate reduction, Jakarta Bean Validation
- Frontend: Prettier (100 char width, single quotes), strict TypeScript
- No ESLint configured yet

## Planning Guidelines

When the user asks for a plan before implementing, do not include code examples in the plan. Only describe what each step will do (e.g., "Replace the `text` column in the `AiExplanation` entity with three nullable TEXT columns: `summary`, `heuristicExplanation`, and `secondOpinion`"). The implementation itself may include code, but the plan should be descriptive only.

## References

- `SPEC.md` — Full project specification and sprint planning
- Sprint 1 focus: Auth + Gmail integration + base homepage
