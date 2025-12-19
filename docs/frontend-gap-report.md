## Frontend Gap Report

Scope: CLJS SPA (Reagent/Reitit). Priority: functional/UX bugs and correctness.

### Critical Gaps (bugs/correctness)
- Fixed: API client accepts 200–204, surfaces backend error bodies, attaches bearer token, and uses env-configured base URL. File: `src-cljs/galaticos/api.cljs`.
- Fixed: Match creation form now uses real championship ids, captures player-statistics, validates required fields, and prevents submit while sending. File: `src-cljs/galaticos/components/matches.cljs`.
- Fixed: Auth is bootstrapped via `check-auth` on init/route change, token stored, and routes/components gate unauthenticated users. Files: `src-cljs/galaticos/effects.cljs`, `state.cljs`, `core.cljs`, `components/layout.cljs`.
- Fixed: Views render per-resource loading/error states with retry controls; dashboard/lists no longer hang silently. Files: `src-cljs/galaticos/components/dashboard.cljs`, `players.cljs`, `matches.cljs`, `championships.cljs`, `state.cljs`, `effects.cljs`.
- Fixed: Router now includes a 404 fallback; unmatched paths render a not-found page instead of hanging. Files: `src-cljs/galaticos/routes.cljs`, `src-cljs/galaticos/core.cljs`.
- Fixed: Navigation uses SPA push-state (no hard reloads) for list/detail/form flows. Files: `src-cljs/galaticos/components/championships.cljs`, `matches.cljs`, `players.cljs`.
- Fixed: Per-resource loading/error flags replace the global spinner for data fetches; detail atoms keep local loading/error handling. Files: `src-cljs/galaticos/state.cljs`, `src-cljs/galaticos/effects.cljs`, `components/*`.
- Fixed: API base URL configurable via env/window; bearer Authorization added when token exists. File: `src-cljs/galaticos/api.cljs`.

### Notable UX/robustness gaps
- Lists render only a static empty message; no retry/refresh controls after errors.
- Detail views don’t handle missing/404 responses distinctly; show “Carregando...” indefinitely if result is nil.
- No input validation on client forms before submit (e.g., empty fields, date format).
- No caching invalidation: once `*-loaded?` is true, data never refreshes unless page reloads.

### Recommended Refactors
- Fix API client: accept 200–204 success codes; surface backend error bodies; add configurable `api-base-url` via env/build, and keep `with-credentials?` aligned with backend CORS/auth.
- Add auth bootstrap (call `check-auth` on app init/route change), store user, and gate routes/components that require login.
- Replace hard reload navigation with SPA navigation via `routes/href` + `rfe/push-state!` (or anchor with `preventDefault`).
- Add per-resource loading/error state and render branches: dashboard, players, matches, championships should show error + retry, not “Carregando…” forever.
- Fix match form: correct championship option values and add player-statistics UI to satisfy backend contract; validate required fields before submit.
- Add a 404 route and fallback component; handle invalid IDs with user-friendly messages.
- Add data refresh/invalidation strategy (e.g., timestamp-based refetch, manual refresh).

### Priority Fix Batch (done)
- API client correctness: Accepts 200–204, propagates backend errors, configurable `api-base-url`, and bearer token support. Acceptance met: create/update/delete resolve on 201/204 and show server error text on failures. File: `src-cljs/galaticos/api.cljs`.
- Auth bootstrap and guards: `check-auth` on init/route change, user/token stored, header shows user, protected views gate unauth state. Acceptance met: dashboard shows user when session valid; unauth state is explicit. Files: `src-cljs/galaticos/effects.cljs`, `state.cljs`, `core.cljs`, `components/layout.cljs`.
- Navigation SPA-only: Replaced hard reloads with SPA push-state so state persists. Acceptance met: list rows/buttons navigate without full reload. Files: `src-cljs/galaticos/components/championships.cljs`, `matches.cljs`, `players.cljs`.
- Match form payload: Uses real championship ids, player-statistics input, client validation, and disables submit while sending. Acceptance met: backend receives expected payload with player stats. File: `src-cljs/galaticos/components/matches.cljs`.
- Loading/error handling per view: Per-resource loading/error flags and retry UI added; lists/dashboard no longer hang on errors. Files: `src-cljs/galaticos/state.cljs`, `effects.cljs`, `components/*`.
- 404/fallback route: Added route and component for unknown paths. Acceptance met: unknown path renders 404 page, not perpetual “Loading…”. Files: `src-cljs/galaticos/routes.cljs`, `core.cljs`.

### Validation Checklist (remaining areas)
- Error surfaces: Each view shows backend error message and offers retry; global error is cleared on retry/nav.
- Data freshness: Provide manual refresh or time-based refetch for players/matches/championships/dashboard.
- Env/config: API base URL varies by env; credentials flag matches backend CORS policy.
- Forms: Client-side validation for empty/format (dates, ids); prevent submit while loading.
- State cleanup: Detail atoms reset on unmount; in-flight requests cancelled or ignored after route change.
- Accessibility: Buttons/links clickable via keyboard; tables have headers; color-only indicators avoided.

