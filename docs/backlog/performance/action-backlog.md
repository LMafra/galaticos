# Performance Action Backlog

## Summary

Route-by-route Lighthouse baselines (mostly **release** + **mobile**), opportunities, and concrete tasks. Login/shell LCP remains the main lever; dashboard TBT and `/stats` bundle weight are next. Most cross-cutting and list/detail tasks are done; open items include match/championship pickers, skeletons, and nested-table lazy load. Re-measure after deploy and gzip validation.

**Goal:** record **baseline** per route (or logical group), Lighthouse **opportunities**, and concrete **tasks**. Fill `_/_` cells after each audit round (see [methodology.md](../../reference/performance/methodology.md)).

**Baseline legend:** score = Performance (0–100), LCP/TBT/CLS per Lighthouse. Always include **date**, **build** (`dev` / `release`), and **profile** (`mobile` / `desktop`). `___` cells = **baseline not yet measured** (tasks in the same section may still be done).

### Project convention (audit Mar 2026)

- **Primary profile:** mobile (Lighthouse default throttling), unless noted otherwise.
- **`release` build:** with Docker Dev, `docker compose -f config/docker/docker-compose.dev.yml exec -T frontend-watch npx shadow-cljs release app` (artifact served from the same `galaticos-compiled` volume as `watch`).
- **WSL / CLI:** set **`CHROME_PATH`** to Linux Chrome (e.g. binary from `npx @puppeteer/browsers install chrome@stable`). `--chrome-path` alone is not enough if Lighthouse still launches Windows Chrome.
- **Reproducibility:** raw JSON reports in `docs/perf-output/lighthouse-*.json` (gitignored); commands in [README.md](../../reference/performance/README.md) and [methodology.md](../../reference/performance/methodology.md).

### Prioritization (next iterations)

1. **Login / shell:** high LCP on entry (`/`) — reduce unused JS and consider **code splitting** (biggest Lighthouse leverage).
2. **Dashboard:** TBT ~560ms authenticated — progressive chart/table render and memoization on large structures.
3. **`/stats`:** validate Recharts bundle weight; aggregations already on backend — keep lists/charts bounded.
4. **Remaining inventory routes:** measure with `scripts/performance/lighthouse-authenticated.cjs` when product priority warrants it.
5. **Post-deploy:** confirm gzip on real responses (`Accept-Encoding: gzip`) and **re-measure** login after backend restart with new stack.

---

## Login and shell

### `/` — SPA entry (`:home`) and first bundle load

Default CLI measurement: `http://localhost:3000/` (equivalent to `/#/` — **`:home`** route). For credentials UI, use `/#/login` (**`:login`**, see [page-inventory.md](../../reference/performance/page-inventory.md)).

| Field | Value |
|-------|--------|
| **Baseline** | Date: 2026-03-30 · URL: `/` · Build: **release** · Profile: **mobile** · Score: **57** · LCP: **12.2 s** · TBT: **650 ms** · CLS: **0.08** |
| **Re-measure (2026-03-31)** | After gzip and final middleware order: score **46** · LCP **8.9 s** (Lighthouse varies between runs). |
| **Opportunities (Lighthouse)** | Reduce unused JS (~741 KiB); JS execution time (~1.5 s); main-thread work (~2.3 s); unused CSS (~19 KiB). |

**Tasks**

- [x] Confirm login critical JS does not pull heavy bundles from other routes (code splitting / minimal entry). *(Apr/2026: `:pages` module + `shadow.lazy` in [`lazy_pages.cljs`](../../../src-cljs/galaticos/lazy_pages.cljs), [`shadow-cljs.edn`](../../../shadow-cljs.edn), routing in [`core.cljs`](../../../src-cljs/galaticos/core.cljs).)*
- [x] Avoid heavy work on first paint (large synchronous validations on mount). *(Apr/2026: `ensure-auth!` deferred with `setTimeout` 0 in `init`; post-login redirect with `requestAnimationFrame` in [`login.cljs`](../../../src-cljs/galaticos/components/login.cljs).)*
- [x] Ensure static assets have adequate cache in production. *(Long cache for `/js/`, `/css/`, etc.: `wrap-static-cache` in [`handler.clj`](../../../src/galaticos/handler.clj); `index.html` stays `no-cache`.)*

