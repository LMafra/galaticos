# Plano 00 — Pré-requisito: testes verdes

## Estado

**Gate permanente** — reutilizar antes de **cada** plano 02–07 e sempre que uma sessão anterior deixar código a meio.

## Objetivo

Garantir que o repositório **compila** e `./bin/galaticos test` passa antes de iniciar ou continuar qualquer plano de migração FP.

## Âmbito

- Corrigir ou reverter ficheiros que impedem carga de namespaces
- **Sem** novas features nem refactor de arquitectura (excepto o plano activo)

## Tarefas

1. `git status` — listar alterações pendentes
2. `./bin/galaticos test` — capturar primeiro erro
3. Se erro em código a meio (`logic/*`, `domain/*`, handlers incompletos, ou legado OO):
   - **Opção A:** reverter para último commit verde (`git checkout -- <paths>`)
   - **Opção B:** corrigir parênteses/namespaces até compilar
4. Repetir testes até 0 falhas

## Critérios de saída

```bash
./bin/galaticos test
```

## Revisão FP

- Válido para refactor funcional e analytics — mesma rede de segurança que no Plano 01
- Ver também [testing-coverage.md](../informacao/dominio/testing-coverage.md)

## Não fazer

- Adicionar derived metrics, UI, ou novos endpoints fora do plano activo
- Avançar para Plano 02+ com testes vermelhos

## Próximo plano

[01-foundation-errors-tests.md](01-foundation-errors-tests.md) (primeira vez) ou o plano FP activo (02–07)
