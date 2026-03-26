# Operating Model de Analytics

## Objetivo

Definir como o time opera, evolui e governa métricas de sports analytics no Galáticos.

## Papéis

### Product owner de analytics

- Prioriza casos de uso analíticos.
- Aprova mudanças de métrica e critérios de sucesso.

### Engenharia

- Implementa pipelines, validações e contratos de dados.
- Mantém performance e confiabilidade de endpoints analíticos.

### Analista de dados (ou função equivalente)

- Cuida de consistência semântica do catálogo de métricas.
- Conduz análises e revisões de qualidade de dados.

## Cadência operacional

### Semanal

- Review de qualidade de dados e inconsistências.
- Acompanhamento dos KPIs principais e anomalias.

### Quinzenal

- Priorização de melhorias no roadmap analítico.
- Revisão de riscos técnicos e documentação.

### Mensal

- Revisão de evolução de métricas e aderência ao catálogo.
- Atualização de status da trilha `docs/analytics`.

## Governança de mudanças

1. Proposta de mudança (nova métrica/ajuste de fórmula/novo contrato).
2. Avaliação de impacto em API, frontend e histórico.
3. Aprovação por product + engenharia.
4. Implementação com documentação e testes.
5. Comunicação para usuários internos.

## Artefatos obrigatórios por mudança

- Atualização em `metrics-catalog.md` quando houver impacto semântico.
- Atualização em `data-contracts.md` para mudança de schema.
- Evidência de validação em `testing-coverage.md` (testes e reconciliação).

## Princípios de decisão

- Clareza supera complexidade.
- Reprodutibilidade dos resultados é mandatória.
- Métrica sem definição formal não vai para produção.