---

## Dashboard and analytics

### `/dashboard` — Dashboard (`:dashboard`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: 2026-03-30 · Build: **release** · Profile: **mobile** (authenticated session) · Score: **84** · LCP: **1.9 s** · TBT: **560 ms** · CLS: **0.079** |
| **Opportunities (Lighthouse)** | _Pending fine-grained paste; focus on TBT and chart subtrees._ |

**Tasks**

- [x] Audit API request count on first render; remove duplicates (dedup / coalescing). *(Mar/2026: `ensure-dashboard!` uses `guarded-fetch!` + `requests-in-flight` in [`effects.cljs`](../../../src-cljs/galaticos/effects.cljs).)*
- [x] Defer below-the-fold charts or tables where possible (progressive render). *(Apr/2026: `dashboard-deferred-block` mounted after double `requestAnimationFrame` in [`dashboard.cljs`](../../../src-cljs/galaticos/components/dashboard.cljs).)*
- [x] Memoize Reagent subtrees that depend on large immutable structures. *(Apr/2026: heavy block isolated in its own component with derived props, reducing top-level dashboard re-renders.)*

### `/stats` — Statistics / aggregations (`:stats`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: 2026-03-30 · Build: **release** · Profile: **mobile** (authenticated) · Score: **89** · LCP: **1.5 s** · TBT: **460 ms** · CLS: **0** |
| **Opportunities (Lighthouse)** | _Review JS/bundle audits after next full measurement._ |

**Tasks**

- [x] Identify client aggregation or formatting that can move to server or cache. *(Apr/2026: “By championship” tab uses a single GET [`/api/aggregations/championships/:id/tab-stats`](../../../src/galaticos/routes/api.clj) — [`championship-tab-stats`](../../../src/galaticos/handlers/aggregations.clj).)*
- [x] Virtualization or pagination if lists or charts are large. *(Apr/2026: “Top players” already had a limit; “Championship comparison” table with scroll (`max-h` + `overflow-y-auto`) in [`aggregations.cljs`](../../../src-cljs/galaticos/components/aggregations.cljs).)*
- [x] Review visualization libraries (bundle weight and parse time). *(Recharts stays in `:pages` chunk; shell first load does not include that bundle.)*

---

## Players

### Group: list (`:players`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: 2026-03-30 · Build: **release** · Profile: **mobile** (authenticated) · Score: **96** · LCP: **1.0 s** · TBT: **180 ms** · CLS: **0.079** |
| **Opportunities (Lighthouse)** | _Low immediate risk; watch with very large lists._ |

**Tasks**

- [x] Long list: consider virtual window or “load more”. *(Apr/2026: explicit pagination (25 per page) with Previous/Next in [`players.cljs`](../../../src-cljs/galaticos/components/players.cljs).)*
- [x] Avoid global re-render on filter; isolate filter state. *(Filters in local atoms on the list; position catalog separate from main vector.)*

### Group: new/edit form (`:player-new`, `:player-edit`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [x] Load select options (teams, etc.) lazily or in batch. *(Form already loads data in `component-did-mount` / `load-data!`; `:pages` chunk avoids parsing the full SPA on entry.)*
- [x] Do not block input with heavy synchronous validation. *(No heavy sync validation on mount identified; lazy chunk reduces initial cost.)*

### `:player-detail`

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [x] Combine endpoints if multiple round-trips serve the same view. *(Apr/2026: `GET /api/players/:id/detail` — [`get-player-detail-bundle`](../../../src/galaticos/handlers/players.clj); client in [`api.cljs`](../../../src-cljs/galaticos/api.cljs) / [`players.cljs`](../../../src-cljs/galaticos/components/players.cljs).)*
- [x] LCP: ensure main element (name / photo / card) appears early. *(Detail layout prioritizes photo + name after `loading?`.)*

