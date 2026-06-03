# Contratos de Dados de Analytics

## Objetivo

Definir contratos explícitos e versionados para estruturas críticas de dados analíticos, reduzindo risco de regressões em métricas e integrações.

## Política de versionamento

- Formato: `vMAJOR.MINOR`.
- `MAJOR`: mudança incompatível.
- `MINOR`: adição retrocompatível.
- Toda alteração deve atualizar este documento e o changelog técnico.

## Contrato 1: match player statistics

- **Nome**: `match_player_statistics`
- **Versão atual**: `v1.0`
- **Coleção**: `matches`
- **Campo**: `player-statistics` (array)
- **Grão**: jogador-partida

### Campos obrigatórios

- `player-id` (ObjectId/string válido)

### Campos opcionais

- `player-name` (string)
- `position` (string)
- `goals` (number >= 0)
- `assists` (number >= 0)
- `yellow-cards` (number >= 0)
- `red-cards` (number >= 0)
- `minutes-played` (number >= 0)

### Regras de contrato

- `player-statistics` deve ser vetor não vazio.
- Campos numéricos ausentes são tratados como zero na agregação.
- IDs devem ser normalizados antes de persistência.

## Contrato 2: player aggregated stats

- **Nome**: `player_aggregated_stats`
- **Versão atual**: `v1.1`
- **Coleção**: `players`
- **Campo**: `aggregated-stats`

### Estrutura esperada

- `total`: mapa de métricas totais do jogador.
- `by-championship`: vetor de métricas por campeonato (e opcionalmente `:season`).

### Campos mínimos em total

- `games`
- `goals`
- `assists`
- `titles`

### Campos opcionais (v1.1)

- `yellow-cards` (number >= 0) — rollup de partidas
- `red-cards` (number >= 0) — rollup de partidas
- `minutes-played` (number >= 0) — rollup de partidas

Os mesmos campos opcionais podem aparecer em cada elemento de `by-championship`.

### Métricas derivadas (não persistidas no cache)

Calculadas em memória a partir dos campos acima via `galaticos.domain.analytics`:

- `goal-contribution`, `goal-contribution-per-game`, `discipline-index`, `minutes-per-goal`

### Regras de contrato

- Campo deve existir em todos os jogadores ativos.
- Valores agregados devem ser reconciliáveis com `matches`.
- Atualizações completas devem registrar `updated-at`.

## Contrato 3: player insights API

- **Nome**: `player_insights_response`
- **Versão atual**: `v1.0`
- **Endpoint**: `GET /api/aggregations/players/:player-id/insights` (autenticado)

### Campos

- `derived` — mapa com métricas derivadas (`goal-contribution`, `goal-contribution-per-game`, `discipline-index`, `minutes-per-goal`)
- `readiness` — `{:ok boolean :reason keyword-or-nil :checks map}`; gate para camada preditiva. Checks incluem histórico do jogador (`min-games`, `min-buckets`, `min-years`) e saúde operacional (`reconciliation-seen?`, `executor-healthy?` via `player_stats_job_meta` e executor in-process).
- `trend`, `risk`, `projection` — presentes quando `readiness.ok` é true; `null` quando false (política HTTP **200**, sem números preditivos)
- `disclaimers` — vetor de strings (inclui aviso experimental)
- `experiment-meta` — metadados do modelo (`version`, `model`, `status`)

### Política de readiness

Quando `readiness.ok` é false, a API responde **200** com `trend`, `risk` e `projection` nulos e `disclaimers` explicando o motivo. Métricas derivadas (`derived`) permanecem disponíveis.

### Export CSV derivado

- `GET /api/exports/dashboard.csv?include-derived=true` adiciona colunas `CONTRIB GOL`, `CONTRIB/JOGO`, `DISCIPLINA` ao bloco de atletas.

## Compatibilidade e depreciação

- Campos não devem ser removidos sem janela de depreciação.
- Campos novos devem ser opcionais na primeira versão de rollout.
- Endpoints consumidores devem suportar período de transição entre versões.

## Checklist de mudança de contrato

1. Atualizar este documento com nova versão.
2. Atualizar testes de validação e agregação.
3. Validar impacto em endpoints e frontend.
4. Registrar plano de rollback/reconciliação.
