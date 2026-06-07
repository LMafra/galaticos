# Analytics Data Contracts

**Summary:** Versioned contracts (`vMAJOR.MINOR`) for match player statistics, player aggregated stats cache, and the player insights API. Read this when you change analytics shapes or API responses. Contract changes require updating this document, tests, and a reconciliation rollback plan.

## Versioning policy

- Format: `vMAJOR.MINOR`.
- `MAJOR`: incompatible change.
- `MINOR`: backward-compatible addition.
- Every change must update this document and the technical changelog.

## Contract 1: match player statistics

- **Name**: `match_player_statistics`
- **Current version**: `v1.0`
- **Collection**: `matches`
- **Field**: `player-statistics` (array)
- **Grain**: player-match

### Required fields

- `player-id` (valid ObjectId/string)

### Optional fields

- `player-name` (string)
- `position` (string)
- `goals` (number >= 0)
- `assists` (number >= 0)
- `yellow-cards` (number >= 0)
- `red-cards` (number >= 0)
- `minutes-played` (number >= 0)

### Contract rules

- `player-statistics` must be a non-empty vector.
- Missing numeric fields are treated as zero in aggregation.
- IDs must be normalized before persistence.

## Contract 2: player aggregated stats

- **Name**: `player_aggregated_stats`
- **Current version**: `v1.1`
- **Collection**: `players`
- **Field**: `aggregated-stats`

### Expected structure

- `total`: map of player total metrics.
- `by-championship`: vector of per-championship metrics (and optionally `:season`).

### Minimum fields in total

- `games`
- `goals`
- `assists`
- `titles`

### Optional fields (v1.1)

- `yellow-cards` (number >= 0) — rollup from matches
- `red-cards` (number >= 0) — rollup from matches
- `minutes-played` (number >= 0) — rollup from matches

The same optional fields may appear in each `by-championship` element.

### Derived metrics (not persisted in cache)

Computed in memory from the fields above via `galaticos.domain.analytics`:

- `goal-contribution`, `goal-contribution-per-game`, `discipline-index`, `minutes-per-goal`

### Contract rules

- Field must exist on all active players.
- Aggregate values must be reconcilable with `matches`.
- Full updates must record `updated-at`.

## Contract 3: player insights API

- **Name**: `player_insights_response`
- **Current version**: `v1.0`
- **Endpoint**: `GET /api/aggregations/players/:player-id/insights` (authenticated)

### Fields

- `derived` — map with derived metrics (`goal-contribution`, `goal-contribution-per-game`, `discipline-index`, `minutes-per-goal`)
- `readiness` — `{:ok boolean :reason keyword-or-nil :checks map}`; gate for predictive layer. Checks include player history (`min-games`, `min-buckets`, `min-years`) and operational health (`reconciliation-seen?`, `executor-healthy?` via `player_stats_job_meta` and in-process executor).
- `trend`, `risk`, `projection` — present when `readiness.ok` is true; `null` when false (HTTP **200** policy, no predictive numbers)
- `disclaimers` — vector of strings (includes experimental warning)
- `experiment-meta` — model metadata (`version`, `model`, `status`)

### Readiness policy

When `readiness.ok` is false, the API responds **200** with `trend`, `risk`, and `projection` null and `disclaimers` explaining why. Derived metrics (`derived`) remain available.

### Derived CSV export

- `GET /api/exports/dashboard.csv?include-derived=true` adds columns `CONTRIB GOL`, `CONTRIB/JOGO`, `DISCIPLINA` to the athletes block.

## Compatibility and deprecation

- Fields must not be removed without a deprecation window.
- New fields must be optional in the first rollout version.
- Consumer endpoints must support a transition period between versions.

## Contract change checklist

1. Update this document with the new version.
2. Update validation and aggregation tests.
3. Validate impact on endpoints and frontend.
4. Record rollback/reconciliation plan.
