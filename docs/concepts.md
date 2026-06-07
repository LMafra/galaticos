# Concepts

Canonical vocabulary for Galáticos documentation and code. Use these terms consistently (see [authoring.md](authoring.md)).

**Summary:** Galáticos manages a single squad’s players, championships, seasons, and matches. Match lineups hold per-player stats; `players.aggregated-stats` is a cache updated by analytics jobs. One admin authenticates with JWT.

You will see these terms in [business rules](reference/domain/business-rules.md), [MongoDB schema](reference/domain/mongodb-schema.md), API handlers, and the Portuguese UI ([ui-decisions.md](reference/ui/ui-decisions.md)).

## Product

| Term | Definition |
|------|------------|
| **Galáticos** | The squad/team managed in the app (seed creates team "Galáticos"). |
| **Admin** | Single authenticated operator (JWT). Not multi-tenant. |
| **Production** | Live app at https://www.galaticosfr.com.br |

## Domain entities

| Term | Definition |
|------|------------|
| **Championship** | Competition container (name, season label, format, status). |
| **Season** | Active or completed period within a championship; gates new match creation when completed. |
| **Team** | Organization entity; players belong to a team. |
| **Player** | Squad member with position, active flag, soft delete. |
| **Match** | Game record with opponent, date, championship/season links, embedded **player statistics**. |
| **Player statistics** | Per-player goals, assists, etc. embedded in a match document (source of truth for event-level stats). |
| **Aggregated stats** | Cached per-player (and per-championship) rollups in `players.aggregated-stats`, derived from matches. |

## Analytics

| Term | Definition |
|------|------------|
| **Metrics catalog** | Semantic source of truth for KPI names and formulas: [metrics-catalog.md](reference/analytics/metrics-catalog.md). |
| **Data contracts** | Versioned API/export shapes: [data-contracts.md](reference/analytics/data-contracts.md). |
| **Player stats job** | In-process job that incrementally or fully recomputes aggregated stats after match writes. |
| **Reconcile** | Operation to align cached aggregated stats with match data (`POST /api/aggregations/reconcile`). |

## Code layout (FP migration)

| Term | Definition |
|------|------------|
| **`domain/*`** | Pure business rules and validations (no I/O). |
| **`logic/*`** | Orchestration: calls domain + DB protocols. |
| **`db/*`** | Monger persistence (not `repository/*` — legacy removed in FP trail). |
| **`handlers/*`** | Thin HTTP layer over `logic/*`. |

## Rule identifiers

Business rules use stable IDs (`RN-AUTH-01`, `RN-MATCH-09`, etc.). Full definitions: [business-rules.md](reference/domain/business-rules.md).

## Related docs

- [Development guide](reference/development.md) — run, test, deploy locally
- [MongoDB schema](reference/domain/mongodb-schema.md) — collections and embedding
- [Matches, seasons, hybrid stats](reference/domain/matches-seasons-hybrid-stats.md) — implementation guide for match/season flows
