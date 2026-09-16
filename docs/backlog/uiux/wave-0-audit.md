# Wave 0 — UI/UX audit baseline

**Summary:** Snapshot of the Galáticos SPA against [ui-decisions.md](../../reference/ui/ui-decisions.md), NotebookLM UI/UX archive (§1–8), and adapted SarradaBet structural patterns (theme, disclaimers, breadcrumbs, admin lists). **No product code changes in this wave.** Use this report to drive later slices; do not reimplement what is already shipped.

**Date:** 2026-09-14  
**Sources of truth (actual paths):**

| Topic | Path |
|-------|------|
| UX decisions | [docs/reference/ui/ui-decisions.md](../../reference/ui/ui-decisions.md) |
| NotebookLM (archived) | [docs/archive/notebookLM/uiux/notebooklm-response-uiux.md](../../archive/notebookLM/uiux/notebooklm-response-uiux.md) |
| Routes inventory | [docs/reference/performance/page-inventory.md](../../reference/performance/page-inventory.md) |
| Domain / BRM-13 | [docs/reference/domain/business-rules.md](../../reference/domain/business-rules.md) |
| Perf backlog | [docs/backlog/performance/action-backlog.md](../performance/action-backlog.md) |
| SarradaBet plans `02`/`04`/`05`/`07` | Not in this repo — use the task brief (map React → Reagent/reitit) |

Prompt paths such as `docs/backlog/uiux/ui-decisions.md` are stale; prefer the table above.

## Contents

