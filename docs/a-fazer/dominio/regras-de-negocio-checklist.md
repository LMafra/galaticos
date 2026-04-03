## Checklist de Regras de Negócio – Pendências (Parcial / Não implementado)

**Gerado em**: 23 de Fevereiro de 2026  
**Fonte**: `docs/informacao/dominio/regras-de-negocio.md` + checklist completo (apenas itens com trabalho pendente).

Este documento contém **somente** as regras e questões em status **Parcial** ou **Não implementado**, para facilitar a execução das tarefas pendentes. O checklist completo com todas as regras (incluindo as já implementadas) pode ser consultado no histórico do repositório ou reconstruído a partir de `docs/informacao/dominio/regras-de-negocio.md`.

Status dos itens abaixo:
- **Parcial**: regra coberta em parte ou com lacunas importantes.
- **Não implementado**: não há implementação clara da regra.

---

## 1. Regras BRM pendentes

- **BRM-05 – Temporada Ativa por Campeonato**
  - **Status**: Concluído
  - **Implementação principal**: `src/galaticos/handlers/championships.clj`, `src/galaticos/db/championships.clj`, `scripts/python/seed_mongodb.py`, `src-cljs/galaticos/components/championships.cljs`
  - **Notas**: Foi introduzida a entidade explícita `seasons` com constraint de unicidade `(championship-id, season)` e regra de **no máximo uma temporada ativa** por campeonato (via `seasons-db/activate!`).
  - **Tarefas**:
    - Concluído: modelo de temporadas definido e implementado (coleção `seasons`).
    - Concluído: unicidade `(championship-id, season)` aplicada (app-level + documentação de schema).
    - Concluído: temporadas passadas representadas como `seasons` com `status` `"completed"`/`"inactive"`.

- **BRM-06 – Gestão de Temporadas (adicionar e selecionar temporada ativa)**
  - **Status**: Concluído
  - **Implementação principal**: `src-cljs/galaticos/components/championships.cljs`, `src/galaticos/handlers/championships.clj`
  - **Notas**: A tela de detalhe do campeonato passou a suportar **múltiplas temporadas** com criação e gestão, incluindo seleção/ativação da temporada ativa.
  - **Tarefas**:
    - Concluído: UI de seleção de temporada ativa por campeonato.
    - Concluído: listagem de temporadas (ano/status/inscritos/títulos) com ação de ativação.

- **BRM-09 – Participação e Pertencimento ao Time**
  - **Status**: Concluído
  - **Implementação principal**: `src/galaticos/handlers/matches.clj`, `src/galaticos/db/matches.clj`, `src/galaticos/db/players.clj`, `src/galaticos/db/teams.clj`
  - **Notas**:
    - Validação garante que jogadores com estatísticas estejam inscritos no campeonato (`validate-players-enrolled`).
    - `team-id` é obrigatório nas estatísticas de jogador.
    - Foi adicionada validação no backend garantindo coerência entre `player-statistics.team-id`, `players.team-id` e `teams.active-player-ids`.
  - **Tarefas**:
    - Concluído: validação de coerência player/time ao salvar estatísticas de partida.

- **BRM-10 – Visualização de Jogadores e Estatísticas da Partida**
  - **Status**: Concluído
  - **Implementação principal**: `src-cljs/galaticos/components/matches.cljs`, `src/galaticos/handlers/matches.clj`
  - **Notas**:
    - Ao editar uma partida, o formulário exibe a lista de jogadores inscritos com suas estatísticas (gols, assistências, minutos).
    - Foi criada a rota/tela de detalhe de partida **somente leitura** (`:match-detail`).
  - **Tarefas**:
    - Concluído: rota `:match-detail` (`/matches/:id`) e componente read-only.

