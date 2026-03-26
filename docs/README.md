# Galáticos - Hub de Documentação

Documentação central do projeto Galáticos, agora organizada para suportar evolução contínua em sports data analytics.

## Como navegar

- `README.md` (raiz): visão geral do projeto, setup e execução.
- `docs/README.md` (este arquivo): índice mestre de documentação.
- `docs/analytics/*`: trilha estratégica e operacional de analytics.

## Trilha Sports Data Analytics

- `docs/analytics/strategy.md`: visão, objetivos e casos de uso analíticos.
- `docs/analytics/architecture.md`: arquitetura de dados e fluxo analítico fim a fim.
- `docs/analytics/metrics-catalog.md`: catálogo oficial de métricas e KPIs.
- `docs/analytics/data-contracts.md`: contratos de dados versionados e governança de schema.
- `docs/analytics/data-quality.md`: regras de qualidade, reconciliação e incidentes.
- `docs/analytics/reconciliation-runbook.md`: rotina operacional de reconciliação e resposta a incidentes.
- `docs/analytics/operating-model.md`: papéis, cadência e gestão de mudanças analíticas.
- `docs/analytics/roadmap.md`: roadmap evolutivo em fases.
- `docs/analytics/technical-evolution.md`: plano de desacoplamento da recomputação analítica e observabilidade.
- `docs/analytics/advanced-analytics-backlog.md`: priorização de métricas derivadas e camada preditiva.

## Documentação funcional e técnica existente

- `docs/regras-de-negocio.md`: regras funcionais e técnicas do domínio.
- `docs/regras-de-negocio-checklist.md`: checklist de implementação de regras.
- `docs/mongodb-schema.md`: modelagem das coleções MongoDB.
- `docs/testing-coverage.md`: cobertura de testes e estratégia de qualidade.
- `docs/IMPLEMENTATION.md`: visão de implementação inicial.

## Performance frontend

- `docs/performance/README.md`: objetivos, interpretação dev vs release, limitações de auditoria com JWT em `localStorage`.
- `docs/performance/metodologia.md`: Lighthouse (DevTools e CLI), WSL/Chrome, rotas públicas e autenticadas.
- `docs/performance/inventario-paginas.md`: inventário de rotas e componentes para medição por página.
- `docs/performance/backlog-acoes.md`: baseline, oportunidades e checklist de tarefas por tela ou grupo.

## Materiais de apoio (NotebookLM)

- `docs/notebookLM/design-and-db-improvements.md`
- `docs/notebookLM/improvement-checklist.md`
- `docs/notebookLM/notebooklm-prompts.md`
- `docs/notebookLM/notebooklm-response.md`

## Referências externas usadas na trilha de analytics

- [Sports analytics - Wikipedia](https://en.wikipedia.org/wiki/Sports_analytics)
- [What Is Sports Data Analytics? | Teradata](https://www.teradata.com/insights/data-analytics/what-is-sports-data-analytics)
- [Data Literacy Toolkit](https://data-literacy-toolkit.github.io)

## Diretriz de manutenção da documentação

- Sempre atualizar o `docs/README.md` quando um novo documento for criado.
- Evitar duplicação de definição de métricas entre `regras-de-negocio.md` e `analytics/metrics-catalog.md`.
- Tratar `analytics/metrics-catalog.md` como fonte de verdade semântica para indicadores analíticos.

