# MongoDB Schema Design - Gestão de Elenco Esportivo

## 1. Estrutura de Coleções

### 1.1 Coleções Principais

**`championships`** - Campeonatos (entidade raiz)
- Embedding: Nenhum
- Relacionamento: Referencia `seasons` via `season-ids`

**`seasons`** - Temporadas
- Embedding: Nenhum
- Relacionamento: Referencia `championships` (championship-id), `matches` (match-ids), `players` (enrolled-player-ids, winner-player-ids)

**`players`** - Jogadores
- Embedding: Estatísticas agregadas por temporada (cache de leitura)
- Relacionamento: Referenciado por `seasons.enrolled-player-ids` e `matches.player-statistics`

**`matches`** - Partidas/Jogos
- Embedding: Estatísticas de jogadores na partida (array)
- Relacionamento: Referencia `season-id`; `player-id` nas estatísticas embarcadas

**`teams`** - Time (único)
- Embedding: Lista de jogadores ativos (referências)
- Relacionamento: Referenciado por `players.team-id`

**`admins`** - Administradores
- Embedding: Nenhum (apenas credenciais)

### 1.2 Estratégia Embedding vs Referencing

- **Embedding**: Estatísticas de jogadores dentro de `matches` (dados acessados juntos, write-heavy)
- **Referencing**: `season-id` em `matches`, `championship-id` em `seasons`, `player-id` nas estatísticas (normalização, flexibilidade)
- **Híbrido**: Estatísticas agregadas por temporada em `players` (read optimization); `championship-id` e `championship-name` denormalizados em `seasons` e `by-season` para queries rápidas

## 2. Schemas JSON de Exemplo

### 2.1 Collection: `championships`

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439011"),
  "name": "Boleiro fut7",
  "format": "society-7",
  "season-ids": [
    ObjectId("607f1f77bcf86cd799439020"),
    ObjectId("607f1f77bcf86cd799439021")
  ],
  "created-at": ISODate("2025-01-15T10:00:00Z"),
  "updated-at": ISODate("2025-01-15T10:00:00Z")
}
```

**Campos:**
- `name` (String, required): Nome do campeonato
- `format` (String, enum, required): Formato do campeonato
  - Valores: "campo-11", "campo-10", "society-7", "society-6", "futsal", "outro"
- `season-ids` (Array of ObjectId): Referências às temporadas deste campeonato
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

### 2.2 Collection: `seasons`

```json
{
  "_id": ObjectId("607f1f77bcf86cd799439020"),
  "championship-id": ObjectId("507f1f77bcf86cd799439011"),
  "championship-name": "Boleiro fut7",
  "season": "2025",
  "format": "society-7",
  "status": "active",
  "enrolled-player-ids": [
    ObjectId("507f1f77bcf86cd799439012"),
    ObjectId("507f1f77bcf86cd799439014")
  ],
  "match-ids": [
    ObjectId("507f1f77bcf86cd799439013")
  ],
  "winner-player-ids": [],
  "titles-count": 0,
  "start-date": ISODate("2025-01-01T00:00:00Z"),
  "end-date": ISODate("2025-12-31T23:59:59Z"),
  "finished-at": null,
  "created-at": ISODate("2025-01-15T10:00:00Z"),
  "updated-at": ISODate("2025-01-15T10:00:00Z")
}
```

**Campos:**
- `championship-id` (ObjectId, required): Referência ao campeonato raiz
- `championship-name` (String, required): Nome do campeonato (denormalizado para leitura rápida)
- `season` (String, required): Ano/identificador da temporada (ex: "2025")
- `format` (String, enum, required): Formato da temporada (denormalizado de `championships`)
- `status` (String, enum): Status da temporada
  - Valores: "active", "completed", "cancelled"
  - Regra: no máximo uma temporada `active` por campeonato
- `enrolled-player-ids` (Array of ObjectId): Jogadores inscritos nesta temporada
- `match-ids` (Array of ObjectId): Partidas disputadas nesta temporada
- `winner-player-ids` (Array of ObjectId): Jogadores vencedores (preenchido ao finalizar)
- `titles-count` (Number): Quantidade de títulos concedidos a cada vencedor
- `start-date` (Date): Data de início
- `end-date` (Date): Data prevista de término
- `finished-at` (Date): Data/hora de finalização efetiva
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

**Índice único:** `(championship-id, season)`

### 2.3 Collection: `players`

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439012"),
  "name": "Gabriel Leal",
  "nickname": "Leal",
  "position": "Atacante",
  "birth-date": ISODate("1995-05-15T00:00:00Z"),
  "height": 175,
  "weight": 72,
  "photo-url": "/uploads/players/gabriel-leal.jpg",
  "team-id": ObjectId("507f1f77bcf86cd799439015"),
  "active": true,
  "aggregated-stats": {
    "total": {
      "games": 22,
      "goals": 12,
      "assists": 8,
      "titles": 1
    },
    "by-season": [
      {
        "season-id": ObjectId("607f1f77bcf86cd799439020"),
        "championship-id": ObjectId("507f1f77bcf86cd799439011"),
        "championship-name": "Boleiro fut7",
        "season": "2025",
        "games": 5,
        "goals": 3,
        "assists": 1,
        "titles": 0
      }
    ]
  },
  "created-at": ISODate("2025-01-15T10:00:00Z"),
  "updated-at": ISODate("2025-01-15T10:00:00Z")
}
```

