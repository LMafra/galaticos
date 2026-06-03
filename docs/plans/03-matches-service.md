# Plano 03 — Matches: domain + logic + protocol (FP)

## Estado

**Concluído** — vertical slice FP; BRM em `domain/*`, orquestração em `logic/*`.

## Objetivo

Vertical slice de partidas: validações BRM puras, orquestração em `logic/*`, jobs de agregados via **intent map**.

## Depende de

- [02-championships-service.md](02-championships-service.md) (padrão FP estabelecido)

## Padrão técnico

Mesmo modelo do Plano 02 — ver [arquitetura-funcional.md](../informacao/arquitetura/arquitetura-funcional.md). **Proibido** `service/matches`, `repository/matches`.

## Entregáveis

| Namespace | Responsabilidade |
|-----------|------------------|
| `domain/matches.clj` | `validate-players-enrolled`, `validate-player-team-coherence`, `validate-season-for-new-match` (puro) |
| `logic/matches.clj` | CRUD, `enrich-match-view`, submit recalc intent |
| `db.protocol/match-store.clj` | Contrato persistência matches |
| `handlers/matches.clj` | parse → logic → response |
| `test/galaticos/domain/matches_test.clj` | BRM sem IO |
| `test/galaticos/logic/matches_test.clj` | `reify` store |

### Comportamento a preservar

- Inscrição e coerência equipa (BRM)
- Protecção `python-seed` com `?force=true`
- Recalc após create/update/delete: intent `{:op :recalc-stats :player-ids [...]}` → [player-stats-jobs](../informacao/analytics/architecture.md)
- `seasons-db/add-match`, `remove-match` via store/protocol
- `enrich-match-view` (nomes jogador/equipa)

### Validação

Reutilizar [validation/entity.clj](../../src/galaticos/validation/entity.clj) (Plano 01).

## Tarefas

- [x] `db.protocol/match-store.clj`
- [x] `domain/matches.clj`
- [x] `logic/matches.clj`
- [x] Handler fino + testes
- [x] Intent map para jobs (integrar com executor existente)
- [x] `./bin/galaticos test`

## Critérios de saída

- Handlers sem lógica BRM inline
- Jobs de agregados disparam como antes
- Zero `service/*` / `repository/*` para matches

## Não fazer

- Alterar pipeline Mongo de agregação (Plano 05)
- UI CLJS

## Próximo plano

[04-handlers-rollout-docs.md](04-handlers-rollout-docs.md)
