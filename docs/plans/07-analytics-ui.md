# Plano 07 — Analytics: UI + fecho de documentação

## Estado

Concluído.

## Objetivo

Consumir API do Plano 06 na SPA com padrão **funcional CLJS** e fechar backlog/roadmap Fase 3.

## Depende de

- [06-analytics-api.md](06-analytics-api.md)

## Padrão FP (CLJS)

- Transições: `(dispatch! [event-type payload])` + `app-reducer` em [`state.cljs`](../../src-cljs/galaticos/state.cljs)
- Estado derivado: `reagent.ratom/reaction` (loading, errors, métricas filtradas)
- Side effects: só [`effects.cljs`](../../src-cljs/galaticos/effects.cljs) (fetch, route)
- Componentes render-only — sem fetch inline
- Opcional: namespace `subs.cljs` para reactions partilhadas (não obrigar re-frame)

Ver [arquitetura-funcional.md](../informacao/arquitetura/arquitetura-funcional.md) e Prompt 9 em [notebooklm-response-fp.md](../informacao/notebookLM/notebooklm-response-fp.md).

## Entregáveis

### Frontend (CLJS)

| Ficheiro | Acção |
|----------|--------|
| [api.cljs](../../src-cljs/galaticos/api.cljs) | `get-player-insights`, extensões dashboard |
| [state.cljs](../../src-cljs/galaticos/state.cljs) | Evoluir para reducer + dispatch (incremental) |
| [effects.cljs](../../src-cljs/galaticos/effects.cljs) | Fetch isolado; route-driven |
| [dashboard.cljs](../../src-cljs/galaticos/components/dashboard.cljs) | Métricas derivadas; reactions para dados filtrados |
| [aggregations.cljs](../../src-cljs/galaticos/components/aggregations.cljs) | Tab/filtro métricas derivadas |
| [players.cljs](../../src-cljs/galaticos/components/players.cljs) | Painel insights; disclaimer se `readiness` false |

### Documentação

- [advanced-analytics-backlog.md](../a-fazer/analytics/advanced-analytics-backlog.md) — marcar concluído / itens opcionais
- [roadmap.md](../informacao/analytics/roadmap.md) — critérios saída Fase 3
- [regras-de-negocio.md](../informacao/dominio/regras-de-negocio.md) — referência a [metrics-catalog.md](../informacao/analytics/metrics-catalog.md)
- [fp-improvement-checklist.md](../a-fazer/notebookLM/fp-improvement-checklist.md) Fase E

## Teste manual

1. Login → dashboard: bloco derivadas
2. Jogador → detalhe: insights ou disclaimer
3. Export CSV com `?include-derived=true`
4. Agregações: métrica derivada no top/lista

## Tarefas

- [x] API client CLJS
- [x] Reducer/reactions (mínimo viável)
- [x] Dashboard + aggregations + player detail UI
- [x] Docs backlog/roadmap
- [x] `./bin/galaticos test` (+ `:cljs-test` se aplicável)

## Critérios de saída

- Fase 3 utilizável (derivadas + insights com disclaimers)
- Efeitos de rede concentrados em `effects.cljs`
- Backlog reflecte itens fora de scope

## Não fazer

- Novo refactor backend (fora de bugs)
- Alterar rollups (Plano 05)

## Fim da sequência

Voltar a [README.md](README.md). Novos planos: performance backlog, etc.
