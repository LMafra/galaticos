# Design Patterns & Database Structure – Improvement Ideas

Suggestions tailored to the Galáticos codebase. Use together with **NotebookLM** (see `notebooklm-prompts.md`) for general principles from your notebook sources.

---

## 1. Design patterns

### 1.1 Introduce a service / use-case layer

**Current:** Handlers (`handlers/*.clj`) do validation, normalization, DB calls, and response building in one place.

**Improvement:** Extract a thin **service** layer so that:

- **Handlers** only: parse request (params, body), call one service function, return response.
- **Services** (e.g. `galaticos.service.championships`): contain business rules, orchestrate DB calls, return data or domain errors.
- **DB** (`db/*.clj`): pure data access (find, insert, update, delete).

**Benefits:** Business rules like “cannot delete championship with matches” live in one place, are easy to unit test without HTTP, and handlers stay small and readable.

**Example:** Move `validate-championship-body`, “has-matches?” check and delete orchestration from `handlers/championships.clj` into `championships-service.clj`; handler only calls e.g. `(championships-service/delete! id)` and maps result to HTTP status/body.

### 1.2 Reusable validation and “allowed fields”

**Current:** Each handler defines its own `allowed-*-fields`, `required-*-fields`, and ad-hoc normalization (e.g. `normalize-championship-body`).

**Improvement:**

- Centralize allowed/required field sets (or schema) per entity, e.g. in a `galaticos.spec` or `galaticos.validation` namespace.
- Use one generic “validate body” helper that takes entity type + body and returns `{:error _}` or `{:data normalized}`.
- Reduces duplication and keeps validation behavior consistent across create/update and across entities.

### 1.3 Consistent error handling in handlers

**Current:** `handle-exception` and try/catch in each handler; some errors mapped to 400 via `ex-data`.

**Improvement:**

- Use a small set of **domain exceptions** (e.g. `:not-found`, `:conflict`, `:validation`) with `ex-data` carrying `:status` and optional `:body`.
- In middleware or a single handler wrapper, catch these and map to HTTP status and JSON body.
- Handlers and services throw domain exceptions; no need for repeated try/catch and `handle-exception` in every handler.

---

## 2. Database structure

### 2.1 Keep “source of truth” vs “cache” explicit

**Current:** `docs/mongodb-schema.md` already states that `matches` (and `player-statistics` inside them) are the source of truth, and `players.aggregated-stats` is a cache. This is good.

**Improvement:**

- In code and docs, name clearly: e.g. “aggregated-stats is derived; recalc from matches when in doubt.”
- Consider a small “recalc” API or admin action that recomputes all aggregated stats from matches, so you can always restore consistency (and use it in tests).

### 2.2 Centralize ObjectId conversion and query shape

**Current:** `->object-id` and similar logic appear in handlers and DB layers; query shapes (e.g. `{:championship-id (->object-id id)}`) are built in several places.

**Improvement:**

- DB layer: accept string or ObjectId for ids and normalize once at the boundary (e.g. in `find-by-id`, `find-by-championship`).
- Handlers: pass string ids from the request; let the DB (or service) layer do conversion and validation. This avoids duplicated conversion and keeps invalid-id handling in one place.

### 2.3 Indexes and usage

**Current:** Schema doc describes collections and relationships; index strategy is not fully described.

**Improvement:**

- Document (e.g. in `mongodb-schema.md`) indexes for common access paths: e.g. `championship-id` + `date` for matches, `team-id` for players, and any fields used in aggregation pipelines.
- Ensure indexes exist in code or migration scripts so dev/staging/prod stay aligned.

### 2.4 Denormalization in `matches.player-statistics`

**Current:** `player-name` and `position` are denormalized in each match’s `player-statistics` to avoid lookups.

**Improvement:**

- When updating a player’s name or position, consider whether you need to backfill existing match documents (for historical accuracy or display). If yes, document the decision and, if needed, add a one-off or periodic job to update old matches.
- Optionally add a short “denormalization policy” section in `mongodb-schema.md` (what is denormalized, when it’s updated, and when it’s acceptable to be stale).

---

## 3. Next steps

1. **NotebookLM:** Use the prompts in `notebooklm-prompts.md` in your “Software Engineering: Delivery, Design, and Clean Code” notebook to get more general patterns and refactoring order.
2. **Service layer:** Pick one bounded area (e.g. championships or matches) and introduce a service namespace; then align handlers and tests.
3. **Validation:** Unify allowed/required fields and validation in one place and reuse from handlers.
4. **Schema doc:** Add an “Indexes” and “Denormalization policy” section to `mongodb-schema.md` and implement the indexes.

These improvements will make the codebase easier to test, extend, and keep consistent with your notebook’s clean-code and delivery principles.
