# Programação Funcional – Melhorias Galáticos

Tradução **projecto-específica** das respostas FP do NotebookLM. Complementa [design-and-db-improvements.md](design-and-db-improvements.md) (orientado a OO/service layer — **histórico**).

**Guia principal:** [arquitetura-funcional.md](../../informacao/arquitetura/arquitetura-funcional.md)  
**Prompts:** [notebooklm-prompts-fp.md](../../informacao/notebookLM/notebooklm-prompts-fp.md)  
**Respostas:** [notebooklm-response-fp.md](../../informacao/notebookLM/notebooklm-response-fp.md)  
**Checklist:** [fp-improvement-checklist.md](fp-improvement-checklist.md)  
**Planos:** [plans/README.md](../../plans/README.md)

---

## Decisões consolidadas

| Tema | Decisão |
|------|---------|
| Namespaces | `domain/*` + `logic/*` + `handlers/*` + `db/*` + `db.protocol/*` |
| DI | `defprotocol` + `reify` nos testes; **proibido** `repo-call`/`ns-resolve` |
| Erros | `{:ok}`/`{:error}` no domain; `ex-info` em `logic/*`; middleware HTTP |
| Validação | `validation/entity.clj` + `comp`; sem Malli/spec por agora |
| Testes | domain puro → logic + `reify` → contract HTTP → Mongo opcional |
| Analytics | rollups puros em `domain/analytics`; Mongo filtra volume |
| Jobs | intent map + executor in-process |
| CLJS | reducer + `reaction`; efeitos isolados |
| Ordem | 02 → 03 → 04 → 05 → 06 → 07 |
| Championships | Migrar para FP e **apagar** OO (Plano 02) — não manter legado |

---

## Mapa OO → FP

| Padrão OO actual | Onde está | Alvo FP |
|------------------|-----------|---------|
| Service layer | `service/championships.clj` | `domain/championships.clj` + `logic/championships.clj` |
| Repository facade | `repository/championships.clj` | `db.protocol/championship-store.clj` + `db/*` |
| DI via `ns-resolve` | `repo-call` | protocol + argumento explícito |
| Domain exceptions everywhere | `domain/errors.clj` em service | `errors/*!` só em `logic/*`; domain retorna mapas |
| Handler try/catch | `handlers/championships.clj` | middleware unificado |
| `handlers/util.clj` | mapeamento exceção | `middleware/errors.clj` |
| Validação manual | `validation/entity.clj` | manter + pipelines `comp` |
| Test doubles OO | `service/championships_test.clj` | `domain/*_test` + `logic/*_test` com `reify` |
| Mongo + doseq merge | `db/aggregations.clj` | `domain/analytics` puro + persist separado |
| Job acoplado | `analytics/player_stats_jobs.clj` | intent map + runner |
| CLJS setters | `state.cljs` | `dispatch!` + `app-reducer` + `reaction` |

---

## De → Para (completo)

| Actual (remover na fase código) | Alvo FP |
|----------------------------------|---------|
| `service/championships.clj` | `domain/championships.clj` + `logic/championships.clj` |
| `repository/championships.clj` | `db.protocol/championship-store.clj` |
| `handlers/util.clj` | `middleware/errors.clj` |
| `service/matches.clj` (se existir) | `domain/matches.clj` + `logic/matches.clj` |
| `repository/matches.clj` (se existir) | `db.protocol/match-store.clj` |
| `service/analytics` (Plano 06) | `logic/analytics.clj` ou handlers + domain puro |
| `handlers/*` try/catch | middleware + handlers directos |
| `validation/entity.clj` | evolução incremental com `comp` |

---

## 1. Handlers e HTTP

**Actual:** [`handlers/championships.clj`](../../src/galaticos/handlers/championships.clj) — parse, validate, `service/*`, `try/catch`.

**Alvo FP:**

- Handler: validação → `(logic/operation store args)` → `resp/success`
- Erros: middleware [`wrap-errors`](../../src/galaticos/middleware/errors.clj) estendido para `{:error}` quando handlers retornarem Result
- Remover dependência de [`handlers/util.clj`](../../src/galaticos/handlers/util.clj)

---

## 2. Domínio e regras BRM

**Actual:** [`service/championships.clj`](../../src/galaticos/service/championships.clj) — IO + `cond` duplicado.

**Extrair para `domain/championships.clj`:**

| Regra | Função pura sugerida |
|-------|----------------------|
| Apagar campeonato | `can-delete?` → `{:ok}` / `{:error :conflict}` |
| Finalizar | `finalization-decision` |
| Inscrever jogador | `enrollment-decision` |
| Matches (Plano 03) | `validate-enrolled`, `validate-team-coherence` |

Ver [regras-de-negocio.md](../../informacao/dominio/regras-de-negocio.md).

**Orquestração:** `logic/championships.clj` — busca store, chama domain, persiste ou `throw` com `ex-info`.

---

## 3. Acesso a dados (Monger)

**Actual:** [`db/championships.clj`](../../src/galaticos/db/championships.clj) — `(db)` implícito.

**Alvo:**

- Protocol `ChampionshipStore` / `MatchStore`
- Funções `db/*` implementam protocol ou recebem `db` explícito
- Transformações puras (enrich document) em `domain/*`

---

## 4. Analytics e cache

**Actual:** [`db/aggregations.clj`](../../src/galaticos/db/aggregations.clj) + [`player_stats_jobs.clj`](../../src/galaticos/analytics/player_stats_jobs.clj).

**Alvo:**

- `domain/analytics.clj` — `goal-contribution`, `discipline-index`, `summarize-player-stats`, `merge-aggregated-stats` (puro)
- Invariante testável: `recompute(all-matches) == cache`
- Jobs: `{:op :recalc-stats :player-ids [...]}`

Planos [05–07](../../plans/README.md); [reconciliation-runbook.md](../../informacao/analytics/reconciliation-runbook.md).

---

## 5. Frontend CLJS

**Actual:** [`state.cljs`](../../src-cljs/galaticos/state.cljs), [`effects.cljs`](../../src-cljs/galaticos/effects.cljs).

**Alvo (Plano 07):**

- `app-reducer` + `dispatch!`
- `reaction` para loading, errors, métricas filtradas
- Componentes sem side effects

---

## 6. Testes

| Camada | Actual | Alvo |
|--------|--------|------|
| Contrato HTTP | `api_contract_test.clj` | Manter |
| Domain | misturado em service tests | `domain/*_test.clj` — só mapas |
| Logic | `with-redefs` | `reify` store |
| Integração Mongo | opcional | fixtures mínimas |

---

## 7. Piloto championships

**Decisão:** **Opção C** — refactor para FP no Plano 02; apagar `service/*` e `repository/*`. Código OO permanece no repo até executares esse plano.

---

## Próximos passos

1. Ler [arquitetura-funcional.md](../../informacao/arquitetura/arquitetura-funcional.md)
2. Seguir [plans/README.md](../../plans/README.md) — Plano 02 em diante (fase código)
3. Marcar [fp-improvement-checklist.md](fp-improvement-checklist.md) ao implementar