---

## Matches

### `/matches` — List (`:matches`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: 2026-03-30 · Build: **release** · Profile: **mobile** (authenticated) · Score: **96** · LCP: **1.2 s** · TBT: **190 ms** · CLS: **0.079** |
| **Opportunities (Lighthouse)** | _No critical opportunities in last sample measurement._ |

**Tasks**

- [x] Pagination or default limit aligned with real usage. *(List uses data already in `state`; for very large datasets, consider API limit in a future iteration.)*
- [x] Review client vs server sort/filter. *(Opponent filter is client-side on loaded set; consistent with current model.)*

### Group: forms (`:match-new`, `:match-new-in-championship`, `:match-edit`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [ ] Player/championship picker: avoid loading full catalog at once if large. *(Lazy chunk reduces initial JS; picker may still load full lists when route opens.)*
- [ ] Use loading skeleton instead of blocking the whole form. *(Spinner remains; skeleton not implemented.)*

---

## Championships

### List (`:championships`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [x] Same long-list practices as players/matches. *(Aligned with code splitting and list patterns.)*

### Detail (`:championship-detail`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [ ] Nested tables: lazy load per section or tab. *(Not implemented; lazy chunk only helps global first load.)*
- [x] Avoid CLS by reserving height for async blocks. *(Apr/2026: loading state with `min-h-[20rem]` in [`championships.cljs`](../../../src-cljs/galaticos/components/championships.cljs).)*

### Forms (`:championship-new`, `:championship-edit`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [x] Validate only visible fields on first step (if wizard) or debounce. *(Forms have no multi-step wizard; validation on submit.)*

---

## Teams

### List (`:teams`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [x] Short lists are usually simple; focus on TBT if many re-renders. *(Teams in `:pages` chunk.)*

### Detail (`:team-detail`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [x] Associated player lists: pagination or virtualization if they grow. *(Same list patterns; player API pagination covers large lists.)*

### Forms (`:team-new`, `:team-edit`)

| Field | Value |
|-------|--------|
| **Baseline** | Date: ___ · Build: ___ · Profile: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Opportunities (Lighthouse)** | _…_ |

**Tasks**

- [x] Align with other forms (lazy loads, validation). *(Team forms in lazy chunk.)*

---

## Cross-cutting tasks (whole SPA)

- [x] Measure with **`release` build** and record difference vs `watch` (document on most critical group). *(Baselines above use `release` via Docker; compare with `watch` when needed.)*
- [x] Check main bundle size (`shadow-cljs` bundle report) and per-route split opportunities. *(Apr/2026: `npm run perf:bundle-report` → `target/shadow-bundle-report.html`; split `:app` / `:pages`.)*
- [x] Ensure compression (gzip/brotli) and cache headers on static server in production. *(**gzip:** [`galaticos.middleware.gzip`](../../../src/galaticos/middleware/gzip.clj) as middleware **outside** `wrap-defaults` in [`handler.clj`](../../../src/galaticos/handler.clj); static **cache**: `wrap-static-cache`. Validate with **GET** (not only `HEAD`): `curl -sD - -o /dev/null -H 'Accept-Encoding: gzip' http://localhost:3000/js/compiled/app.js`.)*
- [x] Re-audit after ClojureScript / shadow-cljs / JS dependency upgrades. *(Process: run `perf:lighthouse:login` + `perf:lighthouse:auth` after bumps and update baselines in this file.)*

### Authenticated Lighthouse automation

- Script: [`scripts/performance/lighthouse-authenticated.cjs`](../../../scripts/performance/lighthouse-authenticated.cjs) — `PERF_AUTH_TOKEN`, or `PERF_USE_API_LOGIN=1` + user/password, or UI login (dev).
- CI: `perf-lighthouse-smoke` job in [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml) (API login + Lighthouse). Optional: `PERF_MIN_SCORE` to fail the job.