- **BRM-12 – Navegação por Itens do Dashboard**
  - **Status**: Concluído
  - **Implementação principal**: `src-cljs/galaticos/components/dashboard.cljs`, `src-cljs/galaticos/routes.cljs`, `src/galaticos/handlers/aggregations.clj`
  - **Notas**:
    - Os cards do dashboard são botões clicáveis e navegam para:
      - `:players`, `:matches`, `:championships` e `:teams`
    - Os cards “Top 5” passam a ter nomes clicáveis, navegando para `:player-detail`.
    - O endpoint `dashboard-stats` expõe `teams-count` para suportar o card de Times.
  - **Tarefas**:
    - Nenhuma pendência técnica.

- **BRM-13 – Busca de Jogadores ao Inscrever em Campeonato**
  - **Status**: Concluído
  - **Implementação principal**: `src-cljs/galaticos/components/championships.cljs`, `src/galaticos/db/players.clj`
  - **Notas**:
    - A inscrição passou a oferecer **busca/autocomplete** por nome (com painel reutilizável) e criação rápida quando não há resultados.
  - **Tarefas**:
    - Concluído: busca/autocomplete na inscrição do campeonato.

- **BRM-14 – Busca de Jogadores no Dashboard**
  - **Status**: Concluído
  - **Implementação principal**: `src-cljs/galaticos/components/players.cljs`, `src/galaticos/db/players.clj`, `src/galaticos/db/aggregations.clj`
  - **Notas**:
    - Há busca por nome e filtro por posição na lista de jogadores, mas não diretamente a partir do dashboard.
    - Backend oferece `search-players` com filtros avançados, mas o dashboard não expõe essa funcionalidade como busca global.
  - **Tarefas**:
    - Concluído: campo de busca no dashboard que navega para `:players` com `q` aplicado.

- **BRM-15 – Inscrição Automática de Jogadores na Importação de Campeonatos**
  - **Status**: Concluído
  - **Implementação principal**: `scripts/python/seed_mongodb.py`, `scripts/python/update_titles_count.py`
  - **Notas**:
    - O seed processa planilhas/CSVs de campeonatos, cria/atualiza documentos de `championships` e atualiza `aggregated-stats` dos jogadores.
    - Os jogadores listados nas zonas de atletas dos arquivos são automaticamente inscritos no campeonato por meio do campo `enrolled-player-ids`.
    - A importação de partidas via CSV cria documentos em `matches` com `player-statistics`, e um passo de rebuild atualiza `players.aggregated-stats` a partir das partidas.
  - **Tarefas**:
    - Nenhuma pendência técnica; regra considerada atendida pelo fluxo atual de seed/importação.

- **BRM-16 – Derivação de Posição Goleiro por "GK" no Nome**
  - **Status**: Concluído (decisão + implementação no seed)
  - **Implementação principal**: `scripts/python/seed_mongodb.py`
  - **Notas**:
    - Foi adicionada a função `infer_position_from_name` que aplica a regra:
      - Se a posição explícita estiver presente e não vazia, ela prevalece.
      - Se a posição estiver ausente/vazia e o nome contiver `"GK"` (case-insensitive), a posição é inferida como `"Goleiro"`.
    - A função é usada no fluxo de criação/atualização de jogadores (`create_players` e `ensure_players_from_base_dados`).
  - **Tarefas**:
    - Nenhuma pendência técnica; regra atendida pelo seed atual.

---

## 2. Questões abertas com tarefas de implementação (Parcial / Não implementado)

- **Q-03 – Critérios de Busca de Jogadores (case/acento/paginação)**
  - **Status**: Concluído
  - **Implementação atual**:
    - Backend:
      - `db.players/find-by-name` passou a usar o campo normalizado `:search-name` (sem acentos, minúsculo) para buscas parciais, garantindo comportamento case/acento-insensitive.
      - `db.aggregations/search-players` aceita `q`, `page`, `limit` e demais filtros, aplicando paginação com defaults (`page=1`, `limit=25`) e ordenação configurável (`sort-by`/`sort-order`).
    - Frontend:
      - Lista de jogadores (`players.cljs`) deixou de filtrar apenas em memória e agora chama `api/search-players` com `q`, `position`, `page` e `limit`, respeitando os critérios de busca/paginação padronizados.
  - **Tarefas**:
    - Nenhuma pendência técnica; regra documentada em `docs/informacao/dominio/regras-de-negocio.md` (`RN-STATS-07`).

