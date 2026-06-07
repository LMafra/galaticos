# Analytics Metrics Catalog

**Summary:** Canonical semantic source for platform sports metrics. Read this before exposing a KPI in API or UI. Each entry defines grain, formula, source fields, and interpretation. Formula changes require versioning and changelog communication.

## Catalog conventions

- **Canonical name**: unique metric identifier.
- **Grain**: calculation level (player-match, player-championship, etc.).
- **Formula**: calculation rule.
- **Source**: origin collection/field.
- **Interpretation**: how to read the metric.

## Base metrics

### games

- **Description**: total matches with recorded participation.
- **Grain**: player-championship and player-total.
- **Formula**: count of matches with an entry in `player-statistics`.
- **Source**: `matches.player-statistics.player-id`.

### goals

- **Description**: goals scored.
- **Grain**: player-match, player-championship, player-total.
- **Formula**: sum of `goals`.
- **Source**: `matches.player-statistics.goals`.

### assists

- **Description**: assists recorded.
- **Grain**: player-match, player-championship, player-total.
- **Formula**: sum of `assists`.
- **Source**: `matches.player-statistics.assists`.

### yellow_cards

- **Description**: yellow cards received.
- **Grain**: player-match, player-championship, player-total.
- **Formula**: sum of `yellow-cards`.
- **Source**: `matches.player-statistics.yellow-cards`.

### red_cards

- **Description**: red cards received.
- **Grain**: player-match, player-championship, player-total.
- **Formula**: sum of `red-cards`.
- **Source**: `matches.player-statistics.red-cards`.

### goals_per_game

- **Description**: offensive efficiency per match.
- **Grain**: player-championship and player-total.
- **Formula**: `goals / games`.
- **Rule**: if `games = 0`, returns `0`.

### assists_per_game

- **Description**: assist efficiency per match.
- **Grain**: player-championship and player-total.
- **Formula**: `assists / games`.
- **Rule**: if `games = 0`, returns `0`.

### titles

- **Description**: titles won.
- **Grain**: player-total.
- **Formula**: sum of approved increments on championship finalization.
- **Source**: `players.aggregated-stats.total.titles`.

## Derived metrics (v1.1)

### goal_contribution

- **Description**: total offensive contribution (goals + assists).
- **Grain**: player-championship and player-total.
- **Formula**: `goals + assists`.
- **Source**: `players.aggregated-stats` (base fields) or pure calculation in `galaticos.domain.analytics/goal-contribution`.
- **Interpretation**: how directly the player contributed to goals; useful for broad offensive rankings.

### goal_contribution_per_game

- **Description**: average offensive contribution per match.
- **Grain**: player-championship and player-total.
- **Formula**: `(goals + assists) / games`.
- **Rule**: if `games = 0`, returns `0`.
- **Implementation**: `galaticos.domain.analytics/goal-contribution-per-game`.

### discipline_index

- **Description**: weighted card index per match (red counts 3× yellow).
- **Grain**: player-championship and player-total.
- **Formula**: `(yellow-cards + 3 × red-cards) / games`.
- **Rule**: if `games = 0`, returns `0`.
- **Source**: `aggregated-stats` with `yellow-cards`, `red-cards`, `games` — no per-request `matches` scan.
- **Implementation**: `galaticos.domain.analytics/discipline-index`.

### minutes_per_goal

- **Description**: minutes played per goal scored.
- **Grain**: player-championship and player-total.
- **Formula**: `minutes-played / goals` when `goals > 0` and minutes quality is OK.
- **Minutes quality**: `minutes-played >= games` (average ≥ 1 min/match).
- **Rule**: returns `nil` when preconditions fail.
- **Implementation**: `galaticos.domain.analytics/minutes-per-goal`.

## Predictive insights (experimental, v1.0)

Exposed via `GET /api/aggregations/players/:player-id/insights` when `readiness.ok` is true. See [data-contracts.md](data-contracts.md) contract 3.

### trend

- **Description**: direction of offensive contribution (recent window vs previous).
- **Grain**: player-temporal (week/month buckets from `player-performance-evolution`).
- **Values**: `:direction` (`:up` | `:down` | `:stable`), `:delta`, per-window averages.
- **Implementation**: `galaticos.analytics.predictive/compute-trend`.

### risk

- **Description**: simple decline-risk label combining trend and `discipline-index`.
- **Values**: `:level` (`:low` | `:medium` | `:high`).
- **Implementation**: `galaticos.analytics.predictive/compute-risk`.

### projection

- **Description**: linear projection of offensive contribution for the next window.
- **Main field**: `:projected-goal-contribution`.
- **Implementation**: `galaticos.analytics.predictive/compute-projection`.

### readiness

- **Description**: gate before exposing trend/risk/projection.
- **Checks**: minimum matches, temporal buckets, distinct years, reconciliation recorded (`player_stats_job_meta`), healthy job queue.
- **Implementation**: `galaticos.analytics.readiness/evaluate`.

## Governance

- Every new metric must be added to this catalog before entering the API/UI.
- Formula changes require versioning and communication in the analytics changelog.
- [business-rules.md](../domain/business-rules.md) must reference this file to avoid semantic drift.
