# Athenyx Ward — Frontend

Angular 20 single-page application (SSR-enabled) for **Athenyx Ward**, the
email-threat-analysis platform. The SPA talks to the Spring Boot backend
documented in `../backend/README.md` — both modules live in the same
monorepo and share configuration through `AGENTS.md` / `SPEC.md`.

---

## Stack

- **Angular 20.3** standalone components, signals, `inject()` API
- **TypeScript 5.9** in `strict` mode (with `strictTemplates`,
  `noPropertyAccessFromIndexSignature`, `noImplicitOverride`, …)
- **Express 5** SSR server (`src/server.ts`)
- **RxJS 7.8** for HTTP, interceptors and guards
- **Karma + Jasmine** for unit tests
- **Prettier** (100-char width, single quotes, `angular` HTML parser)

No global NgModules; every component, directive and pipe is
`standalone: true`.

---

## Quick start

```bash
# 1. Install dependencies
npm install

# 2. Make sure the backend is running on http://localhost:8080
#    (the dev server proxies /api, /oauth2 and /login/oauth2 to it)

# 3. Start the dev server
npm start
# SPA served at http://localhost:4200
```

The Angular dev-server uses `proxy.conf.json` to forward:

| Path prefix | Target | Notes |
| --- | --- | --- |
| `/api` | `http://localhost:8080` | Sets `X-Forwarded-Host: localhost:4200` and rewrites the `app.auth.refresh-cookie-path` (`/api/auth`) back to `/` so cookies land on the SPA origin. |
| `/oauth2` | `http://localhost:8080` | Google OAuth2 start. |
| `/login/oauth2` | `http://localhost:8080` | Google OAuth2 callback. |

### Other commands

```bash
npm run build                  # Production build → dist/frontend
npm run watch                  # Development build with watch
npm test                       # Karma + Chrome (unit tests)
npm run serve:ssr:frontend     # Run the SSR bundle (port 4000)
```

---

## Configuration

Runtime settings live in `src/environments/`:

| File | Used by | Notes |
| --- | --- | --- |
| `environment.ts` | `ng build --configuration=development` (default `ng serve`) | `apiUrl: '/api'` |
| `environment.prod.ts` | `ng build --configuration=production` | Same shape, `production: true` |

`apiUrl` is relative so the dev-server proxy (or the same-origin SSR
deploy) handles routing without CORS gymnastics.

---

## Architecture

```
┌──────────────────────────────┐
│ main.ts (browser bootstrap)  │
│  └─ appConfig.ts            │ ← provideRouter, HttpClient + interceptors
│ main.server.ts (SSR)         │ ← same providers, hydration enabled
└──────────────┬───────────────┘
               │
       ┌───────▼────────┐
       │  app.ts (root) │  → <router-outlet /> + <app-toast-container />
       └───────┬────────┘
               │
   ┌───────────▼──────────────┐
   │ app.routes.ts            │
   │  ├ /login                │ (public)
   │  └ /  → LayoutComponent  │ (authGuard)
   │       ├ /home            │
   │       ├ /history         │
   │       ├ /stats           │
   │       ├ /plan            │
   │       ├ /reminders       │
   │       ├ /important       │
   │       ├ /settings        │
   │       ├ /help            │
   │       └ /admin/*         │ (authGuard + adminGuard)
   └──────────────────────────┘
```

### Folder layout (`src/app/`)

