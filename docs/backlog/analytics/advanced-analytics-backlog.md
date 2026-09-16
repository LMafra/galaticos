# Advanced Analytics Backlog

Summarized in [development-roadmap.md](../development-roadmap.md).

## Summary

Remaining analytics evolution after Phase 3 delivery (derived metrics, insights API, dashboard/UI, CSV). Read this when you prioritize new KPIs or predictive experiments. Metric definitions: [metrics-catalog.md](../../reference/analytics/metrics-catalog.md).

## Completed (2026)

Derived metrics (`goal_contribution`, `goal_contribution_per_game`, `discipline_index`, `minutes_per_goal`), insights API with trend/risk/projection v1, dashboard derived section, CSV `include-derived`, readiness gating, reconciliation hooks, experimental predictive v1 (trend, risk, projection).

## Open work

### Derived metrics

- [ ] Efficiency by position (normalized by participation)
- [ ] Composite indicators by championship/round context (optional)
- [ ] Experimental metrics for internal scouting (optional)

### Predictive layer gates

- [ ] Low inconsistency rate after continuous reconciliation (operational monitoring)
- [ ] Measurable business hypothesis per experiment

## Experimentation governance

Every experiment needs: explicit hypothesis, success metric, evaluation period, rollback criterion.
