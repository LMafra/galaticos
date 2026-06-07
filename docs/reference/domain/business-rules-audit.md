# Audit: business rules vs implementation

**Summary:** Snapshot (2026-04-23) of **RN-*** / **BRM-*** coverage against code and tests (**119 tests, 472 assertions, 0 failures**). Read this when you verify rule implementation — not for day-to-day rule lookup (use [business-rules.md](business-rules.md)). Line references in the main rules doc are mostly stale; open product questions **Q-01–Q-06** remain.

**Date:** 2026-04-23  
**Requirements source:** [business-rules.md](business-rules.md)  
**Automated evidence:** `./bin/galaticos test` — **119 tests, 472 assertions, 0 failures** (backend + ClojureScript).

---

## Executive summary

| Area | Status |
|------|--------|
| **RN-*** catalog (65 rules) | Implementation present in cited modules; **line references** in the main doc are mostly **out of date** (drift). |
| Appendix **BRM-01–16** | Mostly covered by API + UI + `seed_mongodb.py`; **BRM-14** “ideally other dashboard filters” only partial (dashboard sends `q`; extra filters on Players page). |
| **RN-PEND-*** in appendix | Identifiers **do not** exist as `### RN-PEND-*` in the doc; behaviors mapped below. |
| **Q-01–Q-06** | Still **business / doc decisions**; they do not block technical verification but prevent “100% closed” in the manifest. |
| Player stats aggregation jobs | Retry + last-success record (Mongo) + `GET /api/aggregations/player-stats-jobs` (auth); see `player_stats_jobs.clj` and [architecture.md — Player stats aggregation jobs](../analytics/architecture.md#jobs-de-agregados-player-stats). |

---

## BRM-01 to BRM-16 (requirements)

| ID | Verification | Result |
|----|-------------|-----------|
| BRM-01 Player CRUD | `handlers/players.clj`, `db/players.clj`; name+position required; soft delete | ✓ |
| BRM-02 Team CRUD | `handlers/teams.clj`, `db/teams.clj`; delete blocked when players attached | ✓ |
| BRM-03 Championship CRUD | `handlers/championships.clj`; seasons via `seasons` + enrich | ✓ |
| BRM-04 Match CRUD | `handlers/matches.clj`, `player_stats_jobs.clj`; stats required | ✓ |
| BRM-05 Single active season | `seasons-db/create`, `activate!`, `handlers/seasons.clj` | ✓ |
| BRM-06 Season management | Seasons API + UI; appendix text still mentions championship-only `season` model — **doc outdated** | ⚠ doc |
| BRM-07 Finalize championship | `finalize-championship`; once; `finished-at` | ✓ |
| BRM-08 Conditional titles | `titles-award-count` 0 vs >0 + `winner-player-ids` | ✓ |
| BRM-09 Enrollment + team + match | `validate-player-team-coherence` + `validate-players-enrolled` in `matches.clj` | ✓ |
| BRM-10 View match players | Match API + stats; UI in `matches.cljs` | ✓ |
| BRM-11 Stats + score + aggregates | jobs + `aggregations.clj` | ✓ |
| BRM-12 Dashboard → screens | `dashboard.cljs`: cards → `:players`, `:matches`, `:championships`, `:teams` | ✓ |
| BRM-13 Search when enrolling | `player_picker.cljs`: local filter name/nickname on catalog | ✓ |
| BRM-14 Dashboard search | Dashboard: `q` → `:players`; Players list: `api/search-players` + position; no team filter on dashboard | ⚠ partial vs “ideally” |
| BRM-15 Import enrollment | `scripts/python/seed_mongodb.py`: `enrolled-player-ids`, `register_enrollment` | ✓ |
| BRM-16 GK in name | `seed_mongodb.py`: inference if position empty; explicit precedence documented in script | ✓ |

---

## RN-PEND (appendix) → actual code

| Appendix reference | Location |
|---------------------|-----------|
| RN-PEND-01 / 02 enrollment | `championships.cljs` + `seasons` `enrolled-player-ids`; championship/season handlers add player |
| RN-PEND-03 finalize + titles | `handlers/championships.clj` `finalize-championship`; `handlers/seasons.clj` `finalize-season`; `db/seasons.clj` `finalize!` |
| RN-PEND-04 match players in championship | `matches.clj` `validate-players-enrolled` |
| RN-PEND-05 aggregate recalculation | `analytics/player_stats_jobs.clj` + `db/aggregations.clj` |

---

## RN-* catalog (65) — files and drift

All unique paths below **exist** in the repository (verified 2026-04-23).

**Groups aligned with descriptions:** RN-AUTH (6), RN-CHAMP, RN-TEAM, RN-PLAYER, RN-MATCH, RN-STATS (functions present), RN-VALID, RN-REF, RN-RESP, RN-TIME, RN-QUERY-01, RN-INIT.

**Documentation attention (⚠):**

1. **Line numbers** in `business-rules.md` vs current code: many sections (e.g. `championships.clj` required fields doc ~11–12; code `allowed-championship-fields` ~12–17, `validate-championship-body` ~103+) — **treat lines as indicative**.
2. **RN-QUERY-02** points to `aggregations.clj` line 34; line 34 is `agg-entity-id-str`, not sorting. Metric sorting is in `$sort` pipelines (e.g. ~208, 235, 416, 594, 654+).
3. **RN-STATS-01 to RN-STATS-09**: line ranges in the doc do not match the current file; functions `player-stats-by-championship` (~182), `avg-goals-by-position` (~213), `search-players` (~392), etc.
4. **read_excel.py**: Excel read utility; rule **BRM-16** lives in **`seed_mongodb.py`**, not only `read_excel.py` as the appendix suggests.

---

## Open questions Q-01–Q-06

| ID | Implementation state |
|----|-------------------------|
| Q-01 Multiple seasons | **Partially resolved** in product: `seasons` collection + `championship-id`; appendix doc still ambiguous. |
| Q-02 Reopen championship | Not audited as a formal rule; needs business decision. |
| Q-03 Search criteria | Backend `search-players` uses normalization + regex; “accent-insensitive” depends on `normalize-text` — validate with `str-util` and real data. |
| Q-04 Import conflicts | **Aligned** with **Q-04** in [business-rules.md](business-rules.md): policy in `seed_mongodb.py` (e.g. key by name, spreadsheet wins on `aggregated-stats.total` where applicable). |
| Q-05 GK vs position | **Precedence** in `seed_mongodb.py` (explicit position vs inference). |
| Q-06 Card→route map | Dashboard has implicit mapping; formal table in doc is optional. |

---

## Recommendations

1. ~~Update [business-rules.md](business-rules.md)~~ **Done (2026-04-23)**: **RN-PEND** mapping table, BRM-05/BRM-12/BRM-14/BRM-16, RN-STATS-01–10 and RN-QUERY-02 aligned to functions; Q-01/Q-05/Q-06/Q-03 partially closed in text.
2. Keep regression: CI runs tests; after critical rule changes, align with [testing-coverage.md](testing-coverage.md).
3. **Business pending:** Q-02 (reopen championship); optional BRM-14 extension (team filter on dashboard, outside Phase 2 analytics scope). Q-04 aligned to checklist/seed; optional evolution (conflict report).

---

## Verification command

```bash
./bin/galaticos test
```

Optional: `./bin/galaticos coverage` for weak branches vs RN-STATS / handlers.
