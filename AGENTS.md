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
- `controller/` — REST endpoints
- `config/` — Spring configuration (SecurityFilterChain, CORS, etc.)
- `security/` — JWT filters, auth providers
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
