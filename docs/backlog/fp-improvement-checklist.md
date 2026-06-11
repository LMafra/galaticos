# FP Improvement Checklist

Summarized in [development-roadmap.md](development-roadmap.md).

## Summary

Galáticos **functional programming** migration is **closed** (Phases 0–E, global zero-OO). Remaining: optional analytics/handler refactors below. **Team rule:** never mix structural refactor and new features in the same PR. **Closure gate:** zero `service/*` / `repository/*` and green `./bin/galaticos test`.

**Guide:** [functional-architecture.md](../reference/architecture/functional-architecture.md)  
**Design map:** [fp-design-improvements.md](fp-design-improvements.md)  
**Historical source:** [notebooklm-response-fp.md](../archive/notebookLM/fp/notebooklm-response-fp.md)

---

## Completed (Phases 0, A, B, C, D, E — global closure)

HTTP contract tests, `wrap-errors`, `validation/entity`, FP architecture doc, players/teams/seasons/championships/matches `domain/*` + `logic/*`, `db.protocol/*` store protocols, zero `service/*` / `repository/*` globally, `domain/analytics` + `logic/analytics`, derived API/insights/CSV, CLJS `dispatch!` / `app-reducer` / dashboard reactions, domain+logic tests with `reify`, handler tests via bound `*store*` (no `with-redefs` on `db/*` globals). OO pilots deleted; `./bin/galaticos test` green.

Verified 2026-06-11:

```bash
rg 'galaticos\.(service|repository)' src/ test/
# → zero results
./bin/galaticos test
```

---

## Optional follow-ups

- [ ] Extend `wrap-errors` for `{:error}` maps
- [ ] Separate merge/pipeline IO vs pure calculation in aggregations
- [ ] Jobs: intent maps (future evolution)
- [ ] Property `recompute == cache` in tests (additional coverage)
- [ ] Refactor remaining handler tests (players, teams) from `with-redefs` on `db/*` to bound store pattern
