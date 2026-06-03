# Improvement Checklist (from NotebookLM Responses)

This checklist turns the recommendations from [notebooklm-response.md](../../informacao/notebookLM/notebooklm-response.md) into concrete, trackable tasks. It follows the refactoring order suggested in NotebookLM response 3 (safety net → repository → service → thin handlers → DI → refinements). For project-specific context, see [design-and-db-improvements.md](design-and-db-improvements.md).

**Nota:** Para abordagem **programação funcional**, usar [fp-improvement-checklist.md](fp-improvement-checklist.md), [fp-design-improvements.md](fp-design-improvements.md) e [arquitetura-funcional.md](../../informacao/arquitetura/arquitetura-funcional.md). **Fases 2–5 abaixo estão superseded** pela trilha FP (planos [02–07](../../plans/README.md)); o código OO actual (`service/*`, `repository/*`) será removido na fase de implementação FP, não expandido.

---

## Phase 1: Safety net

- [x] Add integration tests for existing API handlers (contract tests).
- [x] Rule: do not mix refactoring and new features in the same change.

---

## Phase 2: Repository layer — **superseded by FP checklist**

> Não implementar camada `repository/*`. Alvo: `db.protocol/*` + `db/*` + `logic/*`. Ver [fp-improvement-checklist.md](fp-improvement-checklist.md) Fase B–D.

- [ ] ~~Introduce repository namespaces~~ — **cancelado (FP)**
- [ ] ~~Move all DB access behind repositories~~ — **cancelado (FP)**
- [ ] ~~Document repository responsibilities~~ — **cancelado (FP)**

---

## Phase 3: Service layer — **superseded by FP checklist**

> Não expandir `service/*`. Alvo: `domain/*` (puro) + `logic/*` (orquestração). Ver Plano [02](../../plans/02-championships-service.md).

- [ ] ~~Add service namespaces~~ — **cancelado (FP)**
- [ ] ~~Move business rules into services~~ — **cancelado (FP)**
- [ ] ~~Handlers delegate to services~~ — **cancelado (FP)**
- [ ] ~~Design by contract in services~~ — **cancelado (FP)**

---

## Phase 4: Thin handlers — **superseded by FP checklist**

> Handlers finos via `logic/*` + middleware unificado; ver [arquitetura-funcional.md](../../informacao/arquitetura/arquitetura-funcional.md).

- [ ] ~~Restrict handlers to parse → service → HTTP~~ — **substituído:** parse → `logic/*` → HTTP
- [ ] ~~Remove validation from handlers~~ — parcialmente feito; validação em `validation/entity`
- [ ] ~~Facade per use case~~ — **cancelado (FP)**

---

## Phase 5: Dependency injection / testability — **superseded by FP checklist**

> DI via `defprotocol` + `reify`; **proibido** `repo-call`/`ns-resolve`. Ver Prompt 5 em [notebooklm-response-fp.md](../../informacao/notebookLM/notebooklm-response-fp.md).

- [ ] ~~Inject repositories into services~~ — **cancelado (FP)**
- [ ] ~~Unit tests for services with mocks~~ — **substituído:** `logic/*_test` com `reify`
- [ ] ~~Centralize composition namespace~~ — **cancelado (FP)**

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
