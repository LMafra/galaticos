# Galáticos documentation

Central documentation organized by **audience and topic**, not project status. Entry points: [concepts.md](concepts.md), [llms.txt](../llms.txt), [root README](../README.md).

## Start here by role

### New developer

You need Java, the Clojure CLI, and Docker for the default workflow.

| Doc | You will learn |
|-----|----------------|
| [concepts.md](concepts.md) | What terms mean (championship, season, aggregated-stats, RN-*) |
| [reference/development.md](reference/development.md) | How to install, run, test, and replicate CI locally |
| [reference/domain/business-rules.md](reference/domain/business-rules.md) | Domain rules (`RN-*`) the app enforces |
| [reference/quality/ai-assisted-code-audit.md](reference/quality/ai-assisted-code-audit.md) | Stack pitfalls when using AI on this codebase |

### Deploy and operations

| Doc | You will learn |
|-----|----------------|
| [reference/operations/production-runbook.md](reference/operations/production-runbook.md) | Deploy, backup, seed without data loss |
| [reference/operations/vps-hosting.md](reference/operations/vps-hosting.md) | VPS, Docker, Nginx, HTTPS checklist |
| [reference/operations/incident-deploy-vps-frontend-2026-05.md](reference/operations/incident-deploy-vps-frontend-2026-05.md) | Post-incident notes (May 2026) |

### Domain and data

| Doc | You will learn |
|-----|----------------|
| [reference/domain/mongodb-schema.md](reference/domain/mongodb-schema.md) | MongoDB collections, embedding, relationships |
| [reference/domain/matches-seasons-hybrid-stats.md](reference/domain/matches-seasons-hybrid-stats.md) | Matches, active seasons, hybrid stat model |
| [reference/domain/business-rules-audit.md](reference/domain/business-rules-audit.md) | Rules vs implementation audit snapshot |
| [reference/analytics/metrics-catalog.md](reference/analytics/metrics-catalog.md) | **Source of truth** for metric semantics and KPIs |

Do not redefine metrics in other docs; link to the catalog.

### UI contributor

| Doc | You will learn |
|-----|----------------|
| [reference/ui/ui-decisions.md](reference/ui/ui-decisions.md) | UX rules, Portuguese vocabulary, responsive shell |
| [reference/performance/page-inventory.md](reference/performance/page-inventory.md) | Routes, components, planned PT hash paths |
| [concepts.md](concepts.md) | Domain terms shown in the UI |

### Writing docs

| Doc | You will learn |
|-----|----------------|
| [authoring.md](authoring.md) | Style, structure, and maintenance conventions |

## Engineering

| Doc | Answers |
|-----|---------|
| [reference/architecture/functional-architecture.md](reference/architecture/functional-architecture.md) | OO → FP migration: `domain/*`, `logic/*`, tests |
| [reference/domain/testing-coverage.md](reference/domain/testing-coverage.md) | Cloverage, E2E coverage, CI thresholds |
| [backlog/fp-improvement-checklist.md](backlog/fp-improvement-checklist.md) | FP migration checklist (remaining code work) |

## Analytics program

| Doc | Answers |
|-----|---------|
| [reference/analytics/strategy.md](reference/analytics/strategy.md) | Vision, use cases |
| [reference/analytics/architecture.md](reference/analytics/architecture.md) | Pipelines, jobs, end-to-end flow |
| [reference/analytics/data-contracts.md](reference/analytics/data-contracts.md) | Versioned data contracts |
| [reference/analytics/data-quality.md](reference/analytics/data-quality.md) | Quality rules and incidents |
| [reference/analytics/reconciliation-runbook.md](reference/analytics/reconciliation-runbook.md) | Reconciliation operations |
| [reference/analytics/operating-model.md](reference/analytics/operating-model.md) | Roles and cadence |
| [reference/analytics/roadmap.md](reference/analytics/roadmap.md) | Phased roadmap |

## Performance

| Doc | Answers |
|-----|---------|
| [reference/performance/overview.md](reference/performance/overview.md) | Goals, dev vs release, JWT audit limits |
| [reference/performance/methodology.md](reference/performance/methodology.md) | Lighthouse methodology |
| [reference/performance/page-inventory.md](reference/performance/page-inventory.md) | Routes and components to measure |
| [backlog/performance/action-backlog.md](backlog/performance/action-backlog.md) | Open Lighthouse tasks |

Local Lighthouse JSON: `docs/perf-output/` (gitignored).

## Backlog

Executable pending work — not stable reference:

- [backlog/analytics/advanced-analytics-backlog.md](backlog/analytics/advanced-analytics-backlog.md)
- [backlog/performance/action-backlog.md](backlog/performance/action-backlog.md)
- [backlog/fp-design-improvements.md](backlog/fp-design-improvements.md)
- [backlog/fp-improvement-checklist.md](backlog/fp-improvement-checklist.md)

When backlog work ships, move knowledge to `reference/` or delete the item.

## Archive

Historical NotebookLM research and superseded checklists (optional reading):

- [archive/notebookLM/](archive/notebookLM/) — [oo/](archive/notebookLM/oo/) · [fp/](archive/notebookLM/fp/) · [uiux/](archive/notebookLM/uiux/)

## External references

- [Sports analytics (Wikipedia)](https://en.wikipedia.org/wiki/Sports_analytics)
- [Teradata — sports data analytics](https://www.teradata.com/insights/data-analytics/what-is-sports-data-analytics)

## Maintaining documentation

1. Put new **stable** docs under `docs/reference/<topic>/`.
2. Put **pending** work under `docs/backlog/`.
3. When done, move to `reference/` or delete the backlog item — do not leave all-`[x]` checklists in active docs.
4. Update this README and [llms.txt](../llms.txt).
5. One semantic source for metrics: [metrics-catalog.md](reference/analytics/metrics-catalog.md).
