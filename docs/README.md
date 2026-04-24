# Galáticos — Hub de documentação

Documentação central do projeto, organizada por **status** (Informação, Concluído, Parcial, A Fazer) e por **tema** dentro de cada status.

## Como navegar

- `README.md` (raiz do repositório): visão geral do projeto, setup e execução.
- `docs/README.md` (este arquivo): índice por status.
- Saídas locais do Lighthouse (JSON): `docs/perf-output/` (gitignored; ver documentação em Informação / performance).

## Informação

Referência estável: visão, processos, modelos e como medir.

### Analytics (`docs/informacao/analytics/`)

- [strategy.md](informacao/analytics/strategy.md) — visão, objetivos e casos de uso analíticos.
- [architecture.md](informacao/analytics/architecture.md) — arquitetura de dados e fluxo analítico fim a fim.
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

### Qualidade (`docs/informacao/qualidade/`)

- [auditoria-alucinacoes-ia.md](informacao/qualidade/auditoria-alucinacoes-ia.md) — guia para auditar código assistido por IA (deps, Monger, Ring/Compojure, CLJS, prompts e validação com testes e clj-kondo).

### Operação (`docs/informacao/operacao/`)

- [runbook-producao.md](informacao/operacao/runbook-producao.md) — deploy, backup, seed e índices em produção sem perda de dados.
- [vps-hospedeiro.md](informacao/operacao/vps-hospedeiro.md) — SSH, `.env` do Compose, Nginx/HTTPS, build Docker na VPS (Clojars/MTU), seed com Excel e checklist de deploy.

### Domínio (`docs/informacao/dominio/`)

- [regras-de-negocio.md](informacao/dominio/regras-de-negocio.md) — regras funcionais e técnicas do domínio.
- [mongodb-schema.md](informacao/dominio/mongodb-schema.md) — modelagem das coleções MongoDB.
- [testing-coverage.md](informacao/dominio/testing-coverage.md) — cobertura de testes e estratégia de qualidade.

### NotebookLM (`docs/informacao/notebookLM/`)

- [notebooklm-prompts.md](informacao/notebookLM/notebooklm-prompts.md)
- [notebooklm-response.md](informacao/notebookLM/notebooklm-response.md)

## Concluído

Registros de entregas ou fases encerradas (snapshot).

### Domínio (`docs/concluido/dominio/`)

- [IMPLEMENTATION.md](concluido/dominio/IMPLEMENTATION.md) — visão da implementação inicial do schema e módulos (histórico).

## Parcial

Planos ou trabalhos em curso, ainda não totalmente aplicados.

### Analytics (`docs/parcial/analytics/`)

- [technical-evolution.md](parcial/analytics/technical-evolution.md) — desacoplamento da recomputação analítica e observabilidade.

## A Fazer

Backlogs, checklists de pendências e melhorias a executar.

### Analytics (`docs/a-fazer/analytics/`)

- [advanced-analytics-backlog.md](a-fazer/analytics/advanced-analytics-backlog.md)

### Performance (`docs/a-fazer/performance/`)

- [backlog-acoes.md](a-fazer/performance/backlog-acoes.md)

### Domínio (`docs/a-fazer/dominio/`)

- [regras-de-negocio-checklist.md](a-fazer/dominio/regras-de-negocio-checklist.md)

### NotebookLM (`docs/a-fazer/notebookLM/`)

- [design-and-db-improvements.md](a-fazer/notebookLM/design-and-db-improvements.md)
- [improvement-checklist.md](a-fazer/notebookLM/improvement-checklist.md)

## Referências externas (trilha de analytics)

- [Sports analytics - Wikipedia](https://en.wikipedia.org/wiki/Sports_analytics)
- [What Is Sports Data Analytics? | Teradata](https://www.teradata.com/insights/data-analytics/what-is-sports-data-analytics)
- [Data Literacy Toolkit](https://data-literacy-toolkit.github.io)

## Manutenção

- Ao criar um documento novo, coloque-o em **uma** pasta de status; se o trabalho evoluir (ex.: checklist concluído), **mova** o arquivo e atualize este `README.md`.
- Evitar duplicar o mesmo conteúdo em duas categorias.
- Evitar duplicação de definição de métricas entre `informacao/dominio/regras-de-negocio.md` e `informacao/analytics/metrics-catalog.md`.
- Tratar `informacao/analytics/metrics-catalog.md` como fonte de verdade semântica para indicadores analíticos.
