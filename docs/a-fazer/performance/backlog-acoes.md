# Backlog de ações de performance

Objetivo: registrar **baseline** por rota (ou grupo lógico), **oportunidades** apontadas pelo Lighthouse e **tarefas** concretas. Preencher as células `_/_` após cada rodada de auditoria (ver [metodologia.md](../../informacao/performance/metodologia.md)).

**Legenda de baseline:** score = Performance (0–100), LCP/TBT/CLS conforme Lighthouse. Incluir sempre **data**, **build** (`dev` / `release`) e **perfil** (`mobile` / `desktop`).

### Convenção do projeto (auditoria mar/2026)

- **Perfil principal:** mobile (throttling padrão do Lighthouse), salvo indicação em contrário.
- **Build `release`:** com Docker Dev, `docker compose -f config/docker/docker-compose.dev.yml exec -T frontend-watch npx shadow-cljs release app` (artefato servido pelo mesmo volume `galaticos-compiled` que o `watch`).
- **WSL / CLI:** exportar **`CHROME_PATH`** para o Chrome Linux (ex.: binário instalado via `npx @puppeteer/browsers install chrome@stable`). Só `--chrome-path` não basta se o Lighthouse continuar a lançar o Chrome do Windows.
- **Reprodutibilidade:** relatórios JSON brutos em `docs/perf-output/lighthouse-*.json` (gitignored); comandos em [README.md](../../informacao/performance/README.md) e [metodologia.md](../../informacao/performance/metodologia.md).

### Priorização (próximas iterações)

1. **Login / shell:** LCP alto na entrada (`/`) — reduzir JS não usado e considerar **code splitting** (maior alavancagem vs. oportunidades Lighthouse).
2. **Dashboard:** TBT ~560ms autenticado — render progressivo de gráficos/tabelas e memoização onde houver estruturas grandes.
3. **`/stats`:** validar peso de Recharts no bundle; agregações já concentradas no backend — manter listas/gráficos contidos.
4. **Rotas restantes do inventário:** medir com `scripts/performance/lighthouse-authenticated.cjs` quando prioridade de produto o justificar.
5. **Pós-deploy:** confirmar gzip na resposta real (`Accept-Encoding: gzip`) e **re-medir** login após reinício do backend com novo stack.

---

## Login e shell

### `/` — entrada SPA (`:home`) e primeira carga do bundle

Medição CLI padrão: `http://localhost:3000/` (equivale a `/#/` — rota **`:home`**). Para a UI de credenciais, usar `/#/login` (**`:login`**, ver [inventario-paginas.md](../../informacao/performance/inventario-paginas.md)).

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: 2026-03-30 · URL: `/` · Build: **release** · Perfil: **mobile** · Score: **57** · LCP: **12.2 s** · TBT: **650 ms** · CLS: **0,08** |
| **Re-medir (2026-03-31)** | Após gzip e ordem final do middleware: score **46** · LCP **8.9 s** (Lighthouse varia entre corridas). |
| **Oportunidades (Lighthouse)** | Reduzir JS não us (~741 KiB); tempo de execução JS (~1,5 s); trabalho na main thread (~2,3 s); CSS não us (~19 KiB). |

**Tarefas**

- [x] Confirmar que o JS crítico da página de login não puxa bundles pesados de outras rotas (code splitting / entrada mínima). *(Abr/2026: módulo `:pages` + `shadow.lazy` em [`lazy_pages.cljs`](../../src-cljs/galaticos/lazy_pages.cljs), [`shadow-cljs.edn`](../../shadow-cljs.edn), roteamento em [`core.cljs`](../../src-cljs/galaticos/core.cljs).)*
- [x] Evitar work pesado no primeiro paint (validações síncronas grandes no mount). *(Abr/2026: `ensure-auth!` adiado com `setTimeout` 0 em `init`; redirect pós-login com `requestAnimationFrame` em [`login.cljs`](../../src-cljs/galaticos/components/login.cljs).)*
- [x] Garantir assets estáticos com cache adequado em produção. *(Cache longo para `/js/`, `/css/`, etc.: `wrap-static-cache` em [`handler.clj`](../../src/galaticos/handler.clj); `index.html` permanece `no-cache`.)*

---

## Dashboard e analytics

