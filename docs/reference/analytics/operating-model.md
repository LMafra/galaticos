# Analytics Operating Model

**Summary:** How the team governs analytics metrics: roles, cadence, and change rules. Read this when you own metric or contract changes. Weekly quality reviews, biweekly roadmap prioritization, monthly catalog adherence.

## Roles

### Analytics product owner

- Prioritizes analytics use cases.
- Approves metric changes and success criteria.

### Engineering

- Implements pipelines, validations, and data contracts.
- Maintains performance and reliability of analytics endpoints.

### Data analyst (or equivalent)

- Maintains semantic consistency of the metrics catalog.
- Conducts analyses and data quality reviews.

## Operational cadence

### Weekly

- Data quality and inconsistency review.
- Tracking of main KPIs and anomalies.

### Biweekly

- Prioritization of analytics roadmap improvements.
- Technical risk and documentation review.

### Monthly

- Metric evolution and catalog adherence review.
- Status update for the `docs/reference/analytics` track.

## Change governance

1. Change proposal (new metric/formula adjustment/new contract).
2. Impact assessment on API, frontend, and history.
3. Approval by product + engineering.
4. Implementation with documentation and tests.
5. Communication to internal users.

## Required artifacts per change

- Update [metrics-catalog.md](metrics-catalog.md) when semantics are affected.
- Update [data-contracts.md](data-contracts.md) for schema changes.
- Validation evidence in [testing-coverage.md](../domain/testing-coverage.md) (tests and reconciliation).

## Decision principles

- Clarity over complexity.
- Reproducibility of results is mandatory.
- Metrics without a formal definition do not go to production.
