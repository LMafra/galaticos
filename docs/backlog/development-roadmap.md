# Galáticos Development Roadmap

**Summary:** Single index of pending development work across backend, frontend, analytics, product, and UX. Read this when you need to decide what to build next. Each row links to the authoritative source — update status here **and** in the source backlog when work ships. Specialized checklists live in [fp-improvement-checklist.md](fp-improvement-checklist.md), [action-backlog.md](performance/action-backlog.md), and [advanced-analytics-backlog.md](analytics/advanced-analytics-backlog.md).

**Status values:** `open` · `done` · `partial` · `blocked` · `decision-needed` · `monitoring`  
**Priority:** `P0` (core/blocking) → `P3` (optional/experimental)

## Contents

- [How to use](#how-to-use)
- [Suggested reading order](#suggested-reading-order)
- [P0 — Core engineering](#p0--core-engineering)
- [P1 — Frontend performance](#p1--frontend-performance)
- [P1 — Product and domain](#p1--product-and-domain)
- [P2 — Routing and i18n](#p2--routing-and-i18n)
- [P2 — Analytics evolution](#p2--analytics-evolution)
- [P2 — FP optional follow-ups](#p2--fp-optional-follow-ups)
- [P3 — UX backlog](#p3--ux-backlog)
- [P3 — Accessibility gaps](#p3--accessibility-gaps)
- [P3 — Process and infra](#p3--process-and-infra)
- [Explicitly deferred](#explicitly-deferred)
- [Completed (reference)](#completed-reference)
- [Maintenance](#maintenance)

---

## How to use

1. Read top-down by priority (`P0` first).
2. Click **Source** links for implementation detail — this file does not duplicate checklists or route maps.
3. Archive UX items (`source: archive`) were reconciled against [ui-decisions.md](../reference/ui/ui-decisions.md) and CLJS code on 2026-06-11.
4. When a task ships: mark it `done` here, update the source backlog, and run `./bin/galaticos test`.

---

## Suggested reading order

| Order | Work | Why |
|-------|------|-----|
| 1 | Match-form picker optimization | Highest open UX/perf impact in a small scope |
| 2 | Q-02 business decision | Blocks any "reopen championship" feature |
| 3 | PT hash Phase 1 | When Portuguese URLs become a product priority |
| 4 | Analytics position efficiency | When Phase 3 gates are met |

---

## P0 — Core engineering

**Rule:** Never mix structural refactor and new features in the same PR ([fp-improvement-checklist.md](fp-improvement-checklist.md)).

| Item | Status | Priority | Source |
|------|--------|----------|--------|
| Phase B — Championships FP (`championship-store`, `domain/championships`, `logic/championships`, handlers, tests, delete OO files) | done | P0 | [fp-improvement-checklist.md](fp-improvement-checklist.md#completed-phases-0-a-b-c-d-e--global-closure) |
| Phase C — Matches FP (`domain/matches`, `logic/matches`, `match-store`, BRM rules, tests) | done | P0 | [fp-improvement-checklist.md](fp-improvement-checklist.md#completed-phases-0-a-b-c-d-e--global-closure) |
| Global FP closure — zero `service/*` / `repository/*` references | done | P0 | [fp-improvement-checklist.md](fp-improvement-checklist.md#completed-phases-0-a-b-c-d-e--global-closure) |
| Design map and migration patterns | reference | — | [fp-design-improvements.md](fp-design-improvements.md) · [functional-architecture.md](../reference/architecture/functional-architecture.md) |

---

## P1 — Frontend performance

From [action-backlog.md](performance/action-backlog.md).

| Item | Status | Priority | Source |
|------|--------|----------|--------|
| Match-form player/championship picker — avoid loading full catalog at scale | open | P1 | [action-backlog.md](performance/action-backlog.md#match-forms-match-new-match-new-in-championship-match-edit) |
| Match-form loading skeleton polish (section-level vs whole-form block) | partial | P1 | [action-backlog.md](performance/action-backlog.md) · skeleton exists for edit load and stats section in `matches.cljs` |
| Re-measure login/shell LCP on `/` after gzip deploy | monitoring | P1 | [action-backlog.md](performance/action-backlog.md#next-priorities) |
| Monitor dashboard TBT (~560 ms) for regressions | monitoring | P1 | [action-backlog.md](performance/action-backlog.md) |
| Monitor `/stats` Recharts bundle in `:pages` chunk on upgrades | monitoring | P1 | [action-backlog.md](performance/action-backlog.md) |
| Fill Lighthouse baselines for unmeasured routes (`player-detail`, `championship-detail`, teams, forms) | open | P1 | [action-backlog.md](performance/action-backlog.md#route-baselines-reference) |

---

## P1 — Product and domain

From [business-rules.md](../reference/domain/business-rules.md) and [business-rules-audit.md](../reference/domain/business-rules-audit.md).

| ID | Item | Status | Priority | Source |
|----|------|--------|----------|--------|
| Q-02 | Reopen / undo championship finalization — needs business rule | decision-needed | P1 | [business-rules.md](../reference/domain/business-rules.md#open-questions-and-identified-gaps) |
| Q-03 | Unify player search behavior (API accent normalization vs picker in-memory filter) | decision-needed | P1 | [business-rules.md](../reference/domain/business-rules.md#open-questions-and-identified-gaps) |
| Q-04 | Optional: exportable import conflict report | open | P2 | [business-rules-audit.md](../reference/domain/business-rules-audit.md) |
| BRM-14 | Optional: team filter on dashboard | open | P2 | [business-rules-audit.md](../reference/domain/business-rules-audit.md) |
| Q-01 | Align external diagrams to seasons collection model | open | P2 | [business-rules.md](../reference/domain/business-rules.md#open-questions-and-identified-gaps) |
| Q-06 | Formal dashboard card→route table for new cards | open | P3 | [business-rules.md](../reference/domain/business-rules.md#open-questions-and-identified-gaps) |

**Match/season feature checklist** (use when adding match or season features):

| Check | Status | Source |
|-------|--------|--------|
| Match create → active season (API + UI) | open | [matches-seasons-hybrid-stats.md](../reference/domain/matches-seasons-hybrid-stats.md#5-new-feature-checklist) |
| Update/delete → confirm RN | open | same |
| Stats → `merge-aggregated-stats` + job tests | open | same |
| Match without `season-id` → fan-out | open | same |
| New product RN in business-rules.md | open | same |

---

## P2 — Routing and i18n

Full EN→PT route map: [page-inventory.md](../reference/performance/page-inventory.md#portuguese-hash-migration-planned) — do not duplicate here.

| Phase | Scope | Status | Breaking? | Source |
|-------|--------|--------|-----------|--------|
| 1 | Register PT hash aliases alongside EN; both work | open | No | [page-inventory.md](../reference/performance/page-inventory.md#rollout-phases) |
| 2 | New links use PT; EN silently `replace-state` to PT | open | No | same |
| 3 | Remove EN routes or 404 with old-URL message | open | Yes | same |

| Dependency | Status | Source |
|------------|--------|--------|
| E2E PT hash routes | blocked (until Phase 1) | [testing-coverage.md](../reference/domain/testing-coverage.md) |

---

## P2 — Analytics evolution

**Phase 2** — decouple recomputation ([roadmap.md](../reference/analytics/roadmap.md), [architecture.md](../reference/analytics/architecture.md)):

| Item | Status | Priority | Source |
|------|--------|----------|--------|
| Async recalc after match CRUD | partial | P2 | In-process worker + intent maps shipped; see `player-stats-jobs` |
| Incremental and full reprocessing | partial | P2 | [architecture.md](../reference/analytics/architecture.md#jobs-de-agregados-player-stats) |
| Job observability (status endpoint, queue depth) | partial | P2 | `player-stats-jobs-status` handler |
| External queue (Redis/SQS), multiple workers, DLQ | open | P3 | [architecture.md](../reference/analytics/architecture.md) |
| Prometheus metrics for jobs | open | P3 | [architecture.md](../reference/analytics/architecture.md) |

**Phase 3 open** — [advanced-analytics-backlog.md](analytics/advanced-analytics-backlog.md):

| Item | Status | Priority | Source |
|------|--------|----------|--------|
| Efficiency by position (normalized by participation) | open | P2 | [advanced-analytics-backlog.md](analytics/advanced-analytics-backlog.md#derived-metrics) |
| Composite indicators by championship/round context | open | P3 | same |
| Experimental scouting metrics | open | P3 | same |
| Predictive layer gate: low inconsistency rate after reconciliation | monitoring | P2 | [advanced-analytics-backlog.md](analytics/advanced-analytics-backlog.md#predictive-layer-gates) |
| Predictive layer gate: measurable business hypothesis per experiment | open | P2 | same |
| User validation of metrics in decision rituals | open | P2 | [roadmap.md](../reference/analytics/roadmap.md#phase-3-advanced-metrics-and-predictive-layer) |
| Reliability monitoring as complexity grows | monitoring | P2 | [roadmap.md](../reference/analytics/roadmap.md#phase-3-advanced-metrics-and-predictive-layer) |

---

## P2 — FP optional follow-ups

From [fp-improvement-checklist.md](fp-improvement-checklist.md#optional-follow-ups).

| Item | Status | Priority |
|------|--------|----------|
| Extend `wrap-errors` for `{:error}` maps | open | P2 |
| Separate merge/pipeline IO vs pure calculation in aggregations | open | P2 |
| Jobs: intent maps (future evolution) | open | P2 |
| Property test `recompute == cache` | open | P2 |

---

## P3 — UX backlog

Impact × effort matrix from [notebooklm-response-uiux.md §20](../archive/notebookLM/uiux/notebooklm-response-uiux.md). Status reconciled against code on 2026-06-11.

| # | UX item | Status | Notes | Source |
|---|---------|--------|-------|--------|
| 1 | Undo on deletes (toast) | done | `delete-undo.cljs`, `toast-with-undo!` | [ui-decisions.md](../reference/ui/ui-decisions.md) |
| 2 | Bounded controls (steppers for goals/minutes) | done | `number-stepper` in `common.cljs`, `matches.cljs` | archive §20 |
| 3 | Fixed header on long match form | done | `match-form-sticky-header` | archive §20 |
| 4 | Tabular numbers in stats tables | done | `tabular-nums` across matches, teams, championships | archive §20 |
| 5 | Match form section skeletons | partial | Skeleton on edit load + stats section; whole-form polish still tracked | [action-backlog.md](performance/action-backlog.md) |
| 6 | Contextual player picker (enrolled roster) | partial | Match form loads enrolled roster; championship/team panels still call full `get-players` | [action-backlog.md](performance/action-backlog.md) |
| 7 | Local auto-save on match form | done | `match-draft.cljs` | [ui-decisions.md](../reference/ui/ui-decisions.md) |
| 8 | Season finalize checklist (forcing function) | open | Finalize API exists; no disabled-until-complete checklist UI | archive §20 wave 3 |
| 9 | Split-view player merge UI | partial | Three-step merge modal shipped; adjacent split view not built | archive §20 |
| 10 | Mobile card stack for wide tables | partial | Players list has manual cards toggle; matches form has mobile stat cards; no auto card stack on narrow viewports | archive §20 wave 3 |

**UX work waves** (archive §20 — use for grouping, not as separate backlog):

| Wave | Focus | Key open items |
|------|-------|----------------|
| Wave 1 | Safety and etiquette | Items 5–6 refinement |
| Wave 2 | Perceived performance | Breadcrumbs with lateral championship switcher (open) |
| Wave 3 | Forcing functions and mobile | Items 8, 10 |

**Aspirational (lower priority):**

| Item | Status | Source |
|------|--------|--------|
| `#/ui-lab` component showcase | done | `ui_lab.cljs`, `:ui-lab` route |
| Deep breadcrumbs with lateral championship switcher | open | archive §20 wave 2 |
| Copy audit table (Portuguese microcopy consistency) | open | archive §18 |

---

## P3 — Accessibility gaps

From archive §19, cross-checked with [ui-decisions.md](../reference/ui/ui-decisions.md) (44–48px touch targets).

| Item | Status | Source |
|------|--------|--------|
| Sidebar Lucide icons `aria-label` | done | `layout.cljs` nav items |
| Chart ARIA / screen-reader alternatives for Recharts | open | `charts.cljs` |
| Keyboard navigation on number steppers (arrow keys) | open | `number-stepper` uses buttons only |
| Inline API validation errors near fields | done | `apply-form-api-error!`, `form-error-summary` |
| Modal focus trap and initial focus | done | `common/modal` |
| Merge workflow text equivalents for before/after states | partial | `merge_modal.cljs` has labels; full SR narrative open |
| Table arrow-key navigation between stat cells | open | archive §19 |
| Squint test / contrast audit on brand-maroon buttons | open | archive §19 |

---

## P3 — Process and infra

| Item | Status | Priority | Source |
|------|--------|----------|--------|
| Future CI Lighthouse with JWT (no credentials in repo) | open | P3 | [methodology.md](../reference/performance/methodology.md) |
| Analytics operating cadence (weekly quality, biweekly roadmap) | monitoring | — | [operating-model.md](../reference/analytics/operating-model.md) |
| AI audit second-pass on changed files | open | P3 | [ai-assisted-code-audit.md](../reference/quality/ai-assisted-code-audit.md) |

---

## Explicitly deferred

Do not pursue without explicit approval ([ui-decisions.md](../reference/ui/ui-decisions.md), archive §20):

- Cosmetic redesign before match-form hierarchy is right
- Multi-step wizard on the match form
- 3D charts or WebGL on the dashboard
- Heavy login-page redesign

---

## Completed (reference)

Link to shipped work — avoid re-reading archive for these:

| Area | What shipped | Source |
|------|--------------|--------|
| FP migration | Phases 0–E complete (players, teams, seasons, championships, matches, analytics, CLJS reducer; global zero-OO) | [fp-improvement-checklist.md](fp-improvement-checklist.md#completed-phases-0-a-b-c-d-e--global-closure) |
| Performance 2026 | Code splitting, gzip, dashboard deferral, pagination, lazy leaderboards, CI Lighthouse smoke | [action-backlog.md](performance/action-backlog.md#completed-2026) |
| Analytics Phase 3 core | Derived metrics, insights API, dashboard section, CSV, predictive v1 | [advanced-analytics-backlog.md](analytics/advanced-analytics-backlog.md#completed-2026) |
| Analytics Phase 1 | Metrics catalog, data contracts, reconciliation playbook | [roadmap.md](../reference/analytics/roadmap.md#phase-1-semantic-standardization-and-data-quality) |

---

## Maintenance

When work ships:

1. Update the **source backlog** (checklist, baseline, or reference doc).
2. Change status in **this file**.
3. If a whole workstream is done, trim or archive the specialized backlog per [authoring.md](../authoring.md).
4. Update [docs/README.md](../README.md) and [llms.txt](../../llms.txt) when adding new streams.

```bash
# Verify FP closure (maintenance)
rg 'galaticos\.(service|repository)' src/ test/
./bin/galaticos test
```
