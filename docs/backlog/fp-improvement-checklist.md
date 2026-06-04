# FP Improvement Checklist (from NotebookLM Responses)

## Summary

Phased checklist for Galáticos’ **functional programming** migration: safety net and docs are largely done; Plans 02–03 (championships, matches) remain; Plans 04–07 (rollout and analytics) are done in code with optional refactors left. Team rule: never mix structural refactor and new features in the same PR. Global closure: zero `service/*` / `repository/*` references and green `./bin/galaticos test`.

Checklist derived from [notebooklm-response-fp.md](../archive/notebookLM/notebooklm-response-fp.md). **Functional programming** vocabulary — distinct from the OO checklist in [improvement-checklist.md](../archive/notebookLM/improvement-checklist.md) (historical).

**Guide:** [functional-architecture.md](../reference/architecture/functional-architecture.md)  
**Documentation status:** consolidated responses; former sequential plans (00–07) removed — track work here.

**Team rule:** do not mix structural refactor with a new feature in the **same** PR.

---

## Phase 0: Safety net (Plan 01 — done)

- [x] HTTP contract tests (`api_contract_test.clj`)
- [x] Rule: refactor and feature in separate PRs
- [x] `galaticos.domain.errors` + `wrap-errors` middleware
- [x] `galaticos.validation.entity` at the HTTP boundary
- [ ] Extend `wrap-errors` for `{:error}` maps (implementation — Plan 02+)

---

## Phase A: FP documentation (done in this plan)

- [x] Decisions consolidated in `notebooklm-response-fp.md`
- [x] [functional-architecture.md](../reference/architecture/functional-architecture.md)
- [x] [fp-design-improvements.md](fp-design-improvements.md) updated
- [x] Former plans 00–07 consolidated into this checklist and [functional-architecture.md](../reference/architecture/functional-architecture.md)
- [x] OO checklist marked superseded

---

## Phase B: Plan 02 — Championships FP (code — pending)

- [ ] `db.protocol/championship-store.clj`
- [ ] `domain/championships.clj` — pure BRM rules
- [ ] `logic/championships.clj` — orchestration + `ex-info`
- [ ] Championships handlers without `try/catch` / without `service/*`
- [ ] Domain + logic tests (`reify`)
- [ ] **Delete** `service/championships.clj`, `repository/championships.clj`, `service/championships_test.clj`
- [ ] **Delete** `handlers/util.clj` if redundant
- [ ] `./bin/galaticos test` green

---

## Phase C: Plan 03 — Matches FP (code — pending)

- [ ] `domain/matches.clj`, `logic/matches.clj`, `db.protocol/match-store.clj`
- [ ] BRM: enrolled, team coherence, season, python-seed, recalc job intent
- [ ] Tests without `with-redefs` on global vars
- [ ] `./bin/galaticos test` green

---

## Phase D: Plan 04 — FP rollout (code — done)

- [x] `domain/*` + `logic/*` for players, teams, seasons (minimum)
- [x] `mongodb-schema.md` — indexes + denormalization
- [x] Zero `service/*` / `repository/*` across the repo

---

## Phase E: Plans 05–07 — Analytics (code — done)

- [x] `domain/analytics.clj` — pure formulas and rollups
- [x] `logic/analytics.clj` (no `service/analytics`)
- [x] Derived API + insights + CSV (Plan 06)
- [x] CLJS: `dispatch!` / `app-reducer`, `dashboard-derived-reaction`, `ensure-player-insights!` (Plan 07)
- [ ] Separate merge/pipeline IO vs pure calculation in aggregations (optional refactor)
- [ ] Jobs: intent maps (future evolution)
- [ ] Property `recompute == cache` in tests (additional coverage)

---

## Global closure criteria (code)

```bash
rg 'galaticos\.(service|repository)' src/ test/
# → zero results
./bin/galaticos test
```

---

## Reference: themes by phase

| Theme | Prompts | Plan phase |
|-------|---------|------------|
| Architecture / protocol | 1 | B, C, D |
| Pure domain | 2 | B, C |
| HTTP errors | 3 | B |
| Validation | 4 | B+ |
| Tests | 5 | B, C |
| Monger | 6 | B–E |
| Analytics | 7 | E |
| Jobs | 8 | C, E |
| CLJS | 9 | E |
| Order | 10 | README |

---

## Relation to OO checklist

| OO checklist | Status |
|--------------|--------|
| Phases 2–5 Repository/Service/DI | **Superseded** — see Phases B–D above |
| Phase 6 Errors/validation | Partially done (Plan 01); evolve in FP |
| Phase 7 Data/schema | Still applies; Plan 04 |
| Phase 8 Command/Strategy/Observer | **Do not apply** |
