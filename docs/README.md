# Galáticos — Hub de documentação

Documentação central do projeto, organizada por **status** (Informação, A Fazer) e por **tema** dentro de cada status.

## Como navegar

- `README.md` (raiz do repositório): visão geral do projeto, setup e execução.
- `docs/README.md` (este arquivo): índice por status.
- [plans/README.md](plans/README.md) — planos de implementação sequenciais (trilha FP 00–07 + analytics Fase 3).
- Saídas locais do Lighthouse (JSON): `docs/perf-output/` (gitignored; ver documentação em Informação / performance).

## Informação

Referência estável: visão, processos, modelos e como medir.

### Analytics (`docs/informacao/analytics/`)

- [strategy.md](informacao/analytics/strategy.md) — visão, objetivos e casos de uso analíticos.
- [architecture.md](informacao/analytics/architecture.md) — arquitetura analítica, jobs de agregados e fluxo fim a fim.
- [metrics-catalog.md](informacao/analytics/metrics-catalog.md) — catálogo oficial de métricas e KPIs.
- [data-contracts.md](informacao/analytics/data-contracts.md) — contratos de dados versionados e governança de schema.
- [data-quality.md](informacao/analytics/data-quality.md) — regras de qualidade, reconciliação e incidentes.
- [reconciliation-runbook.md](informacao/analytics/reconciliation-runbook.md) — rotina operacional de reconciliação e resposta a incidentes.
- [operating-model.md](informacao/analytics/operating-model.md) — papéis, cadência e gestão de mudanças analíticas.
- [roadmap.md](informacao/analytics/roadmap.md) — roadmap evolutivo em fases.

### Performance (`docs/informacao/performance/`)

- [README.md](informacao/performance/README.md) — objetivos, interpretação dev vs release, limitações de auditoria com JWT em `localStorage`.
- [metodologia.md](informacao/performance/metodologia.md) — Lighthouse (DevTools e CLI), WSL/Chrome, rotas públicas e autenticadas.
- [inventario-paginas.md](informacao/performance/inventario-paginas.md) — inventário de rotas e componentes para medição por página.

### Arquitetura (`docs/informacao/arquitetura/`)

- [arquitetura-funcional.md](informacao/arquitetura/arquitetura-funcional.md) — guia principal da migração OO → FP: namespaces (`domain/*`, `logic/*`, `db.protocol/*`), erros, DI, testes, anti-padrões e ordem de execução (planos 02–07).

### Qualidade (`docs/informacao/qualidade/`)

- [auditoria-alucinacoes-ia.md](informacao/qualidade/auditoria-alucinacoes-ia.md) — guia para auditar código assistido por IA (deps, Monger, Ring/Compojure, CLJS, prompts e validação com testes e clj-kondo).

### Operação (`docs/informacao/operacao/`)

- [runbook-producao.md](informacao/operacao/runbook-producao.md) — deploy, backup, seed e índices em produção sem perda de dados.
- [vps-hospedeiro.md](informacao/operacao/vps-hospedeiro.md) — SSH, `.env` do Compose, Nginx/HTTPS, build Docker na VPS (Clojars/MTU), seed com Excel e checklist de deploy.
- [incidente-deploy-vps-frontend-2026-05.md](informacao/operacao/incidente-deploy-vps-frontend-2026-05.md) — nota pós-incidente: Mongo/seed, Clojars na VPS, cache de deploy, browser, remoção do chunk lazy `pages.js`.

### Domínio (`docs/informacao/dominio/`)

- [guia-partidas-temporadas-estatisticas-hibridas.md](informacao/dominio/guia-partidas-temporadas-estatisticas-hibridas.md) — partidas, temporadas ativas e modelo híbrido de estatísticas (merge, fan-out, UI).
- [regras-de-negocio.md](informacao/dominio/regras-de-negocio.md) — regras funcionais e técnicas do domínio.
- [regras-negocio-auditoria.md](informacao/dominio/regras-negocio-auditoria.md) — auditoria regras vs implementação (snapshot).
- [mongodb-schema.md](informacao/dominio/mongodb-schema.md) — modelagem das coleções MongoDB.
- [testing-coverage.md](informacao/dominio/testing-coverage.md) — cobertura de testes e estratégia de qualidade.

### NotebookLM (`docs/informacao/notebookLM/`)

- [notebooklm-prompts.md](informacao/notebookLM/notebooklm-prompts.md) — prompts OO (histórico)
- [notebooklm-response.md](informacao/notebookLM/notebooklm-response.md) — respostas OO (histórico)
- [notebooklm-prompts-fp.md](informacao/notebookLM/notebooklm-prompts-fp.md) — prompts programação funcional
- [notebooklm-response-fp.md](informacao/notebookLM/notebooklm-response-fp.md) — respostas FP + decisões consolidadas

## A Fazer

Backlogs, checklists de pendências e melhorias a executar.

### Analytics (`docs/a-fazer/analytics/`)

- [advanced-analytics-backlog.md](a-fazer/analytics/advanced-analytics-backlog.md)

### Performance (`docs/a-fazer/performance/`)

- [backlog-acoes.md](a-fazer/performance/backlog-acoes.md)

### NotebookLM (`docs/a-fazer/notebookLM/`)

- [design-and-db-improvements.md](a-fazer/notebookLM/design-and-db-improvements.md) — melhorias OO (histórico)
- [improvement-checklist.md](a-fazer/notebookLM/improvement-checklist.md) — checklist OO (Fases 2–5 superseded pela trilha FP)
- [fp-design-improvements.md](a-fazer/notebookLM/fp-design-improvements.md) — mapa De→Para e decisões FP
- [fp-improvement-checklist.md](a-fazer/notebookLM/fp-improvement-checklist.md) — checklist acionável FP (Fase A doc concluída; Fases B–E = código)

## Referências externas (trilha de analytics)

- [Sports analytics - Wikipedia](https://en.wikipedia.org/wiki/Sports_analytics)
- [What Is Sports Data Analytics? | Teradata](https://www.teradata.com/insights/data-analytics/what-is-sports-data-analytics)
- [Data Literacy Toolkit](https://data-literacy-toolkit.github.io)

## Manutenção

- Ao criar um documento novo, coloque-o em **uma** pasta de status; quando o trabalho terminar, **mova** para `informacao/` ou **apague** o backlog e atualize este `README.md`.
- Evitar duplicar o mesmo conteúdo em duas categorias.
- Evitar duplicação de definição de métricas entre `informacao/dominio/regras-de-negocio.md` e `informacao/analytics/metrics-catalog.md`.
- Tratar `informacao/analytics/metrics-catalog.md` como fonte de verdade semântica para indicadores analíticos.
