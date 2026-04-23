# Runbook de Reconciliação de Dados Analíticos

## Objetivo

Padronizar a operação de reconciliação entre a fonte de verdade (`matches.player-statistics`) e o cache analítico (`players.aggregated-stats`).

## Frequência operacional

- Reconciliação completa: diária.
- Reconciliação rápida (amostral): antes de rituais semanais de performance.
- Reconciliação sob demanda: após incidentes ou mudanças de cálculo.

## SLOs de dados

- Latência para refletir partida nas métricas: até 5 minutos.
- Divergência crítica permitida após reconciliação: 0 casos.
- Taxa de inconsistência não crítica: menor que 1% dos registros avaliados.

## API (recálculo completo)

- **Síncrono (padrão):** `POST /api/aggregations/reconcile` (autenticado). A API valida integridade, executa o recompute completo na thread do pedido e responde com `updated` e mensagem. Adequado quando se precisa de confirmação imediata.
- **Assíncrono:** `POST /api/aggregations/reconcile?async=true` (autenticado). Responde **202** com `job-id`; o recompute roda no executor in-process. Use para janelas longas ou reduzir tempo de conexão no cliente. Acompanhe o processamento via logs estruturados (filtrar por `:job-id` e `galaticos.event/player-stats-refresh`).
- **Estado do worker (leitura):** `GET /api/aggregations/player-stats-jobs` (autenticado) devolve último sucesso (incremental/completo) persistido e métricas simples do executor (fila, activos). Útil em incidentes sem aceder apenas aos logs. Ver `docs/parcial/analytics/technical-evolution.md`.

## Checklist de execução

1. Verificar integridade de referências (`player-id`, `championship-id`).
2. Rodar rotina de recálculo completo de agregados.
3. Comparar amostra de métricas críticas:
   - `games`
   - `goals`
   - `assists`
   - `titles`
4. Registrar resultado (ok, warnings, erros).
5. Se houver divergência, classificar severidade e executar playbook.

## Severidade de incidentes

- **P1**: métricas críticas incorretas em produção com impacto em decisão.
- **P2**: inconsistência parcial com impacto limitado.
- **P3**: inconsistência localizada sem impacto direto em decisão.

## Playbook de incidente

1. Classificar P1/P2/P3.
2. Identificar origem (entrada, transformação, agregação, consumo).
3. Executar reconciliação completa.
4. Validar resultado com amostra e com dashboard.
5. Registrar causa raiz e ação preventiva.

## Evidências mínimas por execução

- Data/hora da execução.
- Escopo (completa, amostral, sob demanda).
- Quantidade de divergências.
- Status final: resolvido ou pendente com plano de ação.
