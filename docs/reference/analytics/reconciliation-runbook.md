# Analytics Data Reconciliation Runbook

**Summary:** How you reconcile source of truth (`matches.player-statistics`) with cache (`players.aggregated-stats`). Read this after match fixes or before weekly rituals. Covers daily full runs, sample checks, sync/async API, dev CLI, and severity playbook.

## Before you start

- You need admin JWT (or dev REPL) for API reconciliation.
- After editing match stats, fix the match document first, then reconcile — see [production-runbook.md](../operations/production-runbook.md).

## Operational frequency

- Full reconciliation: daily.
- Quick (sample) reconciliation: before weekly performance rituals.
- On-demand reconciliation: after incidents or calculation changes.

## Data SLOs

- Latency to reflect a match in metrics: within 5 minutes.
- Critical divergence allowed after reconciliation: 0 cases.
- Non-critical inconsistency rate: less than 1% of evaluated records.

## API (full recalculation)

- **Synchronous (default):** `POST /api/aggregations/reconcile` (authenticated). The API validates integrity, runs full recompute on the request thread, and responds with `updated` and a message. Use when immediate confirmation is needed.
- **Asynchronous:** `POST /api/aggregations/reconcile?async=true` (authenticated). Responds **202** with `job-id`; recompute runs on the in-process executor. Use for long windows or shorter client connection time. Track progress via structured logs (filter by `:job-id` and `galaticos.event/player-stats-refresh`).
- **CLI (dev):** `clojure -M:dev -m galaticos.tasks.reconcile-player-stats` — full reconciliation via `update-all-player-stats` and log of players missing `:pre-match-stats` / `:baseline-match-rollup` (see [hybrid guide](../domain/matches-seasons-hybrid-stats.md#troubleshooting-goals-jump-on-match-create)).
- **Worker status (read):** `GET /api/aggregations/player-stats-jobs` (authenticated) returns last success (incremental/full) persisted and simple executor metrics (queue, active). Useful in incidents without log-only access. See [architecture.md — Player stats aggregate jobs](architecture.md#jobs-de-agregados-player-stats).

## Execution checklist

1. Verify referential integrity (`player-id`, `championship-id`).
2. Run full aggregate recalculation routine.
3. Compare a sample of critical metrics:
   - `games`
   - `goals`
   - `assists`
   - `titles`
   - `yellow-cards`, `red-cards`, `minutes-played` (v1.1)
   - derived: `discipline-index`, `goal-contribution` (via `galaticos.domain.analytics` on cache)
4. Record result (ok, warnings, errors).
5. If divergence exists, classify severity and run the playbook.

## Incident severity

- **P1**: critical metrics incorrect in production with decision impact.
- **P2**: partial inconsistency with limited impact.
- **P3**: localized inconsistency with no direct decision impact.

## Incident playbook

1. Classify P1/P2/P3.
2. Identify origin (input, transformation, aggregation, consumption).
3. Run full reconciliation.
4. Validate result with sample and dashboard.
5. Record root cause and preventive action.

## Minimum evidence per run

- Execution date/time.
- Scope (full, sample, on-demand).
- Divergence count.
- Final status: resolved or pending with action plan.
