# Catálogo de Métricas de Analytics

## Objetivo

Ser a fonte de verdade semântica para métricas esportivas da plataforma.

## Convenções de catálogo

- **Nome canônico**: identificador único da métrica.
- **Grão**: nível de cálculo (jogador-partida, jogador-campeonato, etc.).
- **Fórmula**: regra de cálculo.
- **Fonte**: coleção/campo origem.
- **Interpretação**: como ler a métrica.

## Métricas base

### games

- **Descrição**: total de partidas com participação registrada.
- **Grão**: jogador-campeonato e jogador-total.
- **Fórmula**: contagem de partidas com entrada em `player-statistics`.
- **Fonte**: `matches.player-statistics.player-id`.

### goals

- **Descrição**: gols marcados.
- **Grão**: jogador-partida, jogador-campeonato, jogador-total.
- **Fórmula**: soma de `goals`.
- **Fonte**: `matches.player-statistics.goals`.

### assists

- **Descrição**: assistências realizadas.
- **Grão**: jogador-partida, jogador-campeonato, jogador-total.
- **Fórmula**: soma de `assists`.
- **Fonte**: `matches.player-statistics.assists`.

### yellow_cards

- **Descrição**: cartões amarelos recebidos.
- **Grão**: jogador-partida, jogador-campeonato, jogador-total.
- **Fórmula**: soma de `yellow-cards`.
- **Fonte**: `matches.player-statistics.yellow-cards`.

### red_cards

- **Descrição**: cartões vermelhos recebidos.
- **Grão**: jogador-partida, jogador-campeonato, jogador-total.
- **Fórmula**: soma de `red-cards`.
- **Fonte**: `matches.player-statistics.red-cards`.

### goals_per_game

- **Descrição**: eficiência ofensiva por partida.
- **Grão**: jogador-campeonato e jogador-total.
- **Fórmula**: `goals / games`.
- **Regra**: se `games = 0`, retorna `0`.

### assists_per_game

- **Descrição**: eficiência de assistência por partida.
- **Grão**: jogador-campeonato e jogador-total.
- **Fórmula**: `assists / games`.
- **Regra**: se `games = 0`, retorna `0`.

### titles

- **Descrição**: títulos conquistados.
- **Grão**: jogador-total.
- **Fórmula**: soma dos incrementos aprovados em finalização de campeonato.
- **Fonte**: `players.aggregated-stats.total.titles`.

## Métricas derivadas (v1.1)

### goal_contribution

- **Descrição**: contribuição ofensiva total (gols + assistências).
- **Grão**: jogador-campeonato e jogador-total.
- **Fórmula**: `goals + assists`.
- **Fonte**: `players.aggregated-stats` (campos base) ou cálculo puro em `galaticos.domain.analytics/goal-contribution`.
- **Interpretação**: quanto o jogador participou diretamente de gols; útil para ranking ofensivo amplo.

### goal_contribution_per_game

- **Descrição**: contribuição ofensiva média por partida.
- **Grão**: jogador-campeonato e jogador-total.
- **Fórmula**: `(goals + assists) / games`.
- **Regra**: se `games = 0`, retorna `0`.
- **Implementação**: `galaticos.domain.analytics/goal-contribution-per-game`.

### discipline_index

- **Descrição**: índice ponderado de cartões por partida (vermelho pesa 3× amarelo).
- **Grão**: jogador-campeonato e jogador-total.
- **Fórmula**: `(yellow-cards + 3 × red-cards) / games`.
- **Regra**: se `games = 0`, retorna `0`.
- **Fonte**: `aggregated-stats` com `yellow-cards`, `red-cards`, `games` — sem scan de `matches` por request.
- **Implementação**: `galaticos.domain.analytics/discipline-index`.

### minutes_per_goal

- **Descrição**: minutos jogados por gol marcado.
- **Grão**: jogador-campeonato e jogador-total.
- **Fórmula**: `minutes-played / goals` quando `goals > 0` e qualidade de minutos OK.
- **Qualidade de minutos**: `minutes-played >= games` (média ≥ 1 min/partida).
- **Regra**: retorna `nil` quando pré-condições falham.
- **Implementação**: `galaticos.domain.analytics/minutes-per-goal`.

## Insights preditivos (experimental, v1.0)

Expostos via `GET /api/aggregations/players/:player-id/insights` quando `readiness.ok` é true. Ver [data-contracts.md](data-contracts.md) contrato 3.

### trend

- **Descrição**: direção da contribuição ofensiva (janela recente vs anterior).
- **Grão**: jogador-temporal (buckets semana/mês de `player-performance-evolution`).
- **Valores**: `:direction` (`:up` | `:down` | `:stable`), `:delta`, médias por janela.
- **Implementação**: `galaticos.analytics.predictive/compute-trend`.

### risk

- **Descrição**: rótulo simples de risco de queda combinando tendência e `discipline-index`.
- **Valores**: `:level` (`:low` | `:medium` | `:high`).
- **Implementação**: `galaticos.analytics.predictive/compute-risk`.

### projection

- **Descrição**: projeção linear de contribuição ofensiva na próxima janela.
- **Campo principal**: `:projected-goal-contribution`.
- **Implementação**: `galaticos.analytics.predictive/compute-projection`.

### readiness

- **Descrição**: gate antes de expor trend/risk/projection.
- **Checks**: mínimo de partidas, buckets temporais, anos distintos, reconciliação registrada (`player_stats_job_meta`), fila de jobs saudável.
- **Implementação**: `galaticos.analytics.readiness/evaluate`.

## Governança

- Toda nova métrica deve ser adicionada neste catálogo antes de entrar na API/UI.
- Alterações de fórmula exigem versionamento e comunicação no changelog de analytics.
- `docs/informacao/dominio/regras-de-negocio.md` deve referenciar este arquivo para evitar divergência semântica.
