# Backlog de Analytics Avançado

## Objetivo

Priorizar a evolução de métricas derivadas e preparar base para camada preditiva com critérios claros de entrada.

## Priorização de métricas derivadas

### Prioridade alta

- `goal_contribution`: `goals + assists`.
- `goal_contribution_per_game`: `(goals + assists) / games`.
- `discipline_index`: índice ponderado de cartões por partida.

### Prioridade média

- `minutes_per_goal` (quando `minutes-played` atingir qualidade mínima).
- Eficiência por posição (normalizada por participação).
- Tendência por janela móvel (curto vs médio prazo).

### Prioridade baixa

- Indicadores compostos por contexto de campeonato/rodada.
- Métricas experimentais para scouting interno.

## Critérios para liberar camada preditiva

- Histórico de dados consistente por múltiplas temporadas.
- Taxa baixa de inconsistência após reconciliação contínua.
- Definição de hipótese de negócio mensurável.
- Baseline estatístico simples validado antes de modelos mais complexos.

## Backlog de iniciativas preditivas

1. Classificação de tendência de performance individual.
2. Detecção de risco de queda de desempenho.
3. Projeção de contribuição por jogador para próxima janela.

## Governança para experimentação

- Todo experimento deve ter:
  - hipótese explícita
  - métrica de sucesso
  - período de avaliação
  - critério de rollback

## Entregáveis para dashboard e export

- Nova seção de métricas derivadas no dashboard.
- Export CSV com colunas derivadas opcionais.
- Notas de interpretação para evitar leitura incorreta.
