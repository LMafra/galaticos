# Backlog de ações de performance

Objetivo: registrar **baseline** por rota (ou grupo lógico), **oportunidades** apontadas pelo Lighthouse e **tarefas** concretas. Preencher as células `_/_` após cada rodada de auditoria (ver [metodologia.md](metodologia.md)).

**Legenda de baseline:** score = Performance (0–100), LCP/TBT/CLS conforme Lighthouse. Incluir sempre **data**, **build** (`dev` / `release`) e **perfil** (`mobile` / `desktop`).

---

## Login e shell

### `/` — Login (`:login`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _Colar 2–3 itens principais do relatório._ |

**Tarefas**

- [ ] Confirmar que o JS crítico da página de login não puxa bundles pesados de outras rotas (code splitting / entrada mínima).
- [ ] Evitar work pesado no primeiro paint (validações síncronas grandes no mount).
- [ ] Garantir assets estáticos com cache adequado em produção.

---

## Dashboard e analytics

### `/dashboard` — Dashboard (`:dashboard`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Auditar número de pedidos à API no primeiro render; eliminar duplicados (dedup / coalescing).
- [ ] Adiar gráficos ou tabelas abaixo da dobra se possível (render progressivo).
- [ ] Memoizar sub-árvores Reagent que dependem de grandes estruturas imutáveis.

### `/stats` — Estatísticas / agregações (`:stats`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Identificar funções de agregação ou formatação no cliente que possam mover-se para o servidor ou cache.
- [ ] Virtualização ou paginação se listas ou gráficos forem grandes.
- [ ] Revisar bibliotecas de visualização (peso do bundle e tempo de parse).

---

## Jogadores

### Grupo: listagem (`:players`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Lista longa: considerar janela virtual ou “load more”.
- [ ] Evitar re-render global ao filtrar; isolar estado de filtro.

### Grupo: formulário novo/editar (`:player-new`, `:player-edit`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Carregar opções de selects (equipas, etc.) de forma lazy ou em batch.
- [ ] Não bloquear input com validações síncronas pesadas.

### `:player-detail`

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Combinar endpoints se houver múltiplos round-trips para a mesma vista.
- [ ] LCP: garantir que o elemento principal (nome / foto / card) apareça cedo.

---

## Partidas

### `/matches` — Lista (`:matches`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Paginação ou limite default alinhado ao uso real.
- [ ] Revisar ordenação/filtro no cliente vs servidor.

### Grupo: formulários (`:match-new`, `:match-new-in-championship`, `:match-edit`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Picker de jogadores/campeonatos: evitar carregar catálogo completo de uma vez se for grande.
- [ ] Usar loading skeleton em vez de bloquear o formulário inteiro.

---

## Campeonatos

### Lista (`:championships`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Mesmas práticas de listagem longa que em jogadores/partidas.

### Detalhe (`:championship-detail`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Tabelas aninhadas: lazy load por secção ou aba.
- [ ] Evitar CLS reservando altura para blocos assíncronos.

### Formulários (`:championship-new`, `:championship-edit`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Validar apenas campos visíveis no primeiro passo (se wizard) ou debounce.

---

## Equipas

### Lista (`:teams`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Listas curtas costumam ser simples; focar em TBT se houver muitos re-renders.

### Detalhe (`:team-detail`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Listas de jogadores associados: paginação ou virtualização se crescer.

### Formulários (`:team-new`, `:team-edit`)

| Campo | Valor |
|-------|--------|
| **Baseline** | Data: ___ · Build: ___ · Perfil: ___ · Score: ___ · LCP: ___ · TBT: ___ · CLS: ___ |
| **Oportunidades (Lighthouse)** | _…_ |

**Tarefas**

- [ ] Alinhar com padrões dos outros formulários (lazy loads, validação).

---

## Tarefas transversais (todo o SPA)

- [ ] Medir com **build `release`** e registar diferença face ao `watch` (documentar no grupo mais crítico).
- [ ] Verificar tamanho do bundle principal (`shadow-cljs` bundle report) e oportunidades de split por rota.
- [ ] Garantir compressão (gzip/brotli) e headers de cache no servidor estático em produção.
- [ ] Re-auditar após upgrades de ClojureScript / shadow-cljs / dependências JS.
