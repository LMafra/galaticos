# Page inventory (performance)

**Summary:** Route table for Lighthouse and backlog work: Reitit name, hash path, auth, main component, and example URL. Update when `routes.cljs` or `core.cljs` changes.

Source of truth for routes: [src-cljs/galaticos/routes.cljs](../../src-cljs/galaticos/routes.cljs).  
Route → UI mapping: [src-cljs/galaticos/core.cljs](../../src-cljs/galaticos/core.cljs) (`current-page`).

Routing uses **hash** (`:use-fragment true` in `core.cljs`); in the browser URLs look like `http://localhost:3000/#/dashboard`, etc.

| Reitit name | Path (fragment after `#`) | UI title — auth? | Main component | Example URL |
|-------------|---------------------------|------------------|----------------|-------------|
| `:home` | `/` | Dashboard (visitor may see limited data) — login not required to view | `galaticos.components.dashboard/dashboard` | `http://localhost:3000/#/` |
| `:login` | `/login` | Login — no | `galaticos.components.login/login-page` | `http://localhost:3000/#/login` |
| `:dashboard` | `/dashboard` | Dashboard — yes | `galaticos.components.dashboard/dashboard` | `http://localhost:3000/#/dashboard` |
| `:stats` | `/stats` | Statistics — yes | `galaticos.components.aggregations/aggregations-page` | `http://localhost:3000/#/stats` |
| `:players` | `/players` | Players — yes | `galaticos.components.players/player-list` | `http://localhost:3000/#/players` |
| `:player-new` | `/players/new` | New Player — yes | `galaticos.components.players/player-form` | `http://localhost:3000/#/players/new` |
| `:player-detail` | `/players/:id` | Player Details — yes | `galaticos.components.players/player-detail` | `http://localhost:3000/#/players/{objectId}` |
| `:player-edit` | `/players/:id/edit` | Edit Player — yes | `galaticos.components.players/player-form` | `http://localhost:3000/#/players/{objectId}/edit` |
| `:matches` | `/matches` | Matches — yes | `galaticos.components.matches/match-list` | `http://localhost:3000/#/matches` |
| `:match-new` | `/matches/new` | New Match — yes | `galaticos.components.matches/match-form` | `http://localhost:3000/#/matches/new` |
| `:match-new-in-championship` | `/matches/by-championship/:championship-id/new` | New Match — yes | `galaticos.components.matches/match-form` (with `path-params`) | `http://localhost:3000/#/matches/by-championship/{id}/new` |
| `:match-edit` | `/matches/:id/edit` | Edit Match — yes | `galaticos.components.matches/match-form` | `http://localhost:3000/#/matches/{id}/edit` |
| `:match-detail` | `/matches/:id` | Match Details — yes | `galaticos.components.matches/match-detail` | `http://localhost:3000/#/matches/{id}` |
| `:championships` | `/championships` | Championships — yes | `galaticos.components.championships/championship-list` | `http://localhost:3000/#/championships` |
| `:championship-new` | `/championships/new` | New Championship — yes | `galaticos.components.championships/championship-form` | `http://localhost:3000/#/championships/new` |
| `:championship-detail` | `/championships/:id` | Championship Details — yes | `galaticos.components.championships/championship-detail` | `http://localhost:3000/#/championships/{id}` |
| `:championship-season-detail` | `/championships/:id/seasons/:season-id` | Season — yes | `galaticos.components.championships/championship-season-detail` | `http://localhost:3000/#/championships/{id}/seasons/{season-id}` |
| `:championship-edit` | `/championships/:id/edit` | Edit Championship — yes | `galaticos.components.championships/championship-form` | `http://localhost:3000/#/championships/{id}/edit` |
| `:teams` | `/teams` | Teams — yes | `galaticos.components.teams/team-list` | `http://localhost:3000/#/teams` |
| `:team-new` | `/teams/new` | New Team — yes | `galaticos.components.teams/team-form` | `http://localhost:3000/#/teams/new` |
| `:team-detail` | `/teams/:id` | Team Details — yes | `galaticos.components.teams/team-detail` | `http://localhost:3000/#/teams/{id}` |
| `:team-edit` | `/teams/:id/edit` | Edit Team — yes | `galaticos.components.teams/team-form` | `http://localhost:3000/#/teams/{id}/edit` |

## Notes

- **`{id}` / `{objectId}`:** replace with real identifiers (e.g. after seed or via UI). Format depends on API and MongoDB.
- **Route order:** `/players/new` is static and precedes `/players/:id` in `routes.cljs`; Reitit resolves the conflict at runtime with `:conflicts nil`.
- **404:** unmatched routes render `not-found-page` in `core.cljs` (no extra named route).
