# Improvement Checklist (from NotebookLM Responses)

This checklist turns the recommendations from [notebooklm-response.md](../../informacao/notebookLM/notebooklm-response.md) into concrete, trackable tasks. It follows the refactoring order suggested in NotebookLM response 3 (safety net → repository → service → thin handlers → DI → refinements). For project-specific context, see [design-and-db-improvements.md](design-and-db-improvements.md).

---

## Phase 1: Safety net

- [ ] Add integration tests for existing API handlers (contract tests).
- [ ] Rule: do not mix refactoring and new features in the same change.

---

## Phase 2: Repository layer

- [ ] Introduce repository namespaces (e.g. wrap existing `db/championships`, `db/matches`, `db/players` behind a clear repository API if not already).
- [ ] Move all DB access behind repositories; handlers call repositories only.
- [ ] Document repository responsibilities in code or docs.

---

## Phase 3: Service layer

- [ ] Add service namespaces (e.g. `galaticos.service.championships`).
- [ ] Move business rules (e.g. "cannot delete championship with matches") into services; services throw domain exceptions.
- [ ] Handlers delegate to services for all business logic.
- [ ] Apply design by contract (preconditions) in service functions.

---

## Phase 4: Thin handlers

- [ ] Restrict handlers to: parse request (params, body), call one service function, map result/exception to HTTP status and body.
- [ ] Remove validation and business logic from handlers (validation at boundary can stay in a dedicated step that calls into service with validated data).
- [ ] Use Facade-style single entry per use case where it helps.

---

## Phase 5: Dependency injection / testability

- [ ] Inject repositories into services (and services into handlers if applicable); avoid constructing DB/repos inside services.
- [ ] Add unit tests for services using test doubles (e.g. in-memory or mock repositories).
- [ ] Centralize object/component construction (e.g. in `core` or a "composition" namespace).

---

## Phase 6: Error handling and validation

- [ ] Define domain exception types with `ex-data` (e.g. `:not-found`, `:conflict`, `:validation`) and HTTP status.
- [ ] Centralize exception-to-HTTP mapping (middleware or single wrapper).
- [ ] Centralize validation (allowed/required fields, normalization) per entity; validation at boundary before service.

---

## Phase 7: Data and schema

- [ ] Document System of Record vs derived data in [../../informacao/dominio/mongodb-schema.md](../../informacao/dominio/mongodb-schema.md) (matches = source of truth; `players.aggregated-stats` = derived).
- [ ] Ensure no dual writes: derived stats updated only via CDC, background job, or single-write path + deterministic recompute.
- [ ] Add "recalc" path: admin or script to recompute all aggregated stats from matches; use for audits and tests.
- [ ] Document indexes and denormalization policy in [../../informacao/dominio/mongodb-schema.md](../../informacao/dominio/mongodb-schema.md).
- [ ] (Optional) Implement CDC or background job to update aggregates from match events.

---

## Phase 8: Optional patterns

- [ ] Consider Command pattern for complex writes (queueable/loggable).
- [ ] Consider Strategy pattern for varying validation rules.
- [ ] Consider Observer for secondary effects (e.g. cache invalidation, notifications).

---

## Reference: themes by phase

| Theme | Phases |
|-------|--------|
| **Architecture** (layered, thin handlers, Facade) | 2, 3, 4, 5, 8 |
| **Data layer** (Repository, DI, test doubles) | 2, 5 |
| **Schema & consistency** (System of Record, no dual writes, CDC, indexes, denormalization) | 7 |
| **Error handling & validation** (domain exceptions, centralize mapping, validation at boundary) | 6 |
