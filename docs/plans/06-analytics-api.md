# Plano 06 — Analytics: API (derived, predictive, insights, CSV)

## Estado

Concluído.

## Objetivo

Expor métricas derivadas e insights estatísticos leves (**sem** libs ML novas em [deps.edn](../../deps.edn)), com orquestração **funcional** (sem `service/analytics`).

## Depende de

- [05-analytics-data-layer.md](05-analytics-data-layer.md)

## Padrão FP

- Funções puras em `analytics/*` e `domain/analytics`
- Orquestração HTTP: `logic/analytics.clj` **ou** handlers directos que compõem leitura + domain
- Handlers sem `try/catch` repetido; middleware unificado
- **Proibido:** `galaticos.service.analytics`

## Entregáveis

### Namespaces

| Namespace | Responsabilidade |
|-----------|------------------|
| `analytics/derived-metrics.clj` | Funções puras: stat map → campos derivados |
| `analytics/readiness.clj` | Critérios preditivo (temporadas, reconcile/job) |
| `analytics/predictive.clj` | Tendência, risco, projeção linear (experimental) |
| `logic/analytics.clj` | (opcional) Orquestra leitura + derived + predictive |
| `handlers/aggregations.clj` | Endpoints; delega a logic ou domain |

### API

- Extender `search-players` / `GET /api/aggregations/players/top` — `goal-contribution`, `discipline-index`, etc.
- Extender `dashboard-stats` — tops derivados
- **Novo** `GET /api/aggregations/players/:player-id/insights` (auth):
  - `{:derived :trend :risk :projection :readiness :disclaimers :experiment-meta}`
  - Política `readiness` false: **HTTP 200** com `readiness {:ok false :reason ...}`; `trend`/`risk`/`projection` nulos (documentado em [data-contracts.md](../informacao/analytics/data-contracts.md))
- CSV: `?include-derived=true` em export

### Rotas

- [routes/api.clj](../../src/galaticos/routes/api.clj) com `wrap-auth`

### Testes

- `test/galaticos/analytics/derived_metrics_test.clj`
- `test/galaticos/analytics/predictive_test.clj`
- `test/galaticos/handlers/aggregations_test.clj`

## Tarefas

- [x] derived-metrics + testes
- [x] readiness + predictive + testes
- [x] logic/analytics ou handlers + routes
- [x] CSV opcional + data-contracts
- [x] `./bin/galaticos test`

## Critérios de saída

- Insights sem números preditivos se `readiness` falhar
- Zero `service/analytics`
- Documentação de contrato actualizada

## Não fazer

- Alterações CLJS (Plano 07)
- Rollups (Plano 05)

## Próximo plano

[07-analytics-ui.md](07-analytics-ui.md)
