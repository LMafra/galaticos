# FP Improvement Checklist (from NotebookLM Responses)

Checklist derivado de [notebooklm-response-fp.md](../../informacao/notebookLM/notebooklm-response-fp.md). Vocabulário **programação funcional** — distinto do checklist OO em [improvement-checklist.md](improvement-checklist.md) (histórico).

**Guia:** [arquitetura-funcional.md](../../informacao/arquitetura/arquitetura-funcional.md)  
**Planos:** [plans/README.md](../../plans/README.md)  
**Estado documentação:** respostas consolidadas; planos 00–07 alinhados FP (2026).

**Regra de equipa:** não misturar refactor estrutural com feature nova no **mesmo** PR.

---

## Fase 0: Rede de segurança (Plano 01 — concluído)

- [x] Testes de contrato HTTP (`api_contract_test.clj`)
- [x] Regra: refactor e feature em PRs separados
- [x] `galaticos.domain.errors` + `wrap-errors` middleware
- [x] `galaticos.validation.entity` na fronteira HTTP
- [ ] Estender `wrap-errors` para mapas `{:error}` (implementação — Plano 02+)

---

## Fase A: Documentação FP (concluída neste plano)

- [x] Decisões consolidadas em `notebooklm-response-fp.md`
- [x] [arquitetura-funcional.md](../../informacao/arquitetura/arquitetura-funcional.md)
- [x] [fp-design-improvements.md](fp-design-improvements.md) actualizado
- [x] Planos [00–07](../../plans/README.md) reescritos (trilha FP única)
- [x] Checklist OO marcado superseded

---

## Fase B: Plano 02 — Championships FP (código — pendente)

- [ ] `db.protocol/championship-store.clj`
- [ ] `domain/championships.clj` — regras puras BRM
- [ ] `logic/championships.clj` — orquestração + `ex-info`
- [ ] Handlers championships sem `try/catch` / sem `service/*`
- [ ] Testes domain + logic (`reify`)
- [ ] **Apagar** `service/championships.clj`, `repository/championships.clj`, `service/championships_test.clj`
- [ ] **Apagar** `handlers/util.clj` se redundante
- [ ] `./bin/galaticos test` verde

---

## Fase C: Plano 03 — Matches FP (código — pendente)

- [ ] `domain/matches.clj`, `logic/matches.clj`, `db.protocol/match-store.clj`
- [ ] BRM: enrolled, team coherence, season, python-seed, jobs recalc intent
- [ ] Testes sem `with-redefs` em vars globais
- [ ] `./bin/galaticos test` verde

---

## Fase D: Plano 04 — Rollout FP (código — concluído)

- [x] `domain/*` + `logic/*` para players, teams, seasons (mínimo)
- [x] `mongodb-schema.md` — índices + desnormalização
- [x] Zero `service/*`/`repository/*` em todo o repo

---

## Fase E: Planos 05–07 — Analytics (código — concluído)

- [x] `domain/analytics.clj` — fórmulas e rollups puros
- [x] `logic/analytics.clj` (sem `service/analytics`)
- [x] API derived + insights + CSV (Plano 06)
- [x] CLJS: `dispatch!` / `app-reducer`, `dashboard-derived-reaction`, `ensure-player-insights!` (Plano 07)
- [ ] Separar merge/pipeline IO vs cálculo puro em aggregations (refactor opcional)
- [ ] Jobs: intent maps (evolução futura)
- [ ] Propriedade `recompute == cache` em testes (cobertura adicional)

---

## Critério global de fecho (código)

```bash
rg 'galaticos\.(service|repository)' src/ test/
# → zero resultados
./bin/galaticos test
```

---

## Referência: temas por fase

| Tema | Prompts | Fase plano |
|------|---------|------------|
| Arquitectura / protocol | 1 | B, C, D |
| Domínio puro | 2 | B, C |
| Erros HTTP | 3 | B |
| Validação | 4 | B+ |
| Testes | 5 | B, C |
| Monger | 6 | B–E |
| Analytics | 7 | E |
| Jobs | 8 | C, E |
| CLJS | 9 | E |
| Ordem | 10 | README |

---

## Relação com checklist OO

| Checklist OO | Estado |
|--------------|--------|
| Fases 2–5 Repository/Service/DI | **Superseded** — ver Fases B–D acima |
| Fase 6 Errors/validation | Parcialmente feita (Plano 01); evoluir em FP |
| Fase 7 Data/schema | Mantém-se; Plano 04 |
| Fase 8 Command/Strategy/Observer | **Não aplicar** |
