# Plano 04 — Rollout FP: players/teams/seasons + documentação

## Estado

Concluído.

## Objetivo

Aplicar padrão FP nos recursos restantes (mínimo write path) e fechar documentação de schema. **Sem** Command/Strategy/Observer (GoF).

## Depende de

- [03-matches-service.md](03-matches-service.md)

## Padrão FP

Para cada recurso com write path: `domain/*` + `logic/*` + protocol/db — **nunca** `service/*` ou `repository/*`.

Handlers read-mostly podem delegar a `logic/*` fino ou permanecer simples se só leitura directa `db/*` (documentar excepção no PR).

## Entregáveis

### Código (ordem sugerida)

1. `domain/players.clj` + `logic/players.clj` (+ protocol se necessário)
2. `domain/teams.clj` + `logic/teams.clj`
3. `domain/seasons.clj` + `logic/seasons.clj` (se ainda não coberto por championship store)
4. `handlers/aggregations` — leitura via `logic/analytics` ou `db/aggregations` + funções puras em `domain/analytics` (detalhe no Plano 05)

### Documentação

| Ficheiro | Acção |
|----------|--------|
| [mongodb-schema.md](../informacao/dominio/mongodb-schema.md) | Secção **Índices** + **Política de desnormalização** |
| [fp-improvement-checklist.md](../a-fazer/notebookLM/fp-improvement-checklist.md) | Marcar Fase D |
| [design-and-db-improvements.md](../a-fazer/notebookLM/design-and-db-improvements.md) | Histórico OO — não seguir para código novo |

### Não fazer

- `galaticos.composition` com `ns-resolve`
- Fase 8 OO (Command/Strategy/Observer)

## Tarefas

- [x] Migrar players (mínimo FP)
- [x] Migrar teams (mínimo FP)
- [x] Migrar seasons (mínimo FP)
- [x] Actualizar mongodb-schema
- [x] Verificar zero `service/*`/`repository/*` no repo inteiro
- [x] `./bin/galaticos test`

## Critério de saída do refactor (global)

- Championships + matches + rollout: write path via `logic/*`
- Documentação alinhada com [architecture.md](../informacao/analytics/architecture.md)
- `rg 'galaticos\.(service|repository)' src/ test/` → zero

## Não fazer

- Métricas derivadas completas (Plano 05)
- UI analytics (Plano 07)

## Próximo plano

[05-analytics-data-layer.md](05-analytics-data-layer.md)
