# Performance do frontend Galáticos

## Objetivo

Padronizar como medimos e melhoramos a **experiência de carregamento e interatividade** (Core Web Vitals e métricas correlatas) em cada rota do SPA. Esta pasta conecta inventário de telas, metodologia de auditoria e backlog acionável.

## Documentos

| Documento | Conteúdo |
|-----------|----------|
| [metodologia.md](metodologia.md) | Como rodar Lighthouse (CLI e DevTools), ambiente WSL, rotas públicas vs autenticadas. |
| [inventario-paginas.md](inventario-paginas.md) | Uma linha por rota: path, componente, necessidade de auth, exemplo de URL. |
| [backlog-acoes.md](backlog-acoes.md) | Baseline por tela (preencher após auditorias), oportunidades e checklist de tarefas. |

## Dev vs build de release

- Com **`shadow-cljs watch`** (ou equivalente), o bundle tende a ser **grande e pouco otimizado**; métricas como **Total Blocking Time (TBT)** e **Largest Contentful Paint (LCP)** costumam ficar **piores** do que em produção.
- Para uma baseline séria, preferir medir com **`shadow-cljs release`** (ou o artefato que for servido em produção), no mesmo host/base URL que o utilizador final veria.
- Ainda assim, auditorias em **dev** são úteis para **tendências** e regressões óbvias; registre no backlog **qual build** foi usada na coluna de baseline.

## Autenticação e Lighthouse

O token JWT fica em **`localStorage`** na chave `galaticos.auth.token` (ver `src-cljs/galaticos/api.cljs`). Por isso:

- Lighthouse em linha de comando contra URLs **protegidas**, **sem** sessão configurada, costuma medir o fluxo de **login/redirecionamento**, não a tela autenticada.
- Para páginas logadas, use a abordagem descrita em [metodologia.md](metodologia.md) (DevTools logado ou automação futura com token).

## Manutenção

Ao adicionar ou remover rotas em `src-cljs/galaticos/routes.cljs` / `core.cljs`, atualizar [inventario-paginas.md](inventario-paginas.md) e, se necessário, [backlog-acoes.md](backlog-acoes.md).
