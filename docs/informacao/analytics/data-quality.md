# Qualidade de Dados para Analytics

## Objetivo

Garantir confiança nas métricas consumidas em API, dashboard e exportações.

## Dimensões de qualidade

### Completude

- Partidas devem conter `championship-id`.
- `player-statistics` deve existir e ser não vazio.

### Consistência

- `player-id` em partidas deve referenciar jogador existente.
- `championship-id` deve referenciar campeonato existente.
- Campos numéricos devem ser não negativos.

### Unicidade e duplicidade

- Evitar duplicação acidental de entrada do mesmo jogador na mesma partida.
- Revisar regras de importação para não duplicar registros históricos.

### Reconciliação

- `players.aggregated-stats` deve bater com recalculo a partir de `matches`.
- Divergências devem ser registradas com contagem por severidade.

## Controles operacionais

### Checks recomendados em rotina

1. Verificação de integridade referencial.
2. Validação de shape mínimo de `player-statistics`.
3. Reconciliação periódica de agregados.
4. Auditoria de anomalias em métricas extremas.

### SLOs sugeridos

- Atualização de métricas após partida: até 5 minutos.
- Reconciliação completa: ao menos 1 vez por dia.
- Taxa de erro de qualidade crítica: menor que 1% dos registros.

## Tratamento de incidentes de dados

### Severidades

- **P1**: métricas críticas incorretas em produção.
- **P2**: inconsistência parcial com impacto em alguns relatórios.
- **P3**: inconsistência baixa sem impacto direto em decisão.

### Playbook resumido

1. Detectar e classificar severidade.
2. Isolar origem da inconsistência (entrada, transformação ou agregação).
3. Rodar reconciliação e validar correção.
4. Registrar causa raiz e ação preventiva.

## Responsabilidades

- Engenharia: instrumentação, correções e automação dos checks.
- Produto/analytics: validação de impacto em métricas-chave.
- Operação: acompanhamento de alertas e execução de playbook.

## Referências operacionais

- Runbook de reconciliação: `docs/informacao/analytics/reconciliation-runbook.md`.
- Roadmap de evolução: `docs/informacao/analytics/roadmap.md`.
