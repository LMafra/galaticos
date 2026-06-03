# Guia: partidas, temporadas e estatísticas híbridas

**Última atualização:** junho de 2026

Este guia descreve a lógica que costuma ser reimplementada a cada nova feature em partidas ou estatísticas. Use-o com [regras-de-negocio.md](regras-de-negocio.md) (`RN-MATCH-*`, `RN-STATS-*`) e os testes indicados abaixo.

## 1. Visão geral

| Camada | O quê | Onde |
|--------|--------|------|
| Regras puras | Temporada (create), inscrição, coerência equipa, merge de stats | `domain/matches.clj`, `domain/analytics.clj` |
| Orquestração | CRUD, temporada, recalc | `logic/matches.clj` |
| Persistência | Matches, seasons, pipeline | `db/matches.clj`, `db/seasons.clj`, `db/aggregations.clj` |
| HTTP | Delegação fina | `handlers/matches.clj` |
| UI | Formulário e bloqueios | `src-cljs/.../matches.cljs` |

Após gravar: persistir → `add-match` na temporada → job incremental ([architecture.md](../analytics/architecture.md)).

## 2. Temporadas e criação

- **Create:** só temporada `active` (ou `:season-id` explícito ativo). Sem ativa → 400. Explícita concluída → 403.
- **Update/delete:** permitidos com temporada concluída (**RN-MATCH-09**).

| Operação | Resolução | Validação temporada |
|----------|-----------|---------------------|
| `create!` | `find-season-for-new-match` | `validate-season-for-new-match` |
| `update!` | `season-id` existente ou `find-default-season-for-championship` | Não bloqueia por status |
| `delete!` | `remove-match-from-season!` se houver `season-id` | — |

**UI:** `has-active-season?`, `create-locked?` (só create). Esconder “Nova Partida” sem temporada ativa. Em edição: manter tabela de stats e “Marcar participação de todos”; só desabilitar “Criar Partida”.

## 3. Estatísticas híbridas

**Problema:** planilha/seed preenche `aggregated-stats`; partidas somam por cima — risco de contar import duas vezes.

**Por campeonato:**

`exibido = pre-match-stats + (rollup_partidas − baseline-match-rollup)`

- `:pre-match-stats` — baseline da planilha.
- `:baseline-match-rollup` — parte do rollup já contada no baseline (import); congelado no merge.
- Títulos: só da planilha, nunca das partidas.

**Fan-out:** rollup sem `:season` funde na única linha `by-championship` do campeonato (ou soma na linha escopada se já existir). Ver `fanout-unscoped-rollups-into-match-map` em `domain/analytics.clj`.

**Testes obrigatórios antes de mudar merge:** `aggregations_test.clj` (baseline, fan-out, idempotência).

**Incremental:** `update-incremental-player-stats!` — defaults preservam baseline; `:drop-stale-without-match-rollups?` só em reconcile explícito.

## 4. Formulário CLJS

- `form-data` / `:player-statistics` por `player-id`.
- `player-stat-row`, `mark-all-participation!`.
- `home-score` = soma de gols dos jogadores.

Checklist extensão: backend `validation/entity` + `is-edit?` vs `create-locked?` + job de stats se mudar contagens.

## 5. Checklist nova feature

- [ ] Create de partida? → temporada ativa (API + UI).
- [ ] Update/delete? → confirmar RN (geralmente permitido).
- [ ] Stats? → testes `merge-aggregated-stats` + job.
- [ ] Partida sem `season-id`? → fan-out.
- [ ] Nova RN em `regras-de-negocio.md` se for regra de produto.

## 6. Referências

- `RN-MATCH-05`–`09`, `RN-STATS-04`–`06` em [regras-de-negocio.md](regras-de-negocio.md)
- `test/galaticos/handlers/matches_test.clj`
- `test/galaticos/db/aggregations_test.clj`
- [03-matches-service.md](../../plans/03-matches-service.md)
- [architecture.md](../analytics/architecture.md)
