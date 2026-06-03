# Plano 05 — Analytics: catálogo + rollups (`aggregated-stats`)

## Estado

Concluído.

## Objetivo

Camada de **dados** da Fase 3: fórmulas oficiais e cache `players.aggregated-stats` com cartões e minutos — abordagem **funcional** (cálculos puros + IO separado).

## Depende de

- [04-handlers-rollout-docs.md](04-handlers-rollout-docs.md)

## Padrão FP

- **Puro:** `domain/analytics.clj` — rollups, merge, fórmulas derivadas
- **IO:** `db/aggregations.clj` — pipeline Mongo + persist; chama funções puras do domain
- **Jobs:** intent `{:op :recalc-stats :player-ids [...]}` — ver [architecture.md](../informacao/analytics/architecture.md)
- **Invariante testável:** `(recompute all-matches) == cached aggregated-stats` (fixtures in-memory)

## Fórmulas (fixar no catálogo)

| Métrica | Fórmula |
|---------|---------|
| `goal_contribution` | `goals + assists` |
| `goal_contribution_per_game` | `(goals + assists) / games` (0 se `games = 0`) |
| `discipline_index` | `(yellow-cards + 3 × red-cards) / games` (0 se `games = 0`) |
| `minutes_per_goal` | `minutes-played / goals` se `goals > 0` e qualidade de minutos OK |

## Entregáveis

### Documentação

- [metrics-catalog.md](../informacao/analytics/metrics-catalog.md) — métricas oficiais + interpretação
- [data-contracts.md](../informacao/analytics/data-contracts.md) — campos novos em `aggregated-stats`

### Código

| Namespace / ficheiro | Acção |
|---------------------|--------|
| `domain/analytics.clj` | **Novo** — `goal-contribution`, `discipline-index`, `summarize-player-stats`, `merge-aggregated-stats` (puro) |
| `db/aggregations.clj` | Pipeline Mongo (filtro volume); persist; delegar merge/rollup ao domain |
| `test/galaticos/domain/analytics_test.clj` | **Novo** — rollups e fórmulas sem Mongo |
| `test/galaticos/db/aggregations_test.clj` | Regressão pipeline + integração domain |

### Operação

- [reconciliation-runbook.md](../informacao/analytics/reconciliation-runbook.md) — reconcile após deploy

## Tarefas

- [x] Actualizar metrics-catalog
- [x] Criar `domain/analytics.clj` + testes puros
- [x] Refactor `aggregations.clj` — separar IO vs cálculo
- [x] Testes DB + propriedade recompute
- [x] `./bin/galaticos test`
- [ ] `./bin/galaticos coverage` (recomendado)

## Critérios de saída

- `discipline_index` calculável a partir de `aggregated-stats` sem scan total por request
- Lógica de merge testável sem Mongo
- Testes existentes verdes (ajustar expectativas se necessário)

## Não fazer

- Endpoints novos (Plano 06)
- UI CLJS (Plano 07)
- `service/analytics`

## Próximo plano

[06-analytics-api.md](06-analytics-api.md)