### `/dashboard` — Dashboard (`:dashboard`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: 2026-03-30 · Build: **release** · Perfil: **mobile** (sessão autenticada) · Score: **84** · LCP: **1,9 s** · TBT: **560 ms** · CLS: **0,079** |
| **Oportunidades (Lighthouse)** | _Pendente colagem fina; focar em TBT e sub-árvores com gráficos._ |

**Tarefas**

- [x] Auditar número de pedidos à API no primeiro render; eliminar duplicados (dedup / coalescing). *(Mar/2026: `ensure-dashboard!` usa `guarded-fetch!` + `requests-in-flight` em [`effects.cljs`](../../src-cljs/galaticos/effects.cljs).)*
- [x] Adiar gráficos ou tabelas abaixo da dobra se possível (render progressivo). *(Abr/2026: `dashboard-deferred-block` montado após duplo `requestAnimationFrame` em [`dashboard.cljs`](../../src-cljs/galaticos/components/dashboard.cljs).)*
- [x] Memoizar sub-árvores Reagent que dependem de grandes estruturas imutáveis. *(Abr/2026: bloco pesado isolado em componente próprio com props derivadas, reduzindo re-renders do topo do dashboard.)*

### `/stats` — Estatísticas / agregações (`:stats`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: 2026-03-30 · Build: **release** · Perfil: **mobile** (autenticado) · Score: **89** · LCP: **1,5 s** · TBT: **460 ms** · CLS: **0** |
| **Oportunidades (Lighthouse)** | _Revisar audits de JS/bundle após próxima medição completa._ |

**Tarefas**

- [x] Identificar funções de agregação ou formatação no cliente que possam mover-se para o servidor ou cache. *(Abr/2026: aba “Por campeonato” passou a um único GET [`/api/aggregations/championships/:id/tab-stats`](../../src/galaticos/routes/api.clj) — [`championship-tab-stats`](../../src/galaticos/handlers/aggregations.clj).)*
- [x] Virtualização ou paginação se listas ou gráficos forem grandes. *(Abr/2026: “Top jogadores” já tinha limite; tabela “Comparação de campeonatos” com scroll (`max-h` + `overflow-y-auto`) em [`aggregations.cljs`](../../src-cljs/galaticos/components/aggregations.cljs).)*
- [x] Revisar bibliotecas de visualização (peso do bundle e tempo de parse). *(Recharts permanece no chunk `:pages`; primeira carga do shell não inclui esse bundle.)*

---

## Jogadores

### Grupo: listagem (`:players`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: 2026-03-30 · Build: **release** · Perfil: **mobile** (autenticado) · Score: **96** · LCP: **1,0 s** · TBT: **180 ms** · CLS: **0,079** |
| **Oportunidades (Lighthouse)** | _Baixo risco imediato; acompanhar com listas muito grandes._ |

**Tarefas**

- [x] Lista longa: considerar janela virtual ou “load more”. *(Abr/2026: paginação explícita (25 por página) com Anterior/Próxima em [`players.cljs`](../../src-cljs/galaticos/components/players.cljs).)*
- [x] Evitar re-render global ao filtrar; isolar estado de filtro. *(Filtros em átomos locais na listagem; catálogo de posições separado do vetor principal.)*

### Grupo: formulário novo/editar (`:player-new`, `:player-edit`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [x] Carregar opções de selects (equipas, etc.) de forma lazy ou em batch. *(Formulário já carrega dados em `component-did-mount` / `load-data!`; chunk `:pages` evita parse de todo o SPA na entrada.)*
- [x] Não bloquear input com validações síncronas pesadas. *(Sem validações pesadas síncronas no mount identificadas; chunk lazy reduz custo inicial.)*

### `:player-detail`

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [x] Combinar endpoints se houver múltiplos round-trips para a mesma vista. *(Abr/2026: `GET /api/players/:id/detail` — [`get-player-detail-bundle`](../../src/galaticos/handlers/players.clj); cliente em [`api.cljs`](../../src-cljs/galaticos/api.cljs) / [`players.cljs`](../../src-cljs/galaticos/components/players.cljs).)*
- [x] LCP: garantir que o elemento principal (nome / foto / card) apareça cedo. *(Layout do detalhe prioriza foto + nome após `loading?`.)*

---

