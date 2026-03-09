## Checklist de Regras de Negócio – Pendências (Parcial / Não implementado)

**Gerado em**: 23 de Fevereiro de 2026  
**Fonte**: `docs/regras-de-negocio.md` + checklist completo (apenas itens com trabalho pendente).

Este documento contém **somente** as regras e questões em status **Parcial** ou **Não implementado**, para facilitar a execução das tarefas pendentes. O checklist completo com todas as regras (incluindo as já implementadas) pode ser consultado no histórico do repositório ou reconstruído a partir de `docs/regras-de-negocio.md`.

Status dos itens abaixo:
- **Parcial**: regra coberta em parte ou com lacunas importantes.
- **Não implementado**: não há implementação clara da regra.

---

## 1. Regras BRM pendentes

- **BRM-05 – Temporada Ativa por Campeonato**
  - **Status**: Parcial
  - **Implementação principal**: `src/galaticos/handlers/championships.clj`, `src/galaticos/db/championships.clj`, `scripts/python/seed_mongodb.py`, `src-cljs/galaticos/components/championships.cljs`
  - **Notas**: Hoje cada documento de campeonato representa uma combinação `name+season` com um único `status` (`active/completed/...`). Não há entidade explícita de temporada nem restrição forte de "no máximo uma temporada ativa por campeonato" além de convenção de uso.
  - **Tarefas**:
    - Definir modelo de temporadas (entidade própria ou um-doc-por-temporada) como regra oficial.
    - Considerar criação de índice/constraint de unicidade por `(name, season)` na coleção `championships`.
    - Documentar claramente como representar temporadas passadas.

- **BRM-06 – Gestão de Temporadas (adicionar e selecionar temporada ativa)**
  - **Status**: Parcial
  - **Implementação principal**: `src-cljs/galaticos/components/championships.cljs`, `src/galaticos/handlers/championships.clj`
  - **Notas**: É possível criar/editar campeonatos com diferentes `season` e `status`, mas não há uma UI específica de "gestão de temporadas" ligada a um mesmo campeonato raiz.
  - **Tarefas**:
    - Definir se haverá uma tela de "campeonato" com múltiplas temporadas associadas.
    - Implementar UI de seleção de temporada ativa por campeonato (caso se adote esse modelo).

- **BRM-09 – Participação e Pertencimento ao Time**
  - **Status**: Parcial
  - **Implementação principal**: `src/galaticos/handlers/matches.clj`, `src/galaticos/db/matches.clj`, `src/galaticos/db/players.clj`, `src/galaticos/db/teams.clj`
  - **Notas**:
    - Validação garante que jogadores com estatísticas estejam inscritos no campeonato (`validate-players-enrolled`).
    - `team-id` é obrigatório nas estatísticas de jogador.
    - Não há validação explícita de que `team-id` pertence ao time onde o jogador está como ativo (apenas convenção).
  - **Tarefas**:
    - Avaliar se é necessário validar, no backend, que `team-id` nas estatísticas coincide com o time do jogador (`players.team-id`) e/ou com a lista `active-player-ids` do time.

- **BRM-10 – Visualização de Jogadores e Estatísticas da Partida**
  - **Status**: Parcial
  - **Implementação principal**: `src-cljs/galaticos/components/matches.cljs`, `src/galaticos/handlers/matches.clj`
  - **Notas**:
    - Ao editar uma partida, o formulário exibe a lista de jogadores inscritos com suas estatísticas (gols, assistências, minutos).
    - Não existe uma tela de "detalhe de partida" somente leitura; a visualização acontece via tela de edição.
  - **Tarefas**:
    - (Opcional) Criar uma rota de detalhamento de partida somente leitura que reutilize o mesmo layout do formulário, mas sem edição.

