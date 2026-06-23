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
2. Start Ollama locally with Llama 3 model
3. Configure `backend/src/main/resources/application.properties`
4. Run backend: `./mvnw spring-boot:run`
5. Run frontend: `npm start` (from `frontend/`)
6. Frontend dev server proxies API calls to backend (needs `proxy.conf.json`)

## Testing

- Backend: JUnit 5 + Mockito + Spring Security Test
- Frontend: Jasmine + Karma (requires Chrome)
- Run focused tests with the single-test commands above

## Code Style

- Backend: Lombok for boilerplate reduction, Jakarta Bean Validation
- Frontend: Prettier (100 char width, single quotes), strict TypeScript
- No ESLint configured yet

## References

- `SPEC.md` — Full project specification and sprint planning
- Sprint 1 focus: Auth + Gmail integration + base homepage