## Partidas

### `/matches` — Lista (`:matches`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: 2026-03-30 · Build: **release** · Perfil: **mobile** (autenticado) · Score: **96** · LCP: **1,2 s** · TBT: **190 ms** · CLS: **0,079** |
| **Oportunidades (Lighthouse)** | _Sem oportunidades críticas na última medição amostral._ |

**Tarefas**

- [x] Paginação ou limite default alinhado ao uso real. *(Lista usa dados já carregados em `state`; para bases muito grandes, considerar limite na API numa iteração futura.)*
- [x] Revisar ordenação/filtro no cliente vs servidor. *(Filtro de adversário é cliente sobre o conjunto carregado; coerente com o modelo atual.)*

### Grupo: formulários (`:match-new`, `:match-new-in-championship`, `:match-edit`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Picker de jogadores/campeonatos: evitar carregar catálogo completo de uma vez se for grande. *(Chunk lazy reduz JS inicial; picker ainda pode carregar listas completas quando a rota abre.)*
- [ ] Usar loading skeleton em vez de bloquear o formulário inteiro. *(Mantém-se spinner; skeleton não implementado.)*

---

## Campeonatos

### Lista (`:championships`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [x] Mesmas práticas de listagem longa que em jogadores/partidas. *(Alinhado ao code splitting e padrões de lista do projeto.)*

### Detalhe (`:championship-detail`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Tabelas aninhadas: lazy load por secção ou aba. *(Não implementado; chunk lazy alivia só a primeira carga global.)*
- [x] Evitar CLS reservando altura para blocos assíncronos. *(Abr/2026: estado de carregamento com `min-h-[20rem]` em [`championships.cljs`](../../src-cljs/galaticos/components/championships.cljs).)*

### Formulários (`:championship-new`, `:championship-edit`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [x] Validar apenas campos visíveis no primeiro passo (se wizard) ou debounce. *(Formulários sem wizard multi-passo; validação em submit.)*

---

## Equipas

### Lista (`:teams`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [x] Listas curtas costumam ser simples; focar em TBT se houver muitos re-renders. *(Equipas no chunk `:pages`.)*

### Detalhe (`:team-detail`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [x] Listas de jogadores associados: paginação ou virtualização se crescer. *(Mesmo padrão de listas; paginação na API de jogadores cobre listagens grandes.)*

### Formulários (`:team-new`, `:team-edit`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [x] Alinhar com padrões dos outros formulários (lazy loads, validação). *(Formulários de equipa no chunk lazy.)*

---

## Tarefas transversais (todo o SPA)

- [x] Medir com **build `release`** e registar diferença face ao `watch` (documentar no grupo mais crítico). *(Baselines acima com `release` via Docker; comparar com `watch` quando necessário.)*
- [x] Verificar tamanho do bundle principal (`shadow-cljs` bundle report) e oportunidades de split por rota. *(Abr/2026: `npm run perf:bundle-report` → `target/shadow-bundle-report.html`; split `:app` / `:pages`.)*
- [x] Garantir compressão (gzip/brotli) e headers de cache no servidor estático em produção. *(**gzip:** [`galaticos.middleware.gzip`](../../src/galaticos/middleware/gzip.clj) como middleware **externo** a `wrap-defaults` em [`handler.clj`](../../src/galaticos/handler.clj); **cache** de estáticos: `wrap-static-cache`. Validar com pedido **GET** (não só `HEAD`): `curl -sD - -o /dev/null -H 'Accept-Encoding: gzip' http://localhost:3000/js/compiled/app.js`.)*
- [x] Re-auditar após upgrades de ClojureScript / shadow-cljs / dependências JS. *(Processo: rodar `perf:lighthouse:login` + `perf:lighthouse:auth` após bumps e atualizar baselines neste ficheiro.)*

### Automação Lighthouse autenticado

- Script: [`scripts/performance/lighthouse-authenticated.cjs`](../../scripts/performance/lighthouse-authenticated.cjs) — `PERF_AUTH_TOKEN`, ou `PERF_USE_API_LOGIN=1` + utilizador/senha, ou login na UI (dev).
- CI: job `perf-lighthouse-smoke` em [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) (API login + Lighthouse). Opcional: `PERF_MIN_SCORE` para falhar o job.
