# FP Improvement Checklist

## Summary

Open work for Galáticos’ **functional programming** migration. Safety net, documentation, players/teams/seasons rollout, and analytics are shipped. Remaining: championships and matches FP (Phases B–C), plus optional analytics refactors. **Team rule:** never mix structural refactor and new features in the same PR. **Closure:** zero `service/*` / `repository/*` and green `./bin/galaticos test`.

**Guide:** [functional-architecture.md](../reference/architecture/functional-architecture.md)  
**Design map:** [fp-design-improvements.md](fp-design-improvements.md)  
**Historical source:** [notebooklm-response-fp.md](../archive/notebookLM/fp/notebooklm-response-fp.md)

---

## Completed (Phases 0, A, D, E)

HTTP contract tests, `wrap-errors`, `validation/entity`, FP architecture doc, players/teams/seasons `domain/*` + `logic/*`, zero `service/*` / `repository/*` globally, `domain/analytics` + `logic/analytics`, derived API/insights/CSV, CLJS `dispatch!` / `app-reducer` / dashboard reactions. OO checklist superseded.

---

## Phase B — Championships FP (open)

- [ ] `db.protocol/championship-store.clj`
- [ ] `domain/championships.clj` — pure BRM rules
- [ ] `logic/championships.clj` — orchestration + `ex-info`
- [ ] Championships handlers without `try/catch` / without `service/*`
- [ ] Domain + logic tests (`reify`)
- [ ] **Delete** `service/championships.clj`, `repository/championships.clj`, `service/championships_test.clj`
- [ ] **Delete** `handlers/util.clj` if redundant
- [ ] `./bin/galaticos test` green

---

## Phase C — Matches FP (open)

- [ ] `domain/matches.clj`, `logic/matches.clj`, `db.protocol/match-store.clj`
- [ ] BRM: enrolled, team coherence, season, python-seed, recalc job intent
- [ ] Tests without `with-redefs` on global vars
- [ ] `./bin/galaticos test` green

---

## Optional follow-ups

- [ ] Extend `wrap-errors` for `{:error}` maps
- [ ] Separate merge/pipeline IO vs pure calculation in aggregations
- [ ] Jobs: intent maps (future evolution)
- [ ] Property `recompute == cache` in tests (additional coverage)

---

## Global closure

```bash
rg 'galaticos\.(service|repository)' src/ test/
# → zero results
./bin/galaticos test
```