**Campos:**
- `name` (String, required): Nome completo do jogador
- `nickname` (String, optional): Apelido
- `position` (String, enum, required): Posição do jogador
  - Valores: "Goleiro", "Zagueiro", "Lateral", "Volante", "Meia", "Atacante"
  - Regra BRM-16: se nome contiver "GK" (case-insensitive) e posição não informada, inferir "Goleiro"
- `birth-date` (Date, optional): Data de nascimento
- `height` (Number, optional): Altura em cm
- `weight` (Number, optional): Peso em kg
- `photo-url` (String, optional): URL da foto do jogador
- `team-id` (ObjectId, required): Referência ao time
- `active` (Boolean, default: true): Se o jogador está ativo
- `aggregated-stats` (Object): Estatísticas agregadas (cache de leitura)
  - `total` (Object): Totais acumulados de todas as temporadas
    - `games` (Number), `goals` (Number), `assists` (Number), `titles` (Number)
  - `by-season` (Array): Estatísticas por temporada
    - `season-id` (ObjectId): Referência à temporada
    - `championship-id` (ObjectId): Denormalizado para queries
    - `championship-name` (String): Denormalizado para leitura rápida
    - `season` (String): Ano da temporada (denormalizado)
    - `games`, `goals`, `assists`, `titles` (Number)
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

### 2.4 Collection: `matches`

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439013"),
  "season-id": ObjectId("607f1f77bcf86cd799439020"),
  "championship-id": ObjectId("507f1f77bcf86cd799439011"),
  "date": ISODate("2025-02-15T19:00:00Z"),
  "opponent": "Time Adversário",
  "venue": "Arena 61",
  "result": {
    "our-score": 3,
    "opponent-score": 1,
    "outcome": "win"
  },
  "player-statistics": [
    {
      "player-id": ObjectId("507f1f77bcf86cd799439012"),
      "player-name": "Gabriel Leal",
      "position": "Atacante",
      "team-id": ObjectId("507f1f77bcf86cd799439015"),
      "goals": 2,
      "assists": 1,
      "yellow-cards": 0,
      "red-cards": 0,
      "minutes-played": 60,
      "substituted": false
    }
  ],
  "created-at": ISODate("2025-02-15T21:00:00Z"),
  "updated-at": ISODate("2025-02-15T21:00:00Z")
}
```

**Campos:**
- `season-id` (ObjectId, required): Referência à temporada
- `championship-id` (ObjectId, required): Denormalizado da temporada para queries rápidas
- `date` (Date, required): Data e hora da partida
- `opponent` (String, optional): Nome do time adversário
- `venue` (String, optional): Local da partida
- `result` (Object, optional): Resultado da partida
  - `our-score` (Number): Gols do nosso time
  - `opponent-score` (Number): Gols do adversário
  - `outcome` (String, enum): "win", "draw", "loss"
- `player-statistics` (Array, required): Estatísticas dos jogadores na partida
  - `player-id` (ObjectId, required): ID do jogador
  - `player-name` (String, required): Nome (denormalizado)
  - `position` (String, required): Posição na partida
  - `team-id` (ObjectId, required): Time do jogador (validado contra `players.team-id`)
  - `goals` (Number, default: 0)
  - `assists` (Number, default: 0)
  - `yellow-cards` (Number, default: 0)
  - `red-cards` (Number, default: 0)
  - `minutes-played` (Number, optional)
  - `substituted` (Boolean, default: false)
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

### 2.5 Collection: `teams`

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439015"),
  "name": "Galáticos",
  "active-player-ids": [
    ObjectId("507f1f77bcf86cd799439012"),
    ObjectId("507f1f77bcf86cd799439014")
  ],
  "created-at": ISODate("2025-01-15T10:00:00Z"),
  "updated-at": ISODate("2025-01-15T10:00:00Z")
}
```

**Campos:**
- `name` (String, required, unique): Nome do time
- `active-player-ids` (Array of ObjectId): Lista de IDs de jogadores ativos
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

