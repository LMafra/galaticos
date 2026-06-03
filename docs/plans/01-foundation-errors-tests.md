# Plano 01 — Fundação: erros de domínio + testes de contrato

## Estado

**Concluído** — base compatível com migração FP. Não reimplementar; evoluir conforme planos 02+.

## Objetivo

Rede de segurança e espinha dorsal de validação/erros antes do refactor funcional.

## Depende de

- [00-prerequisite-green-tests.md](00-prerequisite-green-tests.md) (se testes não estiverem verdes)

## Entregáveis (no repo)

| Ficheiro | Conteúdo |
|----------|----------|
| `src/galaticos/domain/errors.clj` | `not-found!`, `conflict!`, `validation!`, `forbidden!` → `ex-info` com `{:status :message :code}` |
| `src/galaticos/validation/entity.clj` | allowed/required + validate championship/match/player-stats |
| `test/galaticos/routes/api_contract_test.clj` | Contratos HTTP: envelope JSON, 401, rotas públicas |
| `src/galaticos/middleware/errors.clj` | `wrap-errors` mapeia `ex-data` |

## Tarefas

- [x] Implementar `galaticos.domain.errors`
- [x] Implementar `galaticos.validation.entity`
- [x] Testes de contrato em handler/app
- [x] Confirmar `wrap-errors` mapeia `:status` e `:message`
- [x] `./bin/galaticos test`

## Revisão FP — compatibilidade

| Entregável | Manter | Evoluir (Plano 02+) |
|------------|--------|---------------------|
| `validation/entity.clj` | Sim | pipelines `comp`; coerção ObjectId na fronteira |
| `api_contract_test.clj` | Sim | correr antes/depois de cada slice FP |
| `domain/errors.clj` | Sim | funções **puras** em `domain/*` usam `{:ok}`/`{:error}`; `errors/*!` em `logic/*` |
| `wrap-errors` | Sim | mapear também `{:error}` nos handlers |
| Regra PR (sem refactor + feature) | Sim | [fp-improvement-checklist.md](../a-fazer/notebookLM/fp-improvement-checklist.md) Fase 0 |

## Critérios de saída (mantidos)

- Testes de contrato falham se o formato de resposta mudar sem actualização intencional
- Contract tests verdes durante toda a migração FP

## Documentação

- [fp-improvement-checklist.md](../a-fazer/notebookLM/fp-improvement-checklist.md) Fase 0
- Checklist OO Fase 1: histórico em [improvement-checklist.md](../a-fazer/notebookLM/improvement-checklist.md)

## Próximo plano

[02-championships-service.md](02-championships-service.md)
