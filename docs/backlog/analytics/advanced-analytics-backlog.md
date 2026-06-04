# Advanced Analytics Backlog

## Summary

Phase 3 (Plans 05–07) delivered derived metrics, insights API, dashboard/UI, and optional CSV columns. High-priority derived metrics and experimental predictive v1 (trend, risk, projection) are done; remaining work is medium/low metrics, operational reconciliation monitoring, and governed experiments before heavier ML.

## Objective

Prioritize evolution of derived metrics and prepare a foundation for a predictive layer with clear entry criteria.

## Status (2026)

**Phase 3 (Plans 05–07):** derived metrics, insights API, UI (dashboard, aggregations, player detail), and CSV with `include-derived` — **delivered**. Items below are marked done or optional/future.

## Derived metrics prioritization

### High priority — done

- [x] `goal_contribution`: `goals + assists`.
- [x] `goal_contribution_per_game`: `(goals + assists) / games`.
- [x] `discipline_index`: weighted card index per match.

### Medium priority

- [x] `minutes_per_goal` (when `minutes-played` reaches minimum quality).
- [ ] Efficiency by position (normalized by participation).
- [x] Moving-window trend (short vs medium term) — experimental via insights API.

### Low priority (optional)

- Composite indicators by championship/round context.
- Experimental metrics for internal scouting.

## Criteria to enable predictive layer

- [x] Minimum history and buckets (`readiness` in API).
- [x] Reconciliation recorded (`player_stats_job_meta`) when required.
- [x] Simple statistical baseline (linear trend, rule-based risk) before ML.
- [ ] Low inconsistency rate after continuous reconciliation (operational monitoring).
- [ ] Measurable business hypothesis per experiment.

## Predictive initiatives backlog

1. [x] Individual performance trend classification (experimental v1).
2. [x] Performance drop risk detection (experimental v1).
3. [x] Player contribution projection for next window (experimental v1).

## Experimentation governance

Every experiment must have:

- explicit hypothesis
- success metric
- evaluation period
- rollback criterion

## Dashboard and export deliverables

- [x] New derived metrics section on the dashboard.
- [x] CSV export with optional derived columns (`?include-derived=true`).
- [x] Interpretation notes (disclaimers in insights UI).
