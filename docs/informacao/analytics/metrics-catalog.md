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

## Métricas derivadas recomendadas (próximas fases)

- **goal_contribution**: `goals + assists`.
- **goal_contribution_per_game**: `(goals + assists) / games`.
- **discipline_index**: métrica ponderada de cartões por jogo.
- **minutes_per_goal**: minutos jogados por gol (quando `minutes-played` estiver consistente).

## Governança

- Toda nova métrica deve ser adicionada neste catálogo antes de entrar na API/UI.
- Alterações de fórmula exigem versionamento e comunicação no changelog de analytics.
- `docs/informacao/dominio/regras-de-negocio.md` deve referenciar este arquivo para evitar divergência semântica.