- **BRM-12 – Navegação por Itens do Dashboard**
  - **Status**: Não implementado
  - **Implementação principal**: `src-cljs/galaticos/components/dashboard.cljs`, `src-cljs/galaticos/routes.cljs`
  - **Notas**:
    - Dashboard exibe métricas e tabelas, mas os cards não são links de navegação para listas de jogadores/times/campeonatos/partidas.
    - As rotas para essas telas existem em `routes.cljs`, mas não há "atalhos clicáveis" no dashboard.
  - **Tarefas**:
    - Adicionar interações nos `stat-card` e/ou em novas seções do dashboard que naveguem para:
      - Lista de jogadores (`:players`)
      - Lista de times (`:teams`)
      - Lista de campeonatos (`:championships`)
      - Lista de partidas (`:matches`)
      - Tela de estatísticas avançadas (`:stats`), se aplicável.

- **BRM-13 – Busca de Jogadores ao Inscrever em Campeonato**
  - **Status**: Parcial
  - **Implementação principal**: `src-cljs/galaticos/components/championships.cljs`, `src/galaticos/db/players.clj`
  - **Notas**:
    - Ao inscrever jogadores em um campeonato, há um `select-field` preenchido com todos os jogadores carregados.
    - Não há busca/autocomplete por nome; a seleção é por lista.
  - **Tarefas**:
    - Implementar busca/autocomplete de jogadores por nome na tela de inscrições do campeonato, utilizando `find-by-name` ou endpoint específico de busca.

- **BRM-14 – Busca de Jogadores no Dashboard**
  - **Status**: Parcial
  - **Implementação principal**: `src-cljs/galaticos/components/players.cljs`, `src/galaticos/db/players.clj`, `src/galaticos/db/aggregations.clj`
  - **Notas**:
    - Há busca por nome e filtro por posição na lista de jogadores, mas não diretamente a partir do dashboard.
    - Backend oferece `search-players` com filtros avançados, mas o dashboard não expõe essa funcionalidade como busca global.
  - **Tarefas**:
    - Adicionar no dashboard um campo de busca que navegue ou chame uma visualização de resultados (Players/Stats) usando os filtros avançados.

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
  - **Status**: Não implementado
  - **Implementação principal**: (não encontrada)
  - **Notas**: Não há lógica para inferir posição por presença de `"GK"` no nome em `seed_mongodb.py` nem em outros scripts.
  - **Tarefas**:
    - Adicionar no fluxo de criação de jogadores (seed) uma função que:
      - Se o nome contiver `"GK"` (case-insensitive) e a posição não estiver definida, defina a posição como `"Goleiro"`.
      - Respeite precedência de posição explícita (ver Q-05).

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
    - Nenhuma pendência técnica; regra documentada em `docs/regras-de-negocio.md` (`RN-STATS-07`).

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
    - Nenhuma pendência; regra alinhada com BRM-16 e documentada em `docs/regras-de-negocio.md`.

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
    - Opcional: evoluir o design/UX para incluir outros atalhos (ex.: tela `:stats`) e manter o mapeamento documentado em `docs/regras-de-negocio.md`.

---

## 3. Resumo do checklist de pendências

| ID       | Status              | Área principal                    |
|----------|---------------------|-----------------------------------|
| BRM-05   | Parcial             | Temporadas / campeonatos          |
| BRM-06   | Parcial             | UI gestão de temporadas           |
| BRM-09   | Parcial             | Validação time/jogador em partidas|
| BRM-10   | Parcial             | Detalhe de partida (somente leitura) |
| BRM-12   | Parcialmente coberto| Navegação dashboard → listas     |
| BRM-13   | Parcial             | Busca/autocomplete na inscrição   |
| BRM-14   | Parcial             | Busca de jogadores no dashboard  |
| BRM-15   | Parcial             | Seed: enrolled-player-ids        |
| BRM-16   | Parcial             | Derivação GK no seed              |
| Q-03     | Concluído           | Busca acento/paginação            |
| Q-04     | Concluído           | Conflitos na importação           |
| Q-05     | Concluído           | Precedência posição GK            |
| Q-06     | Concluído           | Navegação dashboard (card → rota) |

**Nota:** As questões Q-01 (Modelo de Temporadas) e Q-02 (Reabertura de Campeonato) são apenas decisões de negócio, sem tarefas de implementação definidas até a decisão; por isso não constam neste checklist de execução.
