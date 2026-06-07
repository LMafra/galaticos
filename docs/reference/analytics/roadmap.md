# Sports Data Analytics Roadmap

**Summary:** Three-phase roadmap: Phase 1 semantic standardization and data quality, Phase 2 decoupled recomputation and job observability, Phase 3 derived metrics and gated predictive insights. Read this when you prioritize analytics work. Strategy context: [strategy.md](strategy.md).

## Phase 1: Semantic standardization and data quality

### Objectives

- Establish a reliable metrics foundation.
- Reduce ambiguity between functional and analytics documentation.

### Deliverables

- Official metrics catalog.
- Versioned data contracts.
- Reconciliation routine and quality playbook.
- Analytics-oriented documentation index.

### Exit criteria

- Critical metrics with a single definition.
- Periodic reconciliation operationalized.
- No documentation conflicts between rules and catalog.

## Phase 2: Decoupling analytics recomputation

### Objectives

- Improve transactional flow resilience and performance.
- Allow reprocessing without direct impact on match CRUD.

### Deliverables

- Async processing plan for recalculation.
- Incremental and full reprocessing strategy.
- Analytics job observability.

### Exit criteria

- Lower latency on match operations.
- Controlled reprocessing with traceability.

## Phase 3: Advanced metrics and predictive layer

### Objectives

- Increase analytics depth for technical and sports management decisions.

### Deliverables

- Derived contribution and contextual efficiency metrics.
- First trend/risk models (when data supports them).
- Dashboard and export evolution for weekly decisions.
- Prioritized advanced analytics backlog and model entry criteria.

### Exit criteria

- [x] Derived metrics and tops exposed in API and dashboard.
- [x] Insights with `readiness` gate and disclaimers in UI.
- [x] CSV export with optional derived columns.
- [ ] Recurring use of new metrics in decision rituals (user validation).
- [ ] Reliability maintained as complexity grows (ongoing monitoring).

## Cross-cutting dependencies

- Continuous data quality.
- Metric regression tests.
- Ongoing updates to analytics documentation.

## Execution references

- Advanced analytics backlog: [advanced-analytics-backlog.md](../../backlog/analytics/advanced-analytics-backlog.md).
- Aggregate jobs and decoupling (Phase 2): [architecture.md](architecture.md#jobs-de-agregados-player-stats).
