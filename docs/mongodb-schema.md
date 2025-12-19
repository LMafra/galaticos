# MongoDB Schema Design - Gestão de Elenco Esportivo

## 1. Estrutura de Coleções

### 1.1 Coleções Principais

**`championships`** - Campeonatos
- Embedding: Nenhum (referência simples)
- Relacionamento: Referenciado por `matches` e `player-statistics`

**`players`** - Jogadores
- Embedding: Estatísticas agregadas por campeonato (para performance)
- Relacionamento: Referenciado por `matches.player-statistics`

**`matches`** - Partidas/Jogos
- Embedding: Estatísticas de jogadores na partida (array)
- Relacionamento: Referencia `championship-id` e `player-id` nas estatísticas

**`teams`** - Time (único)
- Embedding: Lista de jogadores ativos (referências)
- Relacionamento: Referenciado por `players.team-id`

**`admins`** - Administradores
- Embedding: Nenhum (apenas credenciais)

### 1.2 Estratégia Embedding vs Referencing

- **Embedding**: Estatísticas de jogadores dentro de `matches` (dados acessados juntos, write-heavy)
- **Referencing**: `championship-id` em `matches`, `player-id` em estatísticas (normalização, flexibilidade)
- **Híbrido**: Estatísticas agregadas por campeonato em `players` (read optimization)

## 2. Schemas JSON de Exemplo

### 2.1 Collection: `championships`

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439011"),
  "name": "Boleiro fut7",
  "season": "2025",
  "format": "society-7",
  "start-date": ISODate("2025-01-01T00:00:00Z"),
  "end-date": ISODate("2025-12-31T23:59:59Z"),
  "status": "active",
  "created-at": ISODate("2025-01-15T10:00:00Z"),
  "updated-at": ISODate("2025-01-15T10:00:00Z")
}
```

**Campos:**
- `name` (String, required): Nome do campeonato
- `season` (String, required): Temporada (ex: "2025")
- `format` (String, enum, required): Formato do campeonato
  - Valores: "campo-11", "campo-10", "society-7", "society-6", "futsal", "outro"
- `start-date` (Date): Data de início
- `end-date` (Date): Data de término
- `status` (String, enum): Status do campeonato
  - Valores: "active", "finished", "cancelled"
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

### 2.2 Collection: `players`

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
    "by-championship": [
      {
        "championship-id": ObjectId("507f1f77bcf86cd799439011"),
        "championship-name": "Boleiro fut7",
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
- `birth-date` (Date, optional): Data de nascimento
- `height` (Number, optional): Altura em cm
- `weight` (Number, optional): Peso em kg
- `photo-url` (String, optional): URL da foto do jogador
- `team-id` (ObjectId, required): Referência ao time
- `active` (Boolean, default: true): Se o jogador está ativo
- `aggregated-stats` (Object): Estatísticas agregadas (cache)
  - `total` (Object): Estatísticas totais
    - `games` (Number): Total de jogos
    - `goals` (Number): Total de gols
    - `assists` (Number): Total de assistências
    - `titles` (Number): Total de títulos
  - `by-championship` (Array): Estatísticas por campeonato
    - `championship-id` (ObjectId): ID do campeonato
    - `championship-name` (String): Nome do campeonato
    - `games` (Number): Jogos no campeonato
    - `goals` (Number): Gols no campeonato
    - `assists` (Number): Assistências no campeonato
    - `titles` (Number): Títulos no campeonato
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

### 2.3 Collection: `matches`

```json
{
  "_id": ObjectId("507f1f77bcf86cd799439013"),
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
- `championship-id` (ObjectId, required): Referência ao campeonato
- `date` (Date, required): Data e hora da partida
- `opponent` (String, optional): Nome do time adversário
- `venue` (String, optional): Local da partida
- `result` (Object, optional): Resultado da partida
  - `our-score` (Number): Gols do nosso time
  - `opponent-score` (Number): Gols do adversário
  - `outcome` (String, enum): Resultado
    - Valores: "win", "draw", "loss"
- `player-statistics` (Array, required): Estatísticas dos jogadores
  - `player-id` (ObjectId, required): ID do jogador
  - `player-name` (String, required): Nome do jogador (denormalizado)
  - `position` (String, required): Posição na partida
  - `goals` (Number, default: 0): Gols marcados
  - `assists` (Number, default: 0): Assistências
  - `yellow-cards` (Number, default: 0): Cartões amarelos
  - `red-cards` (Number, default: 0): Cartões vermelhos
  - `minutes-played` (Number, optional): Minutos jogados
  - `substituted` (Boolean, default: false): Se foi substituído
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

### 2.4 Collection: `teams`

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
- `active-player-ids` (Array[ObjectId]): Lista de IDs de jogadores ativos
- `created-at` (Date): Data de criação
- `updated-at` (Date): Data de última atualização

### 2.5 Collection: `admins`

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
- `password-hash` (String, required): Hash da senha (bcrypt)
- `created-at` (Date): Data de criação
- `last-login` (Date, optional): Data do último login

## 3. Relacionamentos

```
teams (1) ──< (N) players
championships (1) ──< (N) matches
matches (1) ──< (N) player-statistics (embedded) ──> (1) players
players (N) ──< (N) championships (via aggregated-stats.by-championship)
```

## 4. Considerações de Design

### 4.1 Denormalização
- `player-name` e `position` são denormalizados em `matches.player-statistics` para evitar lookups frequentes
- `championship-name` é denormalizado em `players.aggregated-stats.by-championship` para queries rápidas

### 4.2 Performance
- Estatísticas agregadas em `players.aggregated-stats` são atualizadas via background job após inserção/atualização de partidas
- Queries de dashboard usam dados cached em `aggregated-stats`
- Queries detalhadas usam aggregation pipelines em `matches`

### 4.3 Consistência
- Estatísticas em `matches` são a fonte de verdade
- `aggregated-stats` em `players` é cache que pode ser recalculado a qualquer momento