| Folder | Purpose |
| --- | --- |
| `components/` | Reusable UI building blocks (header, sidebar, layout, email-list, email-viewer, …) |
| `pages/` | Route-level components (home, login, settings, plan, reminders, admin/*, …) |
| `services/` | Singletons — `AuthService`, `EmailService`, `AvatarService`, `ToastService` |
| `guards/` | `authGuard`, `adminGuard` — canActivate functions |
| `interceptors/` | `refreshInterceptor`, `errorToastInterceptor`, `http-error-messages` |
| `models/` | Plain TS interfaces shared across the app |
| `pipes/` | `EmailDatePipe` and other standalone pipes |
| `app.config.ts` | Providers (router, HttpClient, hydration, interceptors) |
| `app.routes.ts` | Route table (lazy-loaded with `loadComponent`) |

### State management

The app favours **Angular signals** for component-local and lightweight
shared state:

- `AuthService.currentUser` — current `UserInfo`
- `AuthService.refreshFailed` — flipped to `true` when `/auth/refresh`
  fails (used by the interceptor to navigate to `/login`)
- `ToastService.toasts` — visible toasts
- `AvatarService.gravatarHashes / failedGravatars / failedFavicons` —
  per-renderer caches for sender avatars

There is no NgRx or other state library — keep it that way unless the
codebase outgrows signals.

### Authentication & HTTP

- The SPA authenticates with Google via Spring Security on the backend.
  `/login` calls `/api/auth/login-url` to get the redirect URL, then
  `window.location.assign`s to it.
- Tokens are stored exclusively in `HttpOnly` cookies set by the
  backend. The SPA never reads them.
- `JwtAuthenticationFilter` on the backend is fed by either the cookie
  or `Authorization: Bearer …` headers; the SPA simply uses
  `withCredentials: true` on every cross-origin request.
- `refreshInterceptor` (`functional`) catches 401s, calls
  `/api/auth/refresh` exactly once (in-flight is shared via
  `shareReplay`) and replays the original request.
- `errorToastInterceptor` converts non-401 errors into toast
  notifications using `http-error-messages.ts` (Spanish messages keyed
  by status code; backend `error` field wins when present).

### Route guards

- `authGuard` — calls `AuthService.checkAuth()` (which hits
  `/api/auth/me`). If unauthenticated, redirects to `/login`. Bypassed
  during SSR.
- `adminGuard` — checks the cached `currentUser().role === 'ADMIN'`
  and shows a toast + redirect to `/home` otherwise. Runs after
  `authGuard` so the user signal is populated.

### Accessibility

- Accessibility mode is toggled server-side (`User.accessibilityMode`)
  and exposed via the cached `UserInfo`. The CSS uses the
  `app-a11y-on` / `app-a11y-off` attribute selectors on the root to
  switch between large-text/high-contrast (default) and the dense
  professional layout.
- All interactive components use semantic HTML and respect ARIA
  attributes; the SSR build means the first paint is accessible
  without depending on JavaScript.
- Spanish-only UI by design (`SPEC.md` § Language Support).

### SSR / hydration

- The Express 5 server (`src/server.ts`) renders the SPA for SEO and
  fast first paint.
- `provideClientHydration(withEventReplay())` is wired in
  `app.config.ts`; the same providers are reused on the server via
  `app.config.server.ts`.
- HTTP requests are short-circuited in interceptors and guards when
  `!isPlatformBrowser(...)`, so SSR never triggers a refresh loop.

---

## Internationalisation

All UI strings, toasts and the AI layer are **Spanish-only** at the
moment. The architecture is kept i18n-friendly: every user-facing
string lives in templates, not the code, and the `THREAT_CATEGORY_LABELS`
const in `models/email-analysis.model.ts` is the one place where the
labels are centralised. Adding a new locale would mean swapping those
constants for a translation loader and wiring Angular's `$localize`.

---

## Testing

Karma + Jasmine with the `@angular/build:karma` builder. Current spec
coverage focuses on the parts of the app that have stable contracts
(models, services, guards, interceptors, layout, page shells).

| Spec | Subject |
| --- | --- |
| `app.spec.ts` | Root component creation + router outlet |
| `interceptors/refresh.interceptor.spec.ts` | 401 retry, blacklist, SSR skip |
| `interceptors/error-toast.interceptor.spec.ts` | Toast + SSR skip |
| `guards/admin.guard.spec.ts` | ADMIN allowed, others redirected |
| `services/toast.service.spec.ts` | Stack, dedupe, dismiss, clear |
| `components/layout/layout.spec.ts` | Sidebar toggle + lifecycle |
| `components/page-shell/page-shell.spec.ts` | Render slot |
| `components/sidebar/sidebar.spec.ts` | Navigation items |
| `components/email-analysis/email-analysis.spec.ts` | Risk badge + actions |
| `pages/*/*.spec.ts` | Smoke tests for every page route |

### Running tests

```bash
npm test

# Single file
npm test -- --include='src/app/services/toast.service.spec.ts'

# Watch mode
npm test -- --watch
```

Karma uses ChromeHeadless by default (see `angular.json`). If Chrome
isn't installed locally, run `npx puppeteer browsers install chrome` or
use the system's Chrome.

---

## Code style

- **Prettier** with 100-char width, single quotes, `angular` parser for
  HTML (configured in `package.json`).
- No ESLint yet — keep imports sorted, prefer `inject()` over
  constructor DI, prefer signals for new component state.
- For RxJS, **always** use the `takeUntil(this.onDestroy)` pattern (see
  `AGENTS.md` § Subscription Management). `Subject` + `complete()` in
  `ngOnDestroy()` is mandatory for any subscription.
- Do not add comments unless the user asks for them.

---

## Known limitations (Sprint 1)

- AI analysis and the heuristics dashboard are not yet wired in the UI
  (Sprint 2 and Sprint 3 respectively).
- i18n is Spanish-only.
- No ESLint configured — relying on Prettier + strict TypeScript.
- The legacy `ng e2e` command in the Angular CLI default README is
  not set up here; the project does not currently include Cypress or
  Playwright.
