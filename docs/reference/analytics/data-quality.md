# Data Quality for Analytics

**Summary:** Quality dimensions (completeness, consistency, uniqueness, reconciliation) and operational controls for analytics. Read this when you investigate metric trust issues or define SLOs. Incident severities P1–P3 map to a short response playbook.

## Quality dimensions

### Completeness

- Matches must include `championship-id`.
- `player-statistics` must exist and be non-empty.

### Consistency

- `player-id` in matches must reference an existing player.
- `championship-id` must reference an existing championship.
- Numeric fields must be non-negative.

### Uniqueness and duplication

- Avoid accidental duplicate entries for the same player in the same match.
- Review import rules to avoid duplicating historical records.

### Reconciliation

- `players.aggregated-stats` must match recalculation from `matches`.
- Divergences must be recorded with counts by severity.

## Operational controls

### Recommended routine checks

1. Referential integrity verification.
2. Minimum `player-statistics` shape validation.
3. Periodic aggregate reconciliation.
4. Audit of anomalies in extreme metrics.

### Suggested SLOs

- Metric update after match: within 5 minutes.
- Full reconciliation: at least once per day.
- Critical quality error rate: less than 1% of records.

## Data incident handling

### Severities

- **P1**: critical metrics incorrect in production.
- **P2**: partial inconsistency affecting some reports.
- **P3**: low inconsistency with no direct impact on decisions.

### Summary playbook

1. Detect and classify severity.
2. Isolate inconsistency origin (input, transformation, or aggregation).
3. Run reconciliation and validate correction.
4. Record root cause and preventive action.

## Responsibilities

- Engineering: instrumentation, fixes, and check automation.
- Product/analytics: impact validation on key metrics.
- Operations: alert monitoring and playbook execution.

## Operational references

- Reconciliation runbook: [reconciliation-runbook.md](reconciliation-runbook.md).
- Evolution roadmap: [roadmap.md](roadmap.md).
