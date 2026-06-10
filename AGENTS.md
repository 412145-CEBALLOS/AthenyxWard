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
- `heuristics/` — Rule-based threat detection
- `util/` — Shared utilities

## Frontend structure** (under `src/app/`):
- Standalone components (no NgModules)
- Signals for reactivity
- SSR enabled (Express 5 server)

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