### 2.6 Collection: `admins`

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439016"),
  "username": "admin",
  "password-hash": "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
  "created-at": ISODate("2025-01-15T10:00:00Z"),
  "last-login": ISODate("2025-01-20T14:30:00Z")
}
```

**Campos:**
- `username` (String, required, unique): Nome de usuário
- `password-hash` (String, required): Hash da senha (bcrypt $2a$)
- `created-at` (Date): Data de criação
- `last-login` (Date, optional): Data do último login

## 3. Relacionamentos

```
teams         (1) ──< (N) players
championships (1) ──< (N) seasons         (via season-ids / championship-id)
seasons       (1) ──< (N) matches         (via match-ids / season-id)
seasons       (N) ──< (N) players         (via enrolled-player-ids)
matches       (1) ──< (N) player-statistics (embedded) ──> (1) players
players       (N) ──< (N) seasons         (via aggregated-stats.by-season)
```

## 4. Considerações de Design

### 4.1 Política de desnormalização

Campos denormalizados são **cache de leitura**; a fonte de verdade permanece na coleção de origem.

| Campo denormalizado | Onde | Fonte de verdade | Quando atualizar |
|---------------------|------|------------------|------------------|
| `player-name`, `position` | `matches.player-statistics` | `players` | Escrita de partida (UI/API) |
| `championship-id` | `matches` | `seasons` / `championships` | Criação/edição de partida |
| `championship-name`, `format` | `seasons` | `championships` | Criação de temporada; evitar drift em rename (re-sync opcional) |
| `championship-name`, `season` | `players.aggregated-stats.by-season` | `seasons` + jobs de recalc | Após CRUD de partidas (background job) |
| `team-name` em respostas API | não persistir em `players` | `teams` | Leitura: `logic/players` anexa nome do time |

**Regras:**

- Não confiar em `players.team-name` armazenado no documento; o bundle de detalhe resolve o nome via `teams`.
- `aggregated-stats` em `players` é recalculável; `matches.player-statistics` é autoritativo para estatísticas por jogo.
- Novos campos denormalizados exigem entrada nesta tabela e, se necessário, índice em `scripts/mongodb/mongodb-indexes.js`.

### 4.2 Denormalização (resumo histórico)

- `player-name` e `position` são denormalizados em `matches.player-statistics` para evitar lookups frequentes
- `championship-name`, `championship-id` e `season` são denormalizados em `players.aggregated-stats.by-season` para queries rápidas de dashboard
- `championship-name` e `format` são denormalizados em `seasons` para evitar join com `championships` em listagens
- `championship-id` é denormalizado em `matches` para manter compatibilidade com queries de agregação existentes

### 4.3 Performance

- Estatísticas agregadas em `players.aggregated-stats` são atualizadas via background job após inserção/atualização de partidas
- Queries de dashboard usam dados cached em `aggregated-stats`
- Queries detalhadas usam aggregation pipelines em `matches`
- `seasons.match-ids` e `seasons.enrolled-player-ids` são sincronizados ao criar/remover matches e inscrições

### 4.4 Consistência e Invariantes

- Estatísticas em `matches` são a fonte de verdade
- `aggregated-stats` em `players` é cache recalculável a qualquer momento
- No máximo uma temporada com `status: "active"` por campeonato (enforced no backend)
- `team-id` em `matches.player-statistics` deve coincidir com `players.team-id` (validado no backend — BRM-09)
- `enrolled-player-ids` em `seasons` deve incluir todos os jogadores com estatísticas em `match-ids` da mesma temporada

## 5. Índices

Fonte de verdade para criação: `scripts/mongodb/mongodb-indexes.js` (espelhado em `config/database/init-indexes.js` no primeiro boot do container).

| Coleção | Índice | Tipo | Uso |
|---------|--------|------|-----|
| `championships` | `{ name: 1, season: 1 }` | unique | Legado; raiz actual usa `name` + `season-ids` |
| `championships` | `{ status: 1 }` | — | Filtro por estado |
| `championships` | `{ start-date: 1, end-date: 1 }` | — | Intervalos |
| `seasons` | `{ championship-id: 1, season: 1 }` | unique | Uma temporada por ano/label por campeonato |
| `seasons` | `{ championship-id: 1, status: 1 }` | — | Temporada activa por campeonato |
| `players` | `{ name: 1 }` | — | Listagem por nome |
| `players` | `{ team-id: 1, active: 1 }` | — | Elenco por time |
| `players` | `{ position: 1 }` | — | Filtro posição |
| `players` | `{ aggregated-stats.total.games: -1 }` | — | Ranking jogos |
| `players` | `{ aggregated-stats.by-championship.championship-id: 1 }` | — | Stats por campeonato |
| `players` | `{ nickname: 1 }` | — | Busca apelido |
| `matches` | `{ championship-id: 1, date: -1 }` | — | Histórico por campeonato |
| `matches` | `{ date: -1 }` | — | Ordenação global |
| `matches` | `{ player-statistics.player-id: 1 }` | — | Partidas por jogador |
| `matches` | `{ season-id: 1, date: -1 }` | — | Partidas por temporada (recomendado em novos deploys) |
| `teams` | `{ name: 1 }` | unique | Nome único |
| `admins` | `{ username: 1 }` | unique | Login |

**Manutenção:** ao adicionar query frequente nova, actualizar o script JS, correr `./bin/galaticos db:setup` em ambientes existentes, e reflectir a linha nesta tabela.