- **Q-04 – Conflitos na Importação por Planilhas**
  - **Status**: Concluído (política definida e aplicada no seed)
  - **Implementação atual**:
    - `seed_mongodb.py`:
      - `create_players` continua usando o nome do jogador como chave de identificação, mas ao encontrar um jogador existente aplica a política **“planilha mais recente prevalece”** para estatísticas agregadas totais (`aggregated-stats.total`), preservando `by-championship` e campos estruturais (ID, time, etc.).
      - Atualizações de jogadores existentes são registradas via mensagens de log informando que o jogador foi atualizado a partir da planilha corrente.
      - `create_championship` mantém a prevenção de duplicidade `name+season` na coleção de campeonatos.
  - **Tarefas**:
    - Opcional: evoluir logs simples para um relatório estruturado (arquivo dedicado) se necessário para auditoria mais detalhada.

- **Q-05 – Derivação de "GK" quando Posição já Existe**
  - **Status**: Concluído (decisão + implementação no seed)
  - **Implementação atual**:
    - Foi adicionada em `seed_mongodb.py` a função `infer_position_from_name`, que aplica a regra:
      - Se a planilha trouxer uma posição explícita não vazia, essa posição **sempre prevalece**, mesmo que o nome contenha `"GK"`.
      - Se a posição estiver ausente/vazia e o nome contiver `"GK"` (case-insensitive), a posição é inferida como `"Goleiro"`.
    - `create_players` passa a usar essa função tanto para novos jogadores quanto para atualizações de existentes, respeitando a precedência definida.
  - **Tarefas**:
    - Nenhuma pendência; regra alinhada com BRM-16 e documentada em `docs/informacao/dominio/regras-de-negocio.md`.

- **Q-06 – Navegação do Dashboard para Novas Funcionalidades**
  - **Status**: Concluído (primeira versão do mapeamento card → rota)
  - **Implementação atual**:
    - Rotas para listas (jogadores, times, campeonatos, partidas, estatísticas) continuam existindo em `routes.cljs`.
    - O dashboard (`dashboard.cljs`) agora envolve os quatro `stat-card` principais em botões clicáveis que navegam para:
      - Card **“Jogadores”** → rota `:players`.
      - Card **“Partidas”** → rota `:matches`.
      - Card **“Gols”** → rota `:players` (lista de jogadores com aba de gols/destaques).
      - Card **“Campeonatos”** → rota `:championships`.
  - **Tarefas**:
    - Opcional: evoluir o design/UX para incluir outros atalhos (ex.: tela `:stats`) e manter o mapeamento documentado em `docs/informacao/dominio/regras-de-negocio.md`.

---

## 3. Resumo do checklist de pendências

| ID       | Status              | Área principal                    |
|----------|---------------------|-----------------------------------|
| BRM-05   | Concluído           | Temporadas / campeonatos          |
| BRM-06   | Concluído           | UI gestão de temporadas           |
| BRM-09   | Concluído           | Validação time/jogador em partidas|
| BRM-10   | Concluído           | Detalhe de partida (somente leitura) |
| BRM-12   | Concluído           | Navegação dashboard → listas     |
| BRM-13   | Concluído           | Busca/autocomplete na inscrição   |
| BRM-14   | Concluído           | Busca de jogadores no dashboard  |
| BRM-15   | Concluído           | Seed: enrolled-player-ids        |
| BRM-16   | Concluído           | Derivação GK no seed              |
| Q-03     | Concluído           | Busca acento/paginação            |
| Q-04     | Concluído           | Conflitos na importação           |
| Q-05     | Concluído           | Precedência posição GK            |
| Q-06     | Concluído           | Navegação dashboard (card → rota) |

**Nota:** As questões Q-01 (Modelo de Temporadas) e Q-02 (Reabertura de Campeonato) são apenas decisões de negócio, sem tarefas de implementação definidas até a decisão; por isso não constam neste checklist de execução.
