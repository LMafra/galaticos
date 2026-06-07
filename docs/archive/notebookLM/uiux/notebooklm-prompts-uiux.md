> **Archived** — historical NotebookLM material (may be Portuguese). For current docs see [docs/README.md](../../../README.md) and [reference/](../../../reference/).

# NotebookLM Prompts – UI/UX (Galáticos)

Use estes prompts no notebook **UI/UX** do NotebookLM. Cole **um prompt de cada vez** no chat.

**Stack UI:** shadow-cljs, Reagent, reitit-frontend (hash routing), Tailwind CSS, lucide-react, cljs-http, toasts locais.

**Utilizador:** administrador do clube (uso esporádico; alta carga cognitiva em dia de jogo).

**Domínio:** jogadores, times, campeonatos/temporadas, partidas com estatísticas por atleta, dashboard e agregações.

**Contexto técnico:** SPA em `src-cljs/galaticos/components/`; design system emergente em `common.cljs` e `layout.cljs`; fluxos complexos em `matches.cljs`, `merge_modal.cljs`, `player_picker.cljs`. Rotas: ver [page-inventory.md](../../../reference/performance/page-inventory.md).

**Restrições:** manter stack actual; melhorias incrementais; Lighthouse mobile (release) já medido — ver [action-backlog.md](../../../backlog/performance/action-backlog.md).

---

## Fluxo após respostas

1. Abrir o notebook **UI/UX** no NotebookLM
2. Copiar o prompt da secção N abaixo
3. Colar no chat; aguardar resposta
4. Colar a resposta na secção N de [notebooklm-response-uiux.md](notebooklm-response-uiux.md)
5. Repetir para secções 1–20
6. Preencher **Decisões consolidadas** e seguir **Como derivar planos** em [notebooklm-response-uiux.md](notebooklm-response-uiux.md)

**Ordem sugerida:** 1→4 (fundação) · 5→7 (auth e feedback) · 8→14 (fluxos de domínio; partidas e campeonatos antes de times) · 15→20 (sistema, a11y, mobile, roadmap).

**Síntese opcional** (após ~5 respostas, no chat do notebook): *Liste contradições entre suas recomendações e priorize 10 mudanças realizáveis em 2 semanas para esta SPA administrativa de gestão de elenco.*

---

## 1. Auditoria heurística (Nielsen + domínio esportivo)

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook de UI/UX, faça uma auditoria heurística da interface de uma SPA administrativa de gestão de elenco esportivo (não e-commerce, não SaaS genérico).

