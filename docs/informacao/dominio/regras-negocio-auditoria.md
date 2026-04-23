# Auditoria: regras de negócio vs implementação

**Data:** 2026-04-23  
**Fonte de requisitos:** [regras-de-negocio.md](regras-de-negocio.md)  
**Evidência automática:** `./bin/galaticos test` — **119 testes, 472 asserções, 0 falhas** (backend + ClojureScript).

---

## Resumo executivo

| Área | Status |
|------|--------|
| Catálogo **RN-*** (65 regras) | Implementação presente nos módulos citados; **referências a linhas** no doc principalmente **desatualizadas** (drift). |
| Apêndice **BRM-01–16** | Maioria coberta por API + UI + `seed_mongodb.py`; **BRM-14** “idealmente outros filtros no dashboard” só parcial (dashboard envia `q`; filtros extra na página Jogadores). |
| **RN-PEND-*** no apêndice | Identificadores **não** existem como `### RN-PEND-*` no doc; comportamentos mapeados abaixo. |
| **Q-01–Q-06** | Continuam **decisões de negócio / doc**; não bloqueiam verificação técnica mas impedem “100% fechado” no manifesto. |
| Jobs de agregados (player stats) | Retry + registo de último sucesso (Mongo) + `GET /api/aggregations/player-stats-jobs` (auth); ver `player_stats_jobs.clj` e [technical-evolution (parcial)](../../parcial/analytics/technical-evolution.md). |

---

## BRM-01 a BRM-16 (requisito)

| ID | Verificação | Resultado |
|----|-------------|-----------|
| BRM-01 CRUD jogadores | `handlers/players.clj`, `db/players.clj`; nome+posição obrigatórios; soft delete | ✓ |
| BRM-02 CRUD times | `handlers/teams.clj`, `db/teams.clj`; bloqueio delete com jogadores | ✓ |
| BRM-03 CRUD campeonatos | `handlers/championships.clj`; temporadas ligadas via `seasons` + enrich | ✓ |
| BRM-04 CRUD partidas | `handlers/matches.clj`, `player_stats_jobs.clj`; stats obrigatórias | ✓ |
| BRM-05 temporada ativa única | `seasons-db/create`, `activate!`, `handlers/seasons.clj` | ✓ |
| BRM-06 gestão temporadas | API seasons + UI; texto do apêndice ainda menciona modelo só `season` no campeonato — **doc desatualizado** | ⚠ doc |
| BRM-07 finalizar campeonato | `finalize-championship`; uma vez; `finished-at` | ✓ |
| BRM-08 títulos condicionais | `titles-award-count` 0 vs >0 + `winner-player-ids` | ✓ |
| BRM-09 inscrição + time + partida | `validate-player-team-coherence` + `validate-players-enrolled` em `matches.clj` | ✓ |
| BRM-10 ver jogadores da partida | API match + stats; UI em `matches.cljs` | ✓ |
| BRM-11 stats + placar + agregados | jobs + `aggregations.clj` | ✓ |
| BRM-12 dashboard → telas | `dashboard.cljs`: cards → `:players`, `:matches`, `:championships`, `:teams` | ✓ |
| BRM-13 busca ao inscrever | `player_picker.cljs`: filtro local nome/apelido sobre catálogo | ✓ |
| BRM-14 busca dashboard | Dashboard: `q` → `:players`; lista Jogadores: `api/search-players` + posição; sem filtro time no dashboard | ⚠ parcial vs “idealmente” |
| BRM-15 import inscrição | `scripts/python/seed_mongodb.py`: `enrolled-player-ids`, `register_enrollment` | ✓ |
| BRM-16 GK no nome | `seed_mongodb.py`: inferência se posição vazia; precedência explícita documentada no script | ✓ |

---

## RN-PEND (apêndice) → código real

