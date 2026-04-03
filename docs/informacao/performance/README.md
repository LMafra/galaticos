# Performance do frontend Galáticos

## Objetivo

Padronizar como medimos e melhoramos a **experiência de carregamento e interatividade** (Core Web Vitals e métricas correlatas) em cada rota do SPA. Esta pasta conecta inventário de telas, metodologia de auditoria e backlog acionável.

## Documentos

| Documento | Conteúdo |
|-----------|----------|
| [metodologia.md](metodologia.md) | Como rodar Lighthouse (CLI e DevTools), ambiente WSL, rotas públicas vs autenticadas. |
| [inventario-paginas.md](inventario-paginas.md) | Uma linha por rota: path, componente, necessidade de auth, exemplo de URL. |
| [backlog-acoes.md](../../a-fazer/performance/backlog-acoes.md) | Baseline por tela (preencher após auditorias), oportunidades e checklist de tarefas. |

## Comandos rápidos

Com `CHROME_PATH` apontando para Chrome Linux (ver [metodologia.md](metodologia.md)) e a app em `http://localhost:3000`:

| Comando | Uso |
|---------|-----|
| `npm run perf:lighthouse:login` | Lighthouse só categoria Performance em `/`, saída `docs/perf-output/lighthouse-login.json`. |
| `CHROME_PATH=... npm run perf:lighthouse:auth` | Rotas autenticadas (URLs extra opcionais; default dashboard + stats). Ver autenticação abaixo. |
| `npm run perf:bundle-report` | `shadow-cljs release` com relatório de bundle em `target/shadow-bundle-report.html` (pasta `target/` no `.gitignore`). |

Build **release** do CLJS no Docker:  
`docker compose -f config/docker/docker-compose.dev.yml exec -T frontend-watch npx shadow-cljs release app`

**Autenticação em `perf:lighthouse:auth`:** `PERF_AUTH_TOKEN` (JWT) injeta o token sem login na UI; ou `PERF_USE_API_LOGIN=1` com `PERF_LOGIN_USER` / `PERF_LOGIN_PASSWORD` (recomendado em CI com secrets). Sem isso, o script usa login na UI (útil com `db:seed-smoke` e `admin`/`admin` em dev).

**Compressão em ambiente real:** após deploy, validar gzip no JS com GET:  
`curl -sD - -o /dev/null -H 'Accept-Encoding: gzip' http://localhost:3000/js/compiled/app.js` (deve aparecer `Content-Encoding: gzip`).

## Dev vs build de release

- Com **`shadow-cljs watch`** (ou equivalente), o bundle tende a ser **grande e pouco otimizado**; métricas como **Total Blocking Time (TBT)** e **Largest Contentful Paint (LCP)** costumam ficar **piores** do que em produção.
- Para uma baseline séria, preferir medir com **`shadow-cljs release`** (ou o artefato que for servido em produção), no mesmo host/base URL que o utilizador final veria.
- Ainda assim, auditorias em **dev** são úteis para **tendências** e regressões óbvias; registre no backlog **qual build** foi usada na coluna de baseline.

## Autenticação e Lighthouse

Rotas **`/`** (`:home`) e **`/login`** (`:login`) estão descritas no [inventario-paginas.md](inventario-paginas.md); o CLI do Lighthouse em `http://localhost:3000/` mede sobretudo a **primeira carga** do shell + dashboard visitante.

O token JWT fica em **`localStorage`** na chave `galaticos.auth.token` (ver `src-cljs/galaticos/api.cljs`). Por isso:

- Lighthouse em linha de comando contra URLs **protegidas**, **sem** sessão configurada, costuma medir o fluxo de **login/redirecionamento**, não a tela autenticada.
- Para páginas logadas, use [metodologia.md](metodologia.md), DevTools logado, ou `npm run perf:lighthouse:auth` com token/API login conforme a tabela acima.

## Manutenção

Ao adicionar ou remover rotas em `src-cljs/galaticos/routes.cljs` / `core.cljs`, atualizar [inventario-paginas.md](inventario-paginas.md) e, se necessário, [backlog-acoes.md](../../a-fazer/performance/backlog-acoes.md).