- [Already shipped](#already-shipped-do-not-reimplement)
- [P0 — product](#p0--product)
- [P1 — real gaps](#p1--real-gaps)
- [P2 — doc and route drift](#p2--doc-and-route-drift)
- [Recalibrated sequence](#recalibrated-sequence)
- [Manual smoke (optional)](#manual-smoke-optional)

## Already shipped (do not reimplement)

| Area | Evidence | Notes |
|------|----------|-------|
| No native confirm/alert/prompt | Grep clean under `src-cljs/` | NotebookLM §7 |
| Undo toast ~10s | `delete_undo.cljs`, `toast.cljs`, `ui_copy.cljs` | Enroll remove, championship/match/player/team deletes, merge delay |
| Login outside shell | `core.cljs` (login / ui-lab skip layout) | No `ml-64` / sidebar offset |
| Hash 404 + hubs | `core.cljs` `not-found-page` | Dashboard / Jogadores / Partidas; no named `*` route needed |
| Resource 404 | `common/not-found-resource` | Player, team, championship missing IDs |
| Theme + persistence | `galaticos.theme` + `state`/`index.html` FOUC | **Wave 4:** pure `resolve-theme`, `apply-theme!`, system preference, `aria-pressed` |
| Responsive shell | `layout.cljs`: `md:w-16` / `lg:w-64`, tabs + Mais drawer | Main uses `pb-32` (not doc’d `pb-20`) |
| Match form 400/409 immunity | `common/apply-form-api-error!` | Does not clear `@form-data` |
| Match sticky header | `matches.cljs` | Champ + opponent + **date** + score |
| Match skeletons | `skeleton-score-header`, `skeleton-table` | Form load path |
| Number steppers + bounds | `common/number-stepper` + `match-stat-bounds` | goals/assists/away 0–20; yellow 0–2; red 0–1; minutes 0–120 |
| Local match draft | `match_draft.cljs` | Metadata **and** `player-statistics` |
| Picker `:disabled?` | `player_picker.cljs` | Ready for enrollment poka-yoke; championship card does not pass it yet |
| Breadcrumb primitive | `common/breadcrumb` | Last crumb not clickable; used on matches + teams only |

## P0 — product

None. Destructive-feedback, public login layout, and routing 404 are in place. Wave 0 deliverable is documentation only.

## P1 — real gaps

### Enrollments card (`#/championships/:id`) — highest value

| Gap | Current | Target |
|-----|---------|--------|
| RVMF `n/max` + progress | Max only as text in Detalhes (`championships.cljs` ~301–302) | Header counter + bar |
| Client poka-yoke at limit | Picker stays enabled; POST → 409 + toast | Disable picker/batch; amber banner; copy in `ui_copy` |
| Enrolled list density | Name + Remover only (~465–481) | Position + base team; desktop table / mobile cards |
| Batch enroll | One POST per pick via typeahead | Modal/drawer for 5–15; still existing API |
| Pure capacity helpers | Inline in component | CLJS pure mirror of JVM `enrollment-decision` (`domain/championships.clj` 126–133) |

**E2E note:** `e2e/ux-championships.spec.js` (“UI rejects enroll when max-players reached”) expects the **toast** path. Slice 1 must update that spec to assert disabled control + banner instead.

### Match form (Wave 1 residual) — shipped 2026-09-14

| Item | Status |
|------|--------|
| Numeric bounds | Done — `match-stat-bounds` (minutes 120 desktop+mobile; goals/assists/away 20; yellow 2; red 1) |
| Sticky date | Done |
| Draft stats | Done — `form-snapshot` / encode-decode preserve `player-statistics` |

### Wave 2 residual — shipped 2026-09-14

| Item | Status |
|------|--------|
| List skeletons | Done — championships, players, matches hubs + detail/form loads |
| Pure breadcrumbs | Done — `galaticos.breadcrumbs/build-breadcrumbs` + `route-labels` |
| Deep crumbs | Done — championship detail/season/edit, player detail/edit, matches hub |
| List toolbar | Done — `common/list-toolbar` on championships, players, matches |

### Wave 3 residual

| Gap | Detail |
|-----|--------|
| Wide tables | **Done (Wave 3)** — `/players` mobile card stack; seasons table dual layout |
| Sensitive actions | **Done (Wave 3)** — merge + finalize require reason; merge audit stores `:reason` |
| Persistent disclaimers | **Done (Wave 3)** — `common/persistent-banner` for limit + no active season |
| Pagination | Players already paginated; confirm matches/championships lists |

### Wave 3 delivered

| Item | Status |
|------|--------|
| Finalization checklist (forcing function) | Done — `galaticos.finalization` + UI on championship detail |
| Card stack mobile `/players` | Done — always cards `<lg`; desktop toggle |
| Reason + audit | Done — merge API/audit; finalize persists `:finalize-reason` |
| Persistent banners | Done — non-dismissible RVMF at Inscrições |

## P2 — doc and route drift

| Item | Detail |
|------|--------|
| Inventory auth column | Was “yes” for read hubs; code gates **writes** + **all Teams** via `protected-routes` in `routes.cljs` |
| Missing inventory row | `:matches-by-championship` → `/matches/championship/:championship-id` |
| PT aliases incomplete | `/times` and `/times/:id` proposed but **not** registered (only `/times/novo`, `/times/:id/editar`) |
| Orphan `:match-new` | `/matches/new` has no UI inbound link; create flow uses championship-scoped new |
| Shell padding | Doc said `pb-20`; code uses `pb-32` |

## Recalibrated sequence

Confirm before each slice. Do **not** redo undo toast, 404 hubs, login centering, or greenfield dark mode.

1. **Enrollment Slice 1** — poka-yoke + RVMF `n/max` (acceptance C3); update E2E.
2. **Wave 1 residual** — súmula bounds + sticky date (+ optional draft stats).
3. **Wave 2** — list skeletons; pure `build-breadcrumbs` + labels; ≥3 deep routes.
4. **Enrollment Slices 2–3** — density (position/team); batch enroll modal.
5. **Wave 3** — mobile card stacks; reason+audit; persistent banners.
6. **Wave 4** — `resolve-theme` + FOUC; tokens; PT microcopy leftovers. **Shipped 2026-09-14.**

### Wave 4 delivered

| Item | Status |
|------|--------|
| Pure `resolve-theme` + tests | Done — `galaticos.theme` |
| FOUC pre-paint | Done — `index.html` + `theme/apply-theme!` sole DOM mutator |
| System preference | Done — unset/`system` follows `prefers-color-scheme`; media listener |
| Toggle a11y | Done — `aria-label` + `aria-pressed` on header/login |
| Design tokens | Done — `common/design-tokens` for surfaces/tables |
| Microcopy pattern | Done — `ui-copy/what-happened-how-to-fix` |

```text
Wave0 → Slice1(enroll RVMF) → Wave1(match bounds)
              ↓
         Slice2 → Slice3(batch)
Wave1 → Wave2(crumbs/skeletons) → Wave3 → Wave4
```

## Manual smoke (optional)

Baseline only — no Wave 0 product diff:

1. `#/login` — centered, theme toggle, no sidebar.
2. `#/nao-existe` — 404 with three shortcuts inside layout.
3. `#/championships/:id` — Inscrições without `n/max`; picker active at limit; Remover → Desfazer toast.
4. `#/matches/:id/edit` — sticky without date; steppers; skeletons while loading.
5. Breakpoints: `lg` sidebar `w-64`; `md` icons; mobile tabs + Mais.
