# Galáticos frontend performance

**Summary:** Standardizes how we measure and improve **loading and interactivity** (Core Web Vitals and related metrics) on each SPA route. This folder links page inventory, audit methodology, and an actionable backlog.

## Objective

Same scope as above: connect inventory, methodology, and backlog for consistent performance work.

## Documents

| Document | Content |
|-----------|----------|
| [methodology.md](methodology.md) | How to run Lighthouse (CLI and DevTools), WSL environment, public vs authenticated routes. |
| [page-inventory.md](page-inventory.md) | One row per route: path, component, auth requirement, example URL. |
| [action-backlog.md](../../backlog/performance/action-backlog.md) | Per-screen baseline (fill after audits), opportunities, and task checklist. |

## Quick commands

With `CHROME_PATH` pointing to Linux Chrome (see [methodology.md](methodology.md)) and the app at `http://localhost:3000`:

| Command | Use |
|---------|-----|
| `npm run perf:lighthouse:login` | Lighthouse Performance category only on `/`, output `docs/perf-output/lighthouse-login.json`. |
| `CHROME_PATH=... npm run perf:lighthouse:auth` | Authenticated routes (optional extra URLs; default dashboard + stats). See authentication below. |
| `npm run perf:bundle-report` | `shadow-cljs release` with bundle report at `target/shadow-bundle-report.html` (`target/` is in `.gitignore`). |

**Release** CLJS build in Docker:  
`docker compose -f config/docker/docker-compose.dev.yml exec -T frontend-watch npx shadow-cljs release app`

**Authentication for `perf:lighthouse:auth`:** `PERF_AUTH_TOKEN` (JWT) injects the token without UI login; or `PERF_USE_API_LOGIN=1` with `PERF_LOGIN_USER` / `PERF_LOGIN_PASSWORD` (recommended in CI with secrets). Otherwise the script uses UI login (useful with `db:seed-smoke` and `admin`/`admin` in dev).

**Compression in a real environment:** after deploy, validate gzip on JS with GET:  
`curl -sD - -o /dev/null -H 'Accept-Encoding: gzip' http://localhost:3000/js/compiled/app.js` (expect `Content-Encoding: gzip`).

## Dev vs release build

- With **`shadow-cljs watch`** (or equivalent), the bundle tends to be **large and less optimized**; metrics like **Total Blocking Time (TBT)** and **Largest Contentful Paint (LCP)** are usually **worse** than production.
- For a serious baseline, measure with **`shadow-cljs release`** (or the artifact served in production), on the same host/base URL the end user sees.
- Dev audits are still useful for **trends** and obvious regressions; record **which build** was used in the backlog baseline column.

## Authentication and Lighthouse

Routes **`/`** (`:home`) and **`/login`** (`:login`) are listed in [page-inventory.md](page-inventory.md); Lighthouse CLI on `http://localhost:3000/` mainly measures **first load** of the shell + visitor dashboard.

The JWT lives in **`localStorage`** under `galaticos.auth.token` (see `src-cljs/galaticos/api.cljs`). Therefore:

- Lighthouse CLI against **protected** URLs **without** a configured session usually measures **login/redirect**, not the authenticated screen.
- For logged-in pages, use [methodology.md](methodology.md), logged-in DevTools, or `npm run perf:lighthouse:auth` with token/API login per the table above.

## Maintenance

When adding or removing routes in `src-cljs/galaticos/routes.cljs` / `core.cljs`, update [page-inventory.md](page-inventory.md) and, if needed, [action-backlog.md](../../backlog/performance/action-backlog.md).
