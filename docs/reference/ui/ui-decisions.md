# UI decisions

**Summary:** Stable UX rules for Galáticos SPA contributors. Covers layout postures, destructive feedback, form behavior, responsive shell, and Portuguese UI vocabulary. For route paths and Lighthouse inventory, see [page-inventory.md](../performance/page-inventory.md). For domain terms, see [concepts.md](../../concepts.md). Microcopy lives in `galaticos.ui-copy` ([ui_copy.cljs](../../../src-cljs/galaticos/ui_copy.cljs)).

## Design decisions

| Topic | Decision |
|-------|----------|
| Density | Match form (súmula) stays dense on desktop; dashboard stays minimal. |
| Wizards | Multi-step wizards only for rare/destructive flows (player merge, finalize season). Matches use a single page with progressive disclosure. |
| Automation | Mobile: proactive suggestions (e.g. last match roster). Desktop: silent audit + RVMF; admin can override improbable values with an amber warning. |
| Touch | Expand hit areas to 44–48px on touch; desktop keeps table density. |
| Navigation | Hub-and-spoke: simple dashboard; championship, match, and roster hubs keep breadcrumbs and persistent context. |
| Errors | Block only impossible input (poka-yoke); allow improbable values with a warning. |
| Destructive actions | Frequent deletes use undo toast (~10s); modal only for irreversible actions (eject-seat pattern). |
| Confirm dialogs | No `window.confirm` for routine deletes. |
| Form resilience | Long forms never clear valid fields on 400/409; match form uses local draft auto-save. |
| URLs | Reitit route names stay English in code; UI labels are Portuguese. Hash PT migration is phased — see [page-inventory.md](../performance/page-inventory.md#portuguese-hash-migration-planned). |
| Loading | Section skeletons beat a global spinner on lists and match forms. |
| Visual identity | `brand-maroon`; dark mode uses semi-transparent status badges. |

## Anti-patterns

Do not pursue these without explicit approval:

- Cosmetic redesign before match-form hierarchy and fields are right.
- Multi-step wizard on the match form.
- 3D charts or WebGL on the dashboard.
- Heavy login-page redesign (transient surface).

## Responsive shell

| Band | Width | Navigation |
|------|-------|------------|
| Mobile | &lt; 768px | Bottom tab bar (5 slots) + “Mais” drawer |
| Tablet | 768px – 1023px (`md:`) | Collapsed sidebar (icons only, `w-16`) |
| Desktop | ≥ 1024px (`lg:`) | Full sidebar (`w-64`) |

Implementation in `layout.cljs`: sidebar `hidden md:flex md:w-16 lg:w-64`; bottom tab `md:hidden`; drawer `md:hidden`; main `pb-20` on mobile for tab bar; match form mobile CTA fixed at `bottom-16`.

## UI vocabulary (Portuguese)

Use these terms in user-facing copy. Do not expose raw IDs or English jargon.

| Concept | Prefer | Avoid |
|---------|--------|-------|
| Squad | elenco, plantel | lista de utilizadores |
| Match stats form | súmula, estatísticas da partida | form payload |
| Season | temporada | season id in UI |
| Championship | campeonato | championship id in UI |
| Match | partida | match id in UI |
| Enrollment | inscrever, inscrição | sign-up, registration |
| Merge | unificar registos | fusão técnica |
| Save | guardar, registar | submeter, enviar |
| Remove | remover, apagar | deletar, excluir |

Critical messages are centralized in `galaticos.ui-copy` (delete undo, merge undo, empty states, form errors). API errors are translated via `galaticos.i18n`. Error copy pattern: **[what happened] + [how to fix]** — never blame the user; no JSON/DB jargon in the UI.

Do not redefine KPIs here — [metrics-catalog.md](../analytics/metrics-catalog.md).

## Postures

| Posture | Where | Traits |
|---------|-------|--------|
| **Sovereign** | Desktop, súmula, dense lists | Keyboard, tables, low chrome, persistent RVMF |
| **Field mobile** | Mobile at the pitch | 44px+ targets, card stack, steppers, auto-save |
| **Visitor** | Dashboard without login | Simple read-only view, obvious CTAs |
