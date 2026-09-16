# Page inventory (performance)

**Summary:** Route table for Lighthouse audits and performance backlog work. Read this when you add routes or measure page load. Lists Reitit name, hash path, auth gate, main component, and example URL. UI shell rules: [ui-decisions.md](../ui/ui-decisions.md). Update when `routes.cljs` or `core.cljs` changes. Auth column matches `protected-routes` in `routes.cljs` (not “login required to view”).

Source of truth for routes: [src-cljs/galaticos/routes.cljs](../../src-cljs/galaticos/routes.cljs).  
Route → UI mapping: [src-cljs/galaticos/core.cljs](../../src-cljs/galaticos/core.cljs) (`current-page`).

Routing uses **hash** (`:use-fragment true` in `core.cljs`); in the browser URLs look like `http://localhost:3000/#/dashboard`, etc.

Phase 1 Portuguese hash aliases are registered for most EN paths (see [Portuguese hash migration](#portuguese-hash-migration-planned)). Examples below use English paths.

**Auth legend:** **no** = viewable without session; **yes** = gated by `protected-routes` (unauthenticated users are redirected). Write routes (new/edit) and **all Teams** routes are gated. Read hubs (dashboard, stats, players, matches, championships and their details) are public.

| Reitit name | Path (fragment after `#`) | UI title — auth? | Main component | Example URL |
|-------------|---------------------------|------------------|----------------|-------------|
| `:home` | `/` | Dashboard (visitor may see limited data) — no | `galaticos.components.dashboard/dashboard` | `http://localhost:3000/#/` |
| `:login` | `/login` | Login — no (outside layout shell) | `galaticos.components.login/login-page` | `http://localhost:3000/#/login` |
| `:dashboard` | `/dashboard` | Dashboard — no | `galaticos.components.dashboard/dashboard` | `http://localhost:3000/#/dashboard` |
| `:stats` | `/stats` | Statistics — no | `galaticos.components.aggregations/aggregations-page` | `http://localhost:3000/#/stats` |
| `:players` | `/players` | Players — no | `galaticos.components.players/player-list` | `http://localhost:3000/#/players` |
| `:player-new` | `/players/new` | New Player — yes | `galaticos.components.players/player-form` | `http://localhost:3000/#/players/new` |
| `:player-detail` | `/players/:id` | Player Details — no | `galaticos.components.players/player-detail` | `http://localhost:3000/#/players/{objectId}` |
| `:player-edit` | `/players/:id/edit` | Edit Player — yes | `galaticos.components.players/player-form` | `http://localhost:3000/#/players/{objectId}/edit` |
| `:matches` | `/matches` | Matches — no | `galaticos.components.matches/match-list` | `http://localhost:3000/#/matches` |
| `:match-new` | `/matches/new` | New Match — yes (orphan: no chrome inbound link; UI prefers championship-scoped create) | `galaticos.components.matches/match-form` | `http://localhost:3000/#/matches/new` |
| `:matches-by-championship` | `/matches/championship/:championship-id` | Matches by championship — no | `galaticos.components.matches/championship-matches-page` | `http://localhost:3000/#/matches/championship/{id}` |
| `:match-new-in-championship` | `/matches/by-championship/:championship-id/new` | New Match — yes | `galaticos.components.matches/match-form` (with `path-params`) | `http://localhost:3000/#/matches/by-championship/{id}/new` |
| `:match-edit` | `/matches/:id/edit` | Edit Match — yes | `galaticos.components.matches/match-form` | `http://localhost:3000/#/matches/{id}/edit` |
| `:match-detail` | `/matches/:id` | Match Details — no | `galaticos.components.matches/match-detail` | `http://localhost:3000/#/matches/{id}` |
| `:championships` | `/championships` | Championships — no | `galaticos.components.championships/championship-list` | `http://localhost:3000/#/championships` |
| `:championship-new` | `/championships/new` | New Championship — yes | `galaticos.components.championships/championship-form` | `http://localhost:3000/#/championships/new` |
| `:championship-detail` | `/championships/:id` | Championship Details — no | `galaticos.components.championships/championship-detail` | `http://localhost:3000/#/championships/{id}` |
| `:championship-season-detail` | `/championships/:id/seasons/:season-id` | Season — no | `galaticos.components.championships/championship-season-detail` | `http://localhost:3000/#/championships/{id}/seasons/{season-id}` |
| `:championship-edit` | `/championships/:id/edit` | Edit Championship — yes | `galaticos.components.championships/championship-form` | `http://localhost:3000/#/championships/{id}/edit` |
| `:teams` | `/teams` | Teams — yes | `galaticos.components.teams/team-list` | `http://localhost:3000/#/teams` |
| `:team-new` | `/teams/new` | New Team — yes | `galaticos.components.teams/team-form` | `http://localhost:3000/#/teams/new` |
| `:team-detail` | `/teams/:id` | Team Details — yes | `galaticos.components.teams/team-detail` | `http://localhost:3000/#/teams/{id}` |
| `:team-edit` | `/teams/:id/edit` | Edit Team — yes | `galaticos.components.teams/team-form` | `http://localhost:3000/#/teams/{id}/edit` |
| `:ui-lab` | `/ui-lab` | UI Lab (dev) — no (outside layout shell) | `galaticos.components.ui-lab/ui-lab-page` | `http://localhost:3000/#/ui-lab` |

## Portuguese hash migration (planned)

Reitit route names (`:players`, etc.) stay English in code; only hash segments change for the UI. Implementation notes live in `routes.cljs`.

### Route map (EN → PT)

| Reitit name | Current hash | Proposed PT hash | Phase 1 registered? |
|-------------|--------------|------------------|---------------------|
| `:home` | `/` | `/` | n/a |
| `:login` | `/login` | `/entrar` | yes |
| `:dashboard` | `/dashboard` | `/painel` | yes |
| `:stats` | `/stats` | `/estatisticas` | yes |
| `:players` | `/players` | `/jogadores` | yes |
| `:player-new` | `/players/new` | `/jogadores/novo` | yes |
| `:player-detail` | `/players/:id` | `/jogadores/:id` | yes |
| `:player-edit` | `/players/:id/edit` | `/jogadores/:id/editar` | yes |
| `:matches` | `/matches` | `/partidas` | yes |
| `:match-new` | `/matches/new` | `/partidas/nova` | yes |
| `:matches-by-championship` | `/matches/championship/:championship-id` | `/partidas/campeonato/:championship-id` | yes |
| `:match-new-in-championship` | `/matches/by-championship/:championship-id/new` | `/partidas/campeonato/:championship-id/nova` | yes |
| `:match-detail` | `/matches/:id` | `/partidas/:id` | yes |
| `:match-edit` | `/matches/:id/edit` | `/partidas/:id/editar` | yes |
| `:championships` | `/championships` | `/campeonatos` | yes |
| `:championship-new` | `/championships/new` | `/campeonatos/novo` | yes |
| `:championship-detail` | `/championships/:id` | `/campeonatos/:id` | yes |
| `:championship-season-detail` | `/championships/:id/seasons/:season-id` | `/campeonatos/:id/temporadas/:season-id` | yes |
| `:championship-edit` | `/championships/:id/edit` | `/campeonatos/:id/editar` | yes |
| `:teams` | `/teams` | `/times` | **no** — gap vs plan |
| `:team-new` | `/teams/new` | `/times/novo` | yes |
| `:team-detail` | `/teams/:id` | `/times/:id` | **no** — gap vs plan |
| `:team-edit` | `/teams/:id/edit` | `/times/:id/editar` | yes |
| `:ui-lab` | `/ui-lab` | `/ui-lab` (dev) | n/a |

### Rollout phases

| Phase | Scope | Breaking? |
|-------|--------|-----------|
| **1** | Register each PT hash as an additional route; both EN and PT work; duplicate Lighthouse inventory lines | No |
| **2** | New links use PT; visiting EN hash silently `replace-state` to PT | No (EN bookmarks still work) |
| **3** | Remove EN routes or 404 with “old URL” message | Yes — requires release notes |

Hash SPAs have no HTTP 301; use `reitit.frontend.easy` + EN→PT lookup in phase 2.

### Risks

| Risk | Mitigation |
|------|------------|
| Shared bookmarks / links | Phases 1–2 keep EN; phase 3 needs changelog |
| Lighthouse scripts | Add PT example URLs in phase 1 |
| Reitit route order (`/new` vs `/:id`) | Mirror static-before-dynamic order in PT paths |
| E2E tests with fixed paths | Parametrize or switch to PT in phase 2 |

## Notes

- **`{id}` / `{objectId}`:** replace with real identifiers (e.g. after seed or via UI). Format depends on API and MongoDB.
- **Route order:** `/players/new` is static and precedes `/players/:id` in `routes.cljs`; Reitit resolves the conflict at runtime with `:conflicts nil`.
- **404:** unmatched hashes render `not-found-page` in `core.cljs` (no extra named route). Shortcuts: Dashboard, Jogadores, Partidas.
- **UI/UX audit:** [wave-0-audit.md](../../backlog/uiux/wave-0-audit.md).
