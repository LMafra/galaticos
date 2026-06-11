# Performance Action Backlog

Summarized in [development-roadmap.md](../development-roadmap.md).

## Summary

Open Lighthouse and bundle tasks. Most route work shipped in 2026 (code splitting, lazy pages, gzip, dashboard deferral, championship lazy leaderboards). **You still need:** match-form picker optimization and form skeleton. Re-measure with **release** + **mobile** after changes — [methodology.md](../../reference/performance/methodology.md).

**Baselines:** score = Performance (0–100). Record **date**, **build** (`dev` / `release`), **profile** (`mobile` / `desktop`). Raw JSON: `docs/perf-output/` (gitignored).

### Convention (Mar 2026)

- Primary profile: mobile.
- Release build: `docker compose -f config/docker/docker-compose.dev.yml exec -T frontend-watch npx shadow-cljs release app`
- WSL: set `CHROME_PATH` to Linux Chrome.
- Routes: [page-inventory.md](../../reference/performance/page-inventory.md)

### Next priorities

1. **Login / shell** — LCP on `/` (code splitting done; re-measure after deploy + gzip).
2. **Dashboard** — TBT ~560ms (progressive render done; watch regressions).
3. **`/stats`** — Recharts bundle in `:pages` chunk (monitor on upgrades).
4. **Match forms** — open tasks below.
5. **Unmeasured routes** — fill `___` baseline cells when product priority warrants.

---

## Open tasks

### Match forms (`:match-new`, `:match-new-in-championship`, `:match-edit`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |

- [ ] Player/championship picker: avoid loading full catalog at once if large.
- [ ] Use loading skeleton instead of blocking the whole form.

---

## Route baselines (reference)

### `/` — Login / shell

| Field | Value |
|-------|--------|
| **Baseline** | 2026-03-30 · **release** · **mobile** · Score **57** · LCP **12.2 s** · TBT **650 ms** · CLS **0.08** |
| **Re-measure** | 2026-03-31 after gzip: score **46** · LCP **8.9 s** |
| **Opportunities** | Unused JS (~741 KiB); JS execution (~1.5 s); main-thread (~2.3 s) |

### `/dashboard`

| Field | Value |
|-------|--------|
| **Baseline** | 2026-03-30 · **release** · **mobile** · Score **84** · LCP **1.9 s** · TBT **560 ms** · CLS **0.079** |

### `/stats`

| Field | Value |
|-------|--------|
| **Baseline** | 2026-03-30 · **release** · **mobile** · Score **89** · LCP **1.5 s** · TBT **460 ms** · CLS **0** |

### `/players` (list)

| Field | Value |
|-------|--------|
| **Baseline** | 2026-03-30 · **release** · **mobile** · Score **96** · LCP **1.0 s** · TBT **180 ms** · CLS **0.079** |

### `/matches` (list)

| Field | Value |
|-------|--------|
| **Baseline** | 2026-03-30 · **release** · **mobile** · Score **96** · LCP **1.2 s** · TBT **190 ms** · CLS **0.079** |

Other routes (`player-detail`, `championship-detail`, teams, forms): baseline cells still `___` — measure with [`lighthouse-authenticated.cjs`](../../../scripts/performance/lighthouse-authenticated.cjs).

---

## Completed (2026)

Login/shell code splitting (`lazy_pages.cljs`, `:pages` chunk), deferred `ensure-auth!`, static cache + gzip middleware, dashboard API dedup + deferred charts + memoization, stats server-side tab-stats + bounded tables, players pagination + isolated filters, player detail bundle endpoint, matches list patterns, championship lazy leaderboards + `min-h` loading, cross-cutting release baselines + bundle report + CI Lighthouse smoke (`perf-lighthouse-smoke`).

**Automation:** `PERF_AUTH_TOKEN` or `PERF_USE_API_LOGIN=1` in [`lighthouse-authenticated.cjs`](../../../scripts/performance/lighthouse-authenticated.cjs).