| Referência apêndice | Onde está |
|---------------------|-----------|
| RN-PEND-01 / 02 inscrição | `championships.cljs` + `seasons` `enrolled-player-ids`; handlers championship/season add player |
| RN-PEND-03 finalizar + títulos | `handlers/championships.clj` `finalize-championship`; `handlers/seasons.clj` `finalize-season`; `db/seasons.clj` `finalize!` |
| RN-PEND-04 jogadores partida no campeonato | `matches.clj` `validate-players-enrolled` |
| RN-PEND-05 recálculo agregados | `analytics/player_stats_jobs.clj` + `db/aggregations.clj` |

---

## Catálogo RN-* (65) — ficheiros e drift

Todos os paths explícitos únicos abaixo **existem** no repositório (verificação 2026-04-23).

**Grupos com implementação alinhada à descrição:** RN-AUTH (6), RN-CHAMP, RN-TEAM, RN-PLAYER, RN-MATCH, RN-STATS (funções presentes), RN-VALID, RN-REF, RN-RESP, RN-TIME, RN-QUERY-01, RN-INIT.

**Atenção documental (⚠):**

1. **Números de linha** em `regras-de-negocio.md` vs código atual: muitas secções (ex. `championships.clj` campos obrigatórios doc ~11–12; código real `allowed-championship-fields` ~12–17, `validate-championship-body` ~103+) — **tratar linhas como indicativas**.
2. **RN-QUERY-02** aponta `aggregations.clj` linha 34; linha 34 é `agg-entity-id-str`, não ordenação. Ordenação por métrica está em pipelines `$sort` (ex. ~208, 235, 416, 594, 654+).
3. **RN-STATS-01 a RN-STATS-09**: intervalos de linha no doc não batem com o ficheiro atual; funções `player-stats-by-championship` (~182), `avg-goals-by-position` (~213), `search-players` (~392), etc.
4. **read_excel.py**: utilitário de leitura Excel; regra **BRM-16** está em **`seed_mongodb.py`**, não só em `read_excel.py` como sugere o apêndice.

---

## Questões abertas Q-01–Q-06

| ID | Estado na implementação |
|----|-------------------------|
| Q-01 múltiplas temporadas | **Parcialmente resolvido** no produto: coleção `seasons` + `championship-id`; doc apêndice ainda ambíguo. |
| Q-02 reabrir campeonato | Não auditado como regra formal; exige decisão de negócio. |
| Q-03 critérios de busca | Backend `search-players` usa normalização + regex; “accent-insensitive” depende de `normalize-text` — validar com `str-util` e dados reais. |
| Q-04 conflitos import | **Alinhado ao** [checklist](../../a-fazer/dominio/regras-de-negocio-checklist.md) e a **Q-04** em [regras-de-negocio.md](regras-de-negocio.md): política no `seed_mongodb.py` (ex.: chave por nome, planilha prevalece em `aggregated-stats.total` onde aplicável). |
| Q-05 GK vs posição | **Precedência** em `seed_mongodb.py` (posição explícita vs inferência). |
| Q-06 mapa card→rota | Dashboard tem mapeamento implícito; tabela formal opcional no doc. |

---

## Recomendações

1. ~~Atualizar [regras-de-negocio.md](regras-de-negocio.md)~~ **Feito (2026-04-23)**: tabela **Mapeamento RN-PEND**, BRM-05/BRM-12/BRM-14/BRM-16, RN-STATS-01–10 e RN-QUERY-02 alinhados a funções; Q-01/Q-05/Q-06/Q-03 parcialmente fechados no texto.
2. Manter regressão: CI já executa testes; após mudanças em regras críticas, alinhar com [testing-coverage.md](testing-coverage.md).
3. **Pendente negócio**: Q-02 (reabrir campeonato); extensão opcional BRM-14 (filtro time no dashboard, fora do âmbito Fase 2 analytics). Q-04 encontra-se alinhada ao checklist/seed; evoluções (relatório de conflitos) são opcionais.

---

## Comando de verificação

```bash
./bin/galaticos test
```

Opcional: `./bin/galaticos coverage` para ramos fracos vs RN-STATS / handlers.
