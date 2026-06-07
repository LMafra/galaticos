# Functional Programming — Galáticos Improvements

## Summary

Design map for Galáticos FP layout: `domain` + `logic` + protocols instead of service/repository layers. Read this when you plan a migration PR. **Open code work:** Phases B–C in [fp-improvement-checklist.md](fp-improvement-checklist.md). Analytics and CLJS reducer patterns are already shipped.

**Project-specific** translation of NotebookLM FP responses. Complements [design-and-db-improvements.md](../archive/notebookLM/oo/design-and-db-improvements.md) (OO/service layer oriented — **historical**).

**Main guide:** [functional-architecture.md](../reference/architecture/functional-architecture.md)  
**Prompts:** [notebooklm-prompts-fp.md](../archive/notebookLM/fp/notebooklm-prompts-fp.md)  
**Responses:** [notebooklm-response-fp.md](../archive/notebookLM/fp/notebooklm-response-fp.md)  
**Checklist:** [fp-improvement-checklist.md](fp-improvement-checklist.md)

---

## Consolidated decisions

| Theme | Decision |
|-------|----------|
| Namespaces | `domain/*` + `logic/*` + `handlers/*` + `db/*` + `db.protocol/*` |
| DI | `defprotocol` + `reify` in tests; **forbidden** `repo-call` / `ns-resolve` |
| Errors | `{:ok}` / `{:error}` in domain; `ex-info` in `logic/*`; HTTP middleware |
| Validation | `validation/entity.clj` + `comp`; no Malli/spec for now |
| Tests | pure domain → logic + `reify` → HTTP contract → optional Mongo |
| Analytics | pure rollups in `domain/analytics`; Mongo filters volume |
| Jobs | intent map + in-process executor |
| CLJS | reducer + `reaction`; isolated side effects |
| Order | 02 → 03 → 04 → 05 → 06 → 07 |
| Championships | Migrate to FP and **delete** OO (Plan 02) — do not keep legacy |

---

## OO → FP map

| Current OO pattern | Where it lives | FP target |
|--------------------|----------------|-----------|
| Service layer | `service/championships.clj` | `domain/championships.clj` + `logic/championships.clj` |
| Repository facade | `repository/championships.clj` | `db.protocol/championship-store.clj` + `db/*` |
| DI via `ns-resolve` | `repo-call` | protocol + explicit argument |
| Domain exceptions everywhere | `domain/errors.clj` in service | `errors/*!` only in `logic/*`; domain returns maps |
| Handler try/catch | `handlers/championships.clj` | unified middleware |
| `handlers/util.clj` | exception mapping | `middleware/errors.clj` |
| Manual validation | `validation/entity.clj` | keep + `comp` pipelines |
| OO test doubles | `service/championships_test.clj` | `domain/*_test` + `logic/*_test` with `reify` |
| Mongo + doseq merge | `db/aggregations.clj` | pure `domain/analytics` + separate persist |
| Coupled job | `analytics/player_stats_jobs.clj` | intent map + runner |
| CLJS setters | `state.cljs` | `dispatch!` + `app-reducer` + `reaction` |

---

## From → to (complete)

| Current (remove in code phase) | FP target |
|--------------------------------|-----------|
| `service/championships.clj` | `domain/championships.clj` + `logic/championships.clj` |
| `repository/championships.clj` | `db.protocol/championship-store.clj` |
| `handlers/util.clj` | `middleware/errors.clj` |
| `service/matches.clj` (if present) | `domain/matches.clj` + `logic/matches.clj` |
| `repository/matches.clj` (if present) | `db.protocol/match-store.clj` |
| `service/analytics` (Plan 06) | `logic/analytics.clj` or handlers + pure domain |
| `handlers/*` try/catch | middleware + direct handlers |
| `validation/entity.clj` | incremental evolution with `comp` |

---

## 1. Handlers and HTTP

**Current:** [`handlers/championships.clj`](../../src/galaticos/handlers/championships.clj) — parse, validate, `service/*`, `try/catch`.

**FP target:**

- Handler: validation → `(logic/operation store args)` → `resp/success`
- Errors: middleware [`wrap-errors`](../../src/galaticos/middleware/errors.clj) extended for `{:error}` when handlers return Result
- Remove dependency on [`handlers/util.clj`](../../src/galaticos/handlers/util.clj)

---

## 2. Domain and BRM rules

**Current:** [`service/championships.clj`](../../src/galaticos/service/championships.clj) — IO + duplicated `cond`.

**Extract to `domain/championships.clj`:**

| Rule | Suggested pure function |
|------|-------------------------|
| Delete championship | `can-delete?` → `{:ok}` / `{:error :conflict}` |
| Finalize | `finalization-decision` |
| Enroll player | `enrollment-decision` |
| Matches (Plan 03) | `validate-enrolled`, `validate-team-coherence` |

See [business-rules.md](../reference/domain/business-rules.md).

**Orchestration:** `logic/championships.clj` — fetch from store, call domain, persist or `throw` with `ex-info`.

---

## 3. Data access (Monger)

**Current:** [`db/championships.clj`](../../src/galaticos/db/championships.clj) — implicit `(db)`.

**Target:**

- Protocol `ChampionshipStore` / `MatchStore`
- `db/*` functions implement protocol or receive explicit `db`
- Pure transforms (enrich document) in `domain/*`

---

## 4. Analytics and cache

**Current:** [`db/aggregations.clj`](../../src/galaticos/db/aggregations.clj) + [`player_stats_jobs.clj`](../../src/galaticos/analytics/player_stats_jobs.clj).

**Target:**

- `domain/analytics.clj` — `goal-contribution`, `discipline-index`, `summarize-player-stats`, `merge-aggregated-stats` (pure)
- Testable invariant: `recompute(all-matches) == cache`
- Jobs: `{:op :recalc-stats :player-ids [...]}`

Analytics phases (E): done in code. [reconciliation-runbook.md](../reference/analytics/reconciliation-runbook.md).

---

## 5. CLJS frontend

**Current:** [`state.cljs`](../../src-cljs/galaticos/state.cljs), [`effects.cljs`](../../src-cljs/galaticos/effects.cljs).

**Target (Plan 07):**

- `app-reducer` + `dispatch!`
- `reaction` for loading, errors, filtered metrics
- Components without side effects

---

## 6. Tests

| Layer | Current | Target |
|-------|---------|--------|
| HTTP contract | `api_contract_test.clj` | Keep |
| Domain | mixed in service tests | `domain/*_test.clj` — maps only |
| Logic | `with-redefs` | `reify` store |
| Mongo integration | optional | minimal fixtures |

---

## 7. Championships pilot

**Decision:** **Option C** — refactor to FP in Phase B; delete `service/*` and `repository/*`. OO pilot still in repo until Phase B ships.

---

## Next steps

1. Read [functional-architecture.md](../reference/architecture/functional-architecture.md)
2. Follow [fp-improvement-checklist.md](fp-improvement-checklist.md) — Phase B onward (code phase)
3. Check off [fp-improvement-checklist.md](fp-improvement-checklist.md) as you implement
