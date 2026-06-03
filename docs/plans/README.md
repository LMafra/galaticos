# Planos de implementação

Índice de planos **sequenciais** para migração **OO → programação funcional** e Fase 3 de analytics. Cada ficheiro é um plano autónomo — executar **um de cada vez**, com `./bin/galaticos test` verde antes do seguinte.

**Arquitectura:** [arquitetura-funcional.md](../informacao/arquitetura/arquitetura-funcional.md)  
**Checklist FP:** [fp-improvement-checklist.md](../a-fazer/notebookLM/fp-improvement-checklist.md)

## Pré-requisito

| Plano | Título | Quando |
|-------|--------|--------|
| [00](00-prerequisite-green-tests.md) | Testes verdes | Antes de cada plano 02–07; sempre que o repo não compilar |

## Trilha FP + Analytics

| # | Plano | Estado | Depende de |
|---|--------|--------|------------|
| [01](01-foundation-errors-tests.md) | Fundação: erros + contratos | **Concluído** | 00 (se necessário) |
| [02](02-championships-service.md) | Championships: domain + logic + protocol | **A fazer** (substitui OO no repo) | 01 |
| [03](03-matches-service.md) | Matches: domain + logic + protocol | Pendente | 02 |
| [04](04-handlers-rollout-docs.md) | Rollout FP + schema docs | Pendente | 03 |
| [05](05-analytics-data-layer.md) | Analytics: rollups + domain puro | Pendente | 04 |
| [06](06-analytics-api.md) | Analytics API + insights | Pendente | 05 |
| [07](07-analytics-ui.md) | Analytics UI CLJS | Concluído | 06 |

**Sequência:** 00 → 01 → 02 → 03 → 04 → 05 → 06 → 07

**Critério global de refactor:** write path via `logic/*`; **zero** namespaces `galaticos.service.*` ou `galaticos.repository.*` em `src/` e `test/`.

## NotebookLM

| Recurso | Ficheiro |
|---------|----------|
| Prompts FP | [notebooklm-prompts-fp.md](../informacao/notebookLM/notebooklm-prompts-fp.md) |
| Respostas + decisões | [notebooklm-response-fp.md](../informacao/notebookLM/notebooklm-response-fp.md) |
| Mapa Galáticos | [fp-design-improvements.md](../a-fazer/notebookLM/fp-design-improvements.md) |
| Checklist OO (histórico) | [improvement-checklist.md](../a-fazer/notebookLM/improvement-checklist.md) |

## Referências estáveis

- Backlog analytics: [advanced-analytics-backlog.md](../a-fazer/analytics/advanced-analytics-backlog.md)
- Roadmap: [roadmap.md](../informacao/analytics/roadmap.md)
- Arquitetura jobs: [architecture.md](../informacao/analytics/architecture.md)

## Manutenção

Ao concluir um plano: marcar itens em [fp-improvement-checklist.md](../a-fazer/notebookLM/fp-improvement-checklist.md), actualizar estado nesta tabela se necessário, e **não** misturar o plano seguinte no mesmo PR.
