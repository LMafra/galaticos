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
- **Versão atual**: `v1.0`
- **Coleção**: `players`
- **Campo**: `aggregated-stats`

### Estrutura esperada

- `total`: mapa de métricas totais do jogador.
- `by-championship`: vetor de métricas por campeonato.

### Campos mínimos em total

- `games`
- `goals`
- `assists`
- `titles`

### Regras de contrato

- Campo deve existir em todos os jogadores ativos.
- Valores agregados devem ser reconciliáveis com `matches`.
- Atualizações completas devem registrar `updated-at`.

## Compatibilidade e depreciação

- Campos não devem ser removidos sem janela de depreciação.
- Campos novos devem ser opcionais na primeira versão de rollout.
- Endpoints consumidores devem suportar período de transição entre versões.

## Checklist de mudança de contrato

1. Atualizar este documento com nova versão.
2. Atualizar testes de validação e agregação.
3. Validar impacto em endpoints e frontend.
4. Registrar plano de rollback/reconciliação.
