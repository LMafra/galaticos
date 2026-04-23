# Arquitetura de Analytics

## Escopo

Este documento descreve o fluxo analítico principal do Galáticos, desde a captura de dados de partidas até o consumo via API, dashboard e exportações.

## Fluxo ponta a ponta

```mermaid
flowchart LR
  inputData[MatchAndPlayerInput] --> validation[ValidateAndNormalize]
  validation --> sourceOfTruth[MatchesCollection]
  sourceOfTruth --> aggregation[AggregationsPipeline]
  aggregation --> playerCache[PlayersAggregatedStats]
  aggregation --> analyticsApi[AnalyticsEndpoints]
  analyticsApi --> dashboardUi[DashboardAndAggregationsUI]
  analyticsApi --> csvExports[CSVExports]
  quality[DataQualityAndReconciliation] --> validation
  quality --> aggregation
```

## Componentes principais

### Source of truth

- Coleção `matches` com `player-statistics` no grão jogador-partida.
- É a base para recomputação consistente.

### Camada de agregação

- Pipeline MongoDB em `src/galaticos/db/aggregations.clj`.
- Produz agregados por jogador, campeonato, posição e tempo.

### Cache analítico em jogador

- Campo `players.aggregated-stats` para leitura rápida no dashboard e rankings.
- Deve ser sempre reconciliável com a fonte `matches`.

### Consumo analítico

- Endpoints em `src/galaticos/routes/api.clj` e handlers de agregação/export.
- Frontend consome no dashboard e páginas de agregação.

## Decisões arquiteturais atuais

- Após CRUD de partidas, recálculo completo de `players.aggregated-stats` é **agendado** em `galaticos.analytics.player-stats-jobs` (executor de thread única), desacoplado da resposta HTTP por padrão.
- Reconciliação operacional: `POST /api/aggregations/reconcile` (autenticado) e `reconcile-stats` disparam o mesmo recálculo completo de forma síncrona (ver `reconciliation-runbook.md`).
- Fallback e reconciliação manual existentes para inconsistências.
- Exportação CSV como ponte com BI externo.

## Limitações conhecidas

- O pipeline `update-aggregated-stats-pipeline` ainda percorre todas as partidas; apenas a resposta HTTP deixou de esperar pelo recálculo (consistência eventual no dashboard).
- Fila in-process: um único worker; múltiplas instâncias da API exigiriam fila externa ou coordenação adicional.
- Contratos de dados ainda precisando de governança explícita por versão.

## Evolução recomendada

1. Introduzir contratos de dados versionados para entradas analíticas.
2. Restringir o pipeline de agregação a subconjuntos (campeonato/temporada) e/ou fila distribuída para escala.
3. Definir SLOs de atualização e qualidade para métricas críticas.

## Referência de execução técnica

- Plano detalhado de desacoplamento e observabilidade: `docs/parcial/analytics/technical-evolution.md`.
