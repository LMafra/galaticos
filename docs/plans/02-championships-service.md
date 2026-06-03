# Plano 02 — Championships: domain + logic + protocol (FP)

> **Spec FP.** O repo contém ainda implementação OO (`service/championships`, `repository/championships`). Este plano descreve o alvo funcional; a execução remove o legado OO.

## Estado

**Concluído** — vertical slice FP em produção; OO championships removido.

## Objetivo

Vertical slice de campeonatos com **Cálculos** em `domain/*`, **Ações** em `logic/*`, persistência via `db.protocol/*` — sem `service/*` nem `repository/*`.

## Depende de

- [01-foundation-errors-tests.md](01-foundation-errors-tests.md)

## Padrão técnico FP

- `defprotocol ChampionshipStore` + implementação Monger
- Testes de `logic/*` com `reify` — **proibido** `repo-call`, `ns-resolve`, `with-redefs` em vars de produção
- Domain retorna `{:ok}` / `{:error}`; logic converte para `ex-info` quando necessário
- Handlers: validação → logic → resposta; erros via middleware (sem `try/catch` por handler)

Ver [arquitetura-funcional.md](../informacao/arquitetura/arquitetura-funcional.md).

## Entregáveis

### Criar

| Namespace | Responsabilidade |
|-----------|------------------|
| `db.protocol/championship-store.clj` | Protocolo persistência (championship + seasons) |
| `domain/championships.clj` | `can-delete?`, `finalization-decision`, `enrollment-decision`, enrich puro |
| `logic/championships.clj` | `list`, `get`, `create!`, `update!`, `delete!`, `enroll!`, `unenroll!`, `finalize!`, `championship-players` |
| `test/galaticos/domain/championships_test.clj` | Regras BRM — só mapas |
| `test/galaticos/logic/championships_test.clj` | Orquestração com `reify` |

### Alterar

- `handlers/championships.clj` — chama `logic/*`; sem `service/*`; sem `try/catch` repetido

### Remover (após migração)

| OO actual | Motivo |
|-----------|--------|
| `service/championships.clj` | Substituído por domain + logic |
| `repository/championships.clj` | Substituído por protocol + db |
| `test/galaticos/service/championships_test.clj` | Substituído por domain/logic tests |
| `handlers/util.clj` | Se middleware cobrir mapeamento de erros |

## Regras de negócio (preservar)

- Não apagar campeonato com partidas (`409`)
- Finalizar só activo / temporada activa; vencedores inscritos
- Limite `max-players` na inscrição
- Enrich com temporada activa / fallback

Ver [regras-de-negocio.md](../informacao/dominio/regras-de-negocio.md).

## Tarefas

- [x] Protocol `ChampionshipStore` + wiring (handler/core)
- [x] `domain/championships.clj`
- [x] `logic/championships.clj`
- [x] Handlers + middleware (sem util redundante)
- [x] Testes domain + logic
- [x] Apagar namespaces OO listados acima
- [x] `./bin/galaticos test` + contract tests verdes

## Critérios de saída

- `rg 'galaticos\.(service|repository)' src/galaticos/handlers/championships.clj src/galaticos/service src/galaticos/repository` → sem championships OO
- BRM e contract tests intactos
- Nenhum `repo-call` / `ns-resolve`

## Não fazer

- Matches, analytics (planos 03+)
- Criar novos ficheiros em `service/` ou `repository/`

## Próximo plano

[03-matches-service.md](03-matches-service.md)
