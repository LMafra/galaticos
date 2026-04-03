# Inventário de páginas (performance)

Fonte de verdade das rotas: [src-cljs/galaticos/routes.cljs](../../src-cljs/galaticos/routes.cljs).  
Mapeamento rota → UI: [src-cljs/galaticos/core.cljs](../../src-cljs/galaticos/core.cljs) (`current-page`).

Routing usa **hash** (`:use-fragment true` em `core.cljs`); no browser as URLs aparecem como `http://localhost:3000/#/dashboard`, etc.

| Nome Reitit | Path (fragmento após `#`) | Título (UI) Auth? | Componente principal | Exemplo de URL |
|-------------|---------------------------|-------------------|----------------------|----------------|
| `:home` | `/` | Dashboard (visitante pode ver dados limitados) — não exige login para ver | `galaticos.components.dashboard/dashboard` | `http://localhost:3000/#/` |
| `:login` | `/login` | Login — não | `galaticos.components.login/login-page` | `http://localhost:3000/#/login` |
| `:dashboard` | `/dashboard` | Dashboard — sim | `galaticos.components.dashboard/dashboard` | `http://localhost:3000/#/dashboard` |
| `:stats` | `/stats` | Estatísticas — sim | `galaticos.components.aggregations/aggregations-page` | `http://localhost:3000/#/stats` |
| `:players` | `/players` | Players — sim | `galaticos.components.players/player-list` | `http://localhost:3000/#/players` |
| `:player-new` | `/players/new` | New Player — sim | `galaticos.components.players/player-form` | `http://localhost:3000/#/players/new` |
| `:player-detail` | `/players/:id` | Player Details — sim | `galaticos.components.players/player-detail` | `http://localhost:3000/#/players/{objectId}` |
| `:player-edit` | `/players/:id/edit` | Edit Player — sim | `galaticos.components.players/player-form` | `http://localhost:3000/#/players/{objectId}/edit` |
| `:matches` | `/matches` | Matches — sim | `galaticos.components.matches/match-list` | `http://localhost:3000/#/matches` |
| `:match-new` | `/matches/new` | New Match — sim | `galaticos.components.matches/match-form` | `http://localhost:3000/#/matches/new` |
| `:match-new-in-championship` | `/matches/by-championship/:championship-id/new` | New Match — sim | `galaticos.components.matches/match-form` (com `path-params`) | `http://localhost:3000/#/matches/by-championship/{id}/new` |
| `:match-edit` | `/matches/:id/edit` | Edit Match — sim | `galaticos.components.matches/match-form` | `http://localhost:3000/#/matches/{id}/edit` |
| `:match-detail` | `/matches/:id` | Match Details — sim | `galaticos.components.matches/match-detail` | `http://localhost:3000/#/matches/{id}` |
| `:championships` | `/championships` | Championships — sim | `galaticos.components.championships/championship-list` | `http://localhost:3000/#/championships` |
| `:championship-new` | `/championships/new` | New Championship — sim | `galaticos.components.championships/championship-form` | `http://localhost:3000/#/championships/new` |
| `:championship-detail` | `/championships/:id` | Championship Details — sim | `galaticos.components.championships/championship-detail` | `http://localhost:3000/#/championships/{id}` |
| `:championship-season-detail` | `/championships/:id/seasons/:season-id` | Temporada — sim | `galaticos.components.championships/championship-season-detail` | `http://localhost:3000/#/championships/{id}/seasons/{season-id}` |
| `:championship-edit` | `/championships/:id/edit` | Edit Championship — sim | `galaticos.components.championships/championship-form` | `http://localhost:3000/#/championships/{id}/edit` |
| `:teams` | `/teams` | Teams — sim | `galaticos.components.teams/team-list` | `http://localhost:3000/#/teams` |
| `:team-new` | `/teams/new` | New Team — sim | `galaticos.components.teams/team-form` | `http://localhost:3000/#/teams/new` |
| `:team-detail` | `/teams/:id` | Team Details — sim | `galaticos.components.teams/team-detail` | `http://localhost:3000/#/teams/{id}` |
| `:team-edit` | `/teams/:id/edit` | Edit Team — sim | `galaticos.components.teams/team-form` | `http://localhost:3000/#/teams/{id}/edit` |

## Notas

- **`{id}` / `{objectId}`:** substituir por identificadores reais (ex.: após seed ou via UI). O formato depende da API e do MongoDB.
- **Ordem de rotas:** `/players/new` é estática e precede `/players/:id` em `routes.cljs`; o router Reitit resolve o conflito em runtime com `:conflicts nil`.
- **404:** rotas não casadas renderizam `not-found-page` em `core.cljs` (não há rota nomeada extra para isso).
