# Evolução Técnica Incremental de Analytics

## Objetivo

Planejar o desacoplamento da recomputação analítica do fluxo transacional de CRUD para melhorar latência, resiliência e capacidade de reprocessamento.

## Situação atual

- Recomputação é acionada no ciclo de criação/edição/remoção de partidas.
- Reprocessamento completo compete com tráfego transacional.
- Observabilidade de operações analíticas ainda limitada.

## Etapa 1: separar comandos transacionais de processamento analítico

- Manter persistência de partida síncrona.
- Emitir evento interno de recálculo por partida.
- Tratar falhas do recálculo sem quebrar operação transacional.

## Etapa 2: reprocessamento incremental e completo

- **Incremental**: recálculo apenas de jogadores e campeonatos impactados.
- **Completo**: recálculo total sob demanda operacional.
- Garantir idempotência para evitar dupla contagem.

## Etapa 3: observabilidade mínima obrigatória

- Logs estruturados para início/fim de recálculo.
- Métricas operacionais:
  - tempo de processamento por job
  - quantidade de entidades recalculadas
  - taxa de falha
- Alertas para jobs que excederem tempo limite.

## Critérios de aceite

- Redução de latência em endpoints de partidas sob carga.
- Possibilidade de reprocessar sem indisponibilidade de API.
- Visibilidade operacional suficiente para troubleshooting rápido.

## Dependências

- Contratos de dados estáveis (`docs/informacao/analytics/data-contracts.md`).
- Runbook de reconciliação (`docs/informacao/analytics/reconciliation-runbook.md`).
- Estratégia de testes de regressão (`docs/informacao/dominio/testing-coverage.md`).