Contexto Galáticos:
- Utilizador: admin do clube; tarefas críticas em dia de jogo (registar partida, estatísticas por jogador, consultar elenco).
- Áreas: dashboard, estatísticas/agregações, jogadores (incl. merge de duplicados em 3 passos), partidas (formulário pesado com tabela de stats), campeonatos/temporadas (inscrição, finalização), times (só autenticado).
- Navegação: sidebar + header; URLs em hash (#/dashboard, #/players, etc.).
- Feedback: toasts, alerts inline, alguns window.confirm nativos.

Por favor recomende:
1. Avaliação por heurística de Nielsen (e outras do notebook), com severidade por achado
2. Heurísticas específicas para dados tabulares e formulários longos em contexto esportivo
3. Top 10 problemas de usabilidade prováveis e impacto no utilizador
4. Quick wins vs mudanças estruturais

Inclua critérios de aceite mensuráveis onde possível. Prefira admin web desktop+mobile.
```

---

## 2. Design system e tokens visuais

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, como estruturar um design system mínimo mas coerente para uma SPA Tailwind + componentes Reagent?

Contexto Galáticos:
- Cor de marca: brand-maroon; superfícies: slate + app-card; tema claro/escuro (toggle no header e login).
- Componentes partilhados: button (primary/secondary/danger/outline/ghost), alert, badge, card, stat-card, input-field, loading-spinner.
- Ícones: lucide-react na navegação.
- Badges de status podem não seguir o mesmo padrão dark que o resto da UI.

Por favor recomende:
1. Tokens (cor, espaçamento, raio, sombra, tipografia) — o que documentar primeiro
2. Matriz de variantes de botão e alert (quando usar cada uma)
3. Consistência dark mode (incl. badges e tabelas)
4. Nomeação e onde viver a documentação (sem Figma obrigatório)

Inclua exemplos de classes Tailwind ou padrões de composição. Critérios de aceite para “componente conforme design system”.
```

---

## 3. Tipografia, hierarquia e densidade de dados

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, como melhorar legibilidade e hierarquia visual em uma aplicação com muitas tabelas e métricas?

Contexto Galáticos:
- Títulos de página no header (ex.: "Jogadores", "Partidas"); h2 em formulários e detalhes.
- Tabelas: lista de partidas, estatísticas por jogador no formulário de partida, agregações, nested tables em campeonatos.
- Uso em campo: possível consulta em telemóvel após o jogo.

Por favor recomende:
1. Escala tipográfica (títulos, corpo, captions, números tabulares)
2. Densidade compacta vs confortável — quando alternar
3. Alinhamento e formatação de números (gols, minutos, percentagens)
4. Destaque visual para dados críticos (placar, status de campeonato, alertas de regra de negócio)

Inclua wireframe textual de uma tabela de estatísticas de partida. Critérios de aceite de legibilidade (contraste, tamanho mínimo).
```

---

## 4. Arquitetura de informação e navegação

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, avalie e melhore a arquitetura de informação de uma SPA com sidebar fixa e rotas hash.

Contexto Galáticos:
- Itens de nav: Dashboard, Estatísticas, Jogadores, Partidas, Campeonatos; Times só autenticado.
- Rotas técnicas em inglês (:player-new, :match-edit) vs labels em português na UI.
- :home e :dashboard ambos levam ao dashboard; visitante pode ver dados limitados sem login.
- Breadcrumbs ausentes; título da página só no header.

Por favor recomende:
1. Mapa mental ideal para o admin (agrupamento de tarefas)
2. Breadcrumbs ou alternativas (contexto em formulários profundos)
3. Consistência de nomenclatura (PT na UI, URLs)
4. Fluxos transversais (ex.: do campeonato → nova partida → voltar)

Inclua diagrama textual de IA (nós e ligações). Critérios de aceite: utilizador encontra X em ≤3 cliques.
```

---

## 5. Onboarding, login e sessão

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, como melhorar a experiência de autenticação e primeira utilização?

Contexto Galáticos:
- Login: cartão centrado, usuário/senha, JWT 24h; redirect para dashboard se já autenticado.
- Logout: client-side (descartar token); sem estado de sessão no servidor.
- Rotas protegidas redirecionam para dashboard com mensagem "Redirecionando..." se não autenticado.
- Tema claro/escuro disponível na página de login.

Por favor recomende:
1. Primeira visita e empty states pós-login
2. Mensagens de erro de login (segurança vs clareza)
3. Indicadores de sessão a expirar (opcional)
4. Diferença UX visitante vs autenticado no dashboard

Inclua jornada de login e critérios de aceite. Não proponha OAuth unless o notebook justificar para admin único.
```

---

## 6. Estados: loading, vazio, erro e 404

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, defina padrões de estados da interface (loading, empty, error, not found).

Contexto Galáticos:
- Layout global: spinner substitui todo o main quando :loading no app-state.
- Componente not-found-resource para 404 de jogador/campeonato/time.
- Toasts para erros de API; alerts inline em formulários.
- Listas podem ficar vazias após filtros (campeonatos em partidas, jogadores inscritos).

Por favor recomende:
1. Quando usar spinner global vs inline vs skeleton
2. Copy e ilustração para empty states por entidade
3. Hierarquia erro recuperável vs fatal
4. Página 404 de rota inexistente

Inclua tabela: estado × componente × exemplo de mensagem PT. Critérios de aceite por rota crítica (/matches/new, /players).
```

---

## 7. Feedback, confirmações e ações destrutivas

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, como padronizar feedback e confirmações para ações destrutivas ou irreversíveis?

Contexto Galáticos:
- Deletes: window.confirm nativo em partidas (e possivelmente outras entidades).
- Merge de jogadores: modal em 3 passos (referência → candidatos → campos).
- Finalizar campeonato/temporada: regras de negócio rígidas (vencedores, inscrições).
- Toasts para sucesso/erro após API.

Por favor recomende:
1. Modal de confirmação vs inline vs undo — quando usar cada um
2. Conteúdo do diálogo (o que mostrar antes de apagar/finalizar/merge)
3. Feedback pós-ação (toast vs navegação vs permanecer na página)
4. Acessibilidade em modais (foco, escape, aria)

Inclua exemplos de copy em português. Critérios de aceite para delete de partida e merge de jogadores.
```

---

## 8. Formulários e validação na interface

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, como desenhar formulários administrativos complexos com validação alinhada à API?

Contexto Galáticos:
- Entidades: jogador, time, campeonato, partida (a mais complexa: data, adversário, placar, stats por jogador inscrito).
- Erros da API em JSON (400/409/404); validação de negócio no servidor.
- Campos read-only quando contexto fixo (campeonato/time na criação de partida por rota).
- Avisos amber quando não há campeonato ativo ou jogadores inscritos.

Por favor recomende:
1. Layout de formulário (secções, fieldsets, ordem dos campos)
2. Validação inline vs no submit vs após blur
3. Apresentação de erros por campo vs resumo no topo
4. Estados do botão Salvar (disabled, loading, dirty)

Inclua wireframe textual do formulário de partida. Critérios de aceite: utilizador corrige erro sem perder dados já introduzidos.
```

---

## 9. Listas, busca, filtros e paginação

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, melhore padrões de listagem, busca e filtros.

Contexto Galáticos:
- Jogadores: busca com debounce no backend (/api/aggregations/players/search), filtros de posição, paginação 25 itens.
- Partidas: lista agrupada por campeonato com filtro de texto local.
- Campeonatos e times: listas com cards ou tabelas.

Por favor recomende:
1. Posição de busca e filtros (toolbar consistente)
2. Feedback durante busca (loading, sem resultados)
3. Paginação vs infinite scroll para admin
4. Persistência de filtros (URL query vs localStorage)

Inclua padrão de toolbar reutilizável. Critérios de aceite para lista de jogadores com >100 registos.
```

---

## 10. Fluxo: jogadores (CRUD, detalhe e merge)

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, redesenhe a experiência de gestão de jogadores incluindo merge de duplicados.

Contexto Galáticos:
- Rotas: lista, novo, detalhe, editar.
- Merge em 3 passos: escolher referência → checkboxes de candidatos (fuzzy) → escolha campo a campo (master/candidato/combinado).
- Player picker reutilizável: busca local, lista alfabética, criação rápida com posição "A definir".
- Detalhe: bundle API (foto, nome, stats).

Por favor recomende:
1. Simplificar ou manter wizard de merge — alternativas
2. Quando oferecer merge (da lista vs detalhe vs pós-criação)
3. Detalhe do jogador: hierarquia de informação (bio vs stats vs histórico)
4. Formulário create/edit: campos obrigatórios vs avançados

Inclua jornada passo a passo e critérios de aceite. Reduzir carga cognitiva no passo 3 do merge.
```

---

## 11. Fluxo: partidas (lista, detalhe e formulário)

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, redesenhe o fluxo de listar, ver e criar/editar partidas.

Contexto Galáticos:
- Lista: navegação por campeonato (cards), depois tabela de partidas.
- Formulário: campeonato, time, adversário, data, gols, resultado calculado, tabela editável (gols, assistências, cartões, minutos) por jogador inscrito.
- Débito conhecido: pickers podem carregar catálogo inteiro; falta skeleton no form (spinner global).
- Delete com confirm nativo.

Por favor recomende:
1. Wizard vs página única para criar partida
2. Ordem e agrupamento de campos; registo rápido pós-jogo
3. Tabela editável: teclado, defaults, linhas vazias
4. Loading parcial (skeleton por secção: cabeçalho vs stats)

Inclua jornada "registar partida em 5 minutos após o jogo". Critérios de aceite alinhados a mobile.
```

---

## 12. Fluxo: campeonatos e temporadas

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, melhore UX de campeonatos, temporadas (seasons), inscrição e finalização.

Contexto Galáticos:
- Rotas: lista, novo, detalhe, editar, detalhe de temporada (/championships/:id/seasons/:season-id).
- Regras: max-players, inscrição de jogadores, finalizar com vencedores inscritos, não apagar se houver partidas.
- UI: nested tables; loading com min-height reservado; tarefa aberta: lazy load por secção.

Por favor recomende:
1. Detalhe de campeonato: tabs vs scroll longo vs accordion
2. Inscrição em massa vs uma a uma — padrões de seleção
3. Finalizar: fluxo guiado (checklist antes de confirmar)
4. Estados visuais (ativo, concluído, indefinido) — badges e copy

Inclua wireframe do detalhe de campeonato. Critérios de aceite para inscrição até ao limite max-players.
```

---

## 13. Fluxo: times

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, alinhe a experiência de times com jogadores e partidas.

Contexto Galáticos:
- Menu Times só para utilizador autenticado.
- Rotas: lista, novo, detalhe, editar.
- Jogadores referenciam team-id; coerência de time na partida e inscrição.

Por favor recomende:
1. Informação mínima no card/lista de times
2. Relação time ↔ jogadores na UI (link, contagem, elenco)
3. Formulário de time vs complexidade dos outros módulos
4. Empty state quando não há times

Inclua critérios de aceite e consistência com módulo de jogadores.
```

---

## 14. Dashboard e estatísticas (gráficos)

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, melhore dashboard e página de estatísticas/agregações.

Contexto Galáticos:
- Dashboard: filtros, cards de métricas, blocos deferidos (requestAnimationFrame), links para entidades.
- /stats: abas, tabelas comparativas, Recharts, export CSV, dados já agregados no servidor onde possível.
- Métricas derivadas: contribuição de gols, disciplina, minutos por gol, etc.

Por favor recomende:
1. Hierarquia do dashboard (o que ver primeiro)
2. Gráficos: tipos adequados, legendas, cores acessíveis
3. Filtros globais vs por secção
4. Export e partilha (CSV) — feedback ao utilizador

Inclua esboço de layout desktop e mobile. Critérios de aceite: compreender desempenho do elenco em <30s.
```

---

## 15. Componentes reutilizáveis (UI kit interno)

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, que componentes devem compor um UI kit interno mínimo para esta SPA Reagent?

Contexto Galáticos:
- Existentes: common (button, alert, card, inputs), player-picker, merge-modal, toast, charts.
- Estado global em app-state + effects.cljs; sem re-frame (ver notebook FP para arquitectura CLJS — não repetir aqui).

Por favor recomende:
1. Lista priorizada de componentes a extrair/normalizar
2. API de props consistente (variant, disabled, class merge)
3. O que não componentizar (evitar over-abstraction)
4. Documentação mínima para quem mantém ClojureScript

Inclua 1 exemplo de assinatura de componente (texto). Critérios de aceite: novo ecrã reutiliza ≥3 primitivos do kit.
```

---

## 16. Acessibilidade (WCAG) e teclado

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, defina um plano de acessibilidade pragmático para admin web.

Contexto Galáticos:
- Alguns aria-label (menu, tema); tabelas editáveis no form de partida; navegação por links <a href>.
- Dark mode; contraste brand-maroon em fundos claros e escuros.
- Confirms nativos acessíveis mas modais custom podem falhar foco.

Por favor recomende:
1. Prioridade WCAG 2.x (nível alvo realista)
2. Tabelas, formulários e modais: teclado e leitores de ecrã
3. Checklist rápida por página crítica
4. Testes manuais vs automáticos viáveis

Inclua tabela página × requisito × estado (OK/gap). Não exija refactor completo de stack.
```

---

## 17. Mobile, responsivo e touch

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, melhore experiência mobile e touch para admin em campo.

Contexto Galáticos:
- Sidebar: drawer em mobile (overlay); desktop fixa.
- Tabelas largas com scroll horizontal implícito.
- Lighthouse mobile release: login LCP alto; listas de jogadores/partidas já razoáveis.

Por favor recomende:
1. Breakpoints e o que colapsar primeiro
2. Tamanho mínimo de alvos de toque em ações frequentes
3. Formulário de partida em telemóvel — layout alternativo?
4. Tabelas: cards em mobile vs scroll

Inclua critérios de aceite para uso com uma mão. Priorize rotas /matches e /players.
```

---

## 18. Performance percebida e microinterações

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, melhore performance percebida sem mudar stack.

Contexto Galáticos:
- Spinner global no layout vs skeletons pendentes em forms de partida.
- Dashboard: blocos deferidos; guarded-fetch evita duplicar API.
- Login: bundle e LCP ainda sensíveis; code splitting já aplicado.

Por favor recomende:
1. Skeleton patterns por tipo de conteúdo (card, tabela, form)
2. Microinterações úteis vs distração (hover, transições)
3. Optimistic UI — onde vale a pena neste domínio
4. Mensagens de "a guardar..." e jobs assíncronos (recalc stats)

Cruze com boas práticas do notebook; não duplique tarefas já fechadas em performance backlog salvo melhoria UX. Critérios de aceite por rota.
```

---

## 19. Microcopy, i18n e tom de voz

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook, defina tom de voz e padrões de microcopy em português.

Contexto Galáticos:
- UI maioritariamente PT; títulos de rotas Reitit em inglês; alguns status da API em inglês mapeados para PT (status-label).
- Erros: toasts com prefixo técnico ("Erro ao fazer login: ...").
- Domínio: campeonato, temporada, partida, adversário, inscrição, finalizar.

Por favor recomende:
1. Tom (formal vs informal) para admin de clube
2. Glossário consistente PT (evitar misturar season vs temporada na UI)
3. Templates de erro, sucesso, confirmação, empty
4. Se e quando unificar idioma das rotas/document.title

Inclua 10 exemplos antes/depois de mensagens. Critérios de aceite de clareza para erros 400/409.
```

---

## 20. Priorização e roadmap UX

**Colar no NotebookLM:**

```
Com base nas fontes deste notebook e nas recomendações que já deu nesta conversa (se houver), construa um roadmap UX incremental.

Contexto Galáticos:
- Stack fixa: Reagent + Tailwind; melhorias incrementais.
- Débito documentado: skeletons em match form, pickers pesados, nested tables em campeonatos (ver performance action backlog).
- Módulos por impacto de negócio: partidas > campeonatos > jogadores > dashboard/stats > times.

Por favor recomende:
1. Matriz impacto × esforço (10–15 itens)
2. Ordem de 3–4 ondas de trabalho (2 semanas cada)
3. O que não fazer agora (anti-patterns de redesign)
4. Métricas de sucesso UX (qualitativas ou proxy)

Inclua dependências entre itens. Formato pronto para virar planos de implementação no repositório.
```

---

## Prompts de follow-up (opcional)

Use após preencher secções relevantes:

**Wireframe por rota:** *Para a rota `#/matches/by-championship/:id/new`, descreva wireframe textual mobile e desktop com todos os estados (loading, erro, vazio, sucesso).*

**Critérios de aceite:** *Liste critérios de aceite Given/When/Then para o fluxo de merge de jogadores em 3 passos.*

**Contradições:** *Compare suas recomendações das secções 1–20 e liste contradições; proponha resolução alinhada a admin esportivo.*

---

## Referências do projecto

- Respostas: [notebooklm-response-uiux.md](notebooklm-response-uiux.md)
- Inventário de rotas: [page-inventory.md](../../../reference/performance/page-inventory.md)
- Regras de negócio (copy e validação): [business-rules.md](../../../reference/domain/business-rules.md)
- Débito UX/performance: [action-backlog.md](../../../backlog/performance/action-backlog.md)
- Componentes CLJS: `src-cljs/galaticos/components/`
- Notebooks anteriores: [notebooklm-prompts.md](../oo/notebooklm-prompts.md), [notebooklm-prompts-fp.md](../fp/notebooklm-prompts-fp.md)
