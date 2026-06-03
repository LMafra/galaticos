# Backlog de Analytics Avançado

## Objetivo

Priorizar a evolução de métricas derivadas e preparar base para camada preditiva com critérios claros de entrada.

## Estado (2026)

**Fase 3 (planos 05–07):** métricas derivadas, API de insights, UI (dashboard, agregações, detalhe do jogador) e CSV com `include-derived` — **entregues**. Itens abaixo marcados como concluídos ou opcionais/futuros.

## Priorização de métricas derivadas

### Prioridade alta — concluído

- [x] `goal_contribution`: `goals + assists`.
- [x] `goal_contribution_per_game`: `(goals + assists) / games`.
- [x] `discipline_index`: índice ponderado de cartões por partida.

### Prioridade média

- [x] `minutes_per_goal` (quando `minutes-played` atingir qualidade mínima).
- [ ] Eficiência por posição (normalizada por participação).
- [x] Tendência por janela móvel (curto vs médio prazo) — experimental via insights API.

### Prioridade baixa (opcional)

- Indicadores compostos por contexto de campeonato/rodada.
- Métricas experimentais para scouting interno.

## Critérios para liberar camada preditiva

- [x] Histórico mínimo e buckets (`readiness` em API).
- [x] Reconciliação registrada (`player_stats_job_meta`) quando exigido.
- [x] Baseline estatístico simples (tendência linear, risco por regras) antes de ML.
- [ ] Taxa baixa de inconsistência após reconciliação contínua (monitorização operacional).
- [ ] Definição de hipótese de negócio mensurável por experimento.

## Backlog de iniciativas preditivas

1. [x] Classificação de tendência de performance individual (v1 experimental).
2. [x] Detecção de risco de queda de desempenho (v1 experimental).
3. [x] Projeção de contribuição por jogador para próxima janela (v1 experimental).

## Governança para experimentação

- Todo experimento deve ter:
  - hipótese explícita
  - métrica de sucesso
  - período de avaliação
  - critério de rollback

## Entregáveis para dashboard e export

- [x] Nova seção de métricas derivadas no dashboard.
- [x] Export CSV com colunas derivadas opcionais (`?include-derived=true`).
- [x] Notas de interpretação (disclaimers na UI de insights).
