# MongoDB Schema Design - Sports Roster Management

## Summary

Read this when you change collections, indexes, or embedding. Vocabulary: [concepts.md](../../concepts.md).

Galáticos stores sports roster data in six MongoDB collections: `championships`, `seasons`, `players`, `matches`, `teams`, and `admins`. Championships are the root entity; seasons reference championships and track enrolled players and matches; matches embed per-player statistics and reference a season. Players cache aggregated stats per season for fast reads, while match documents remain the source of truth for game-level stats. Denormalized fields (`championship-name`, `player-name`, etc.) speed up dashboards and listings and must be kept in sync via writes and background jobs. Indexes are defined in `scripts/mongodb/mongodb-indexes.js`.

## 1. Collection Structure

### 1.1 Main Collections

**`championships`** - Championships (root entity)
- Embedding: None
- Relationship: References `seasons` via `season-ids`

**`seasons`** - Seasons
- Embedding: None
- Relationship: References `championships` (`championship-id`), `matches` (`match-ids`), `players` (`enrolled-player-ids`, `winner-player-ids`)

**`players`** - Players
- Embedding: Aggregated statistics per season (read cache)
- Relationship: Referenced by `seasons.enrolled-player-ids` and `matches.player-statistics`

**`matches`** - Matches/Games
- Embedding: Player statistics for the match (array)
- Relationship: References `season-id`; `player-id` in embedded statistics

**`teams`** - Team (single team)
- Embedding: List of active players (references)
- Relationship: Referenced by `players.team-id`

**`admins`** - Administrators
- Embedding: None (credentials only)

### 1.2 Embedding vs Referencing Strategy

- **Embedding**: Player statistics inside `matches` (data accessed together, write-heavy)
- **Referencing**: `season-id` in `matches`, `championship-id` in `seasons`, `player-id` in statistics (normalization, flexibility)
- **Hybrid**: Aggregated statistics per season in `players` (read optimization); `championship-id` and `championship-name` denormalized in `seasons` and `by-season` for fast queries

## 2. Example JSON Schemas

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

**Fields:**
- `name` (String, required): Championship name
- `format` (String, enum, required): Championship format
  - Values: "campo-11", "campo-10", "society-7", "society-6", "futsal", "outro"
- `season-ids` (Array of ObjectId): References to seasons for this championship
- `created-at` (Date): Creation timestamp
- `updated-at` (Date): Last update timestamp

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

**Fields:**
- `championship-id` (ObjectId, required): Reference to the root championship
- `championship-name` (String, required): Championship name (denormalized for fast reads)
- `season` (String, required): Season year/identifier (e.g. "2025")
- `format` (String, enum, required): Season format (denormalized from `championships`)
- `status` (String, enum): Season status
  - Values: "active", "completed", "cancelled"
  - Rule: at most one `active` season per championship
- `enrolled-player-ids` (Array of ObjectId): Players enrolled in this season
- `match-ids` (Array of ObjectId): Matches played in this season
- `winner-player-ids` (Array of ObjectId): Winning players (filled when the season is finalized)
- `titles-count` (Number): Number of titles awarded to each winner
- `start-date` (Date): Start date
- `end-date` (Date): Expected end date
- `finished-at` (Date): Actual completion date/time
- `created-at` (Date): Creation timestamp
- `updated-at` (Date): Last update timestamp

**Unique index:** `(championship-id, season)`

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

**Fields:**
- `name` (String, required): Player full name
- `nickname` (String, optional): Nickname
- `position` (String, enum, required): Player position
  - Values: "Goleiro", "Zagueiro", "Lateral", "Volante", "Meia", "Atacante"
  - BRM-16 rule: if the name contains "GK" (case-insensitive) and position is not provided, infer "Goleiro"
- `birth-date` (Date, optional): Date of birth
- `height` (Number, optional): Height in cm
- `weight` (Number, optional): Weight in kg
- `photo-url` (String, optional): Player photo URL
- `team-id` (ObjectId, required): Reference to the team
- `active` (Boolean, default: true): Whether the player is active
- `aggregated-stats` (Object): Aggregated statistics (read cache)
  - `total` (Object): Cumulative totals across all seasons
    - `games` (Number), `goals` (Number), `assists` (Number), `titles` (Number)
  - `by-season` (Array): Statistics per season
    - `season-id` (ObjectId): Reference to the season
    - `championship-id` (ObjectId): Denormalized for queries
    - `championship-name` (String): Denormalized for fast reads
    - `season` (String): Season year (denormalized)
    - `games`, `goals`, `assists`, `titles` (Number)
- `created-at` (Date): Creation timestamp
- `updated-at` (Date): Last update timestamp

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

**Fields:**
- `season-id` (ObjectId, required): Reference to the season
- `championship-id` (ObjectId, required): Denormalized from the season for fast queries
- `date` (Date, required): Match date and time
- `opponent` (String, optional): Opponent team name
- `venue` (String, optional): Match venue
- `result` (Object, optional): Match result
  - `our-score` (Number): Our team's goals
  - `opponent-score` (Number): Opponent's goals
  - `outcome` (String, enum): "win", "draw", "loss"
- `player-statistics` (Array, required): Player statistics for the match
  - `player-id` (ObjectId, required): Player ID
  - `player-name` (String, required): Name (denormalized)
  - `position` (String, required): Position in the match
  - `team-id` (ObjectId, required): Player's team (validated against `players.team-id`)
  - `goals` (Number, default: 0)
  - `assists` (Number, default: 0)
  - `yellow-cards` (Number, default: 0)
  - `red-cards` (Number, default: 0)
  - `minutes-played` (Number, optional)
  - `substituted` (Boolean, default: false)
- `created-at` (Date): Creation timestamp
- `updated-at` (Date): Last update timestamp

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

**Fields:**
- `name` (String, required, unique): Team name
- `active-player-ids` (Array of ObjectId): List of active player IDs
- `created-at` (Date): Creation timestamp
- `updated-at` (Date): Last update timestamp

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

**Fields:**
- `username` (String, required, unique): Username
- `password-hash` (String, required): Password hash (bcrypt $2a$)
- `created-at` (Date): Creation timestamp
- `last-login` (Date, optional): Last login timestamp

## 3. Relationships

```
teams         (1) ──< (N) players
championships (1) ──< (N) seasons         (via season-ids / championship-id)
seasons       (1) ──< (N) matches         (via match-ids / season-id)
seasons       (N) ──< (N) players         (via enrolled-player-ids)
matches       (1) ──< (N) player-statistics (embedded) ──> (1) players
players       (N) ──< (N) seasons         (via aggregated-stats.by-season)
```

## 4. Design Considerations

### 4.1 Denormalization Policy

Denormalized fields are a **read cache**; the source of truth remains in the origin collection.

| Denormalized field | Where | Source of truth | When to update |
|---------------------|------|------------------|------------------|
| `player-name`, `position` | `matches.player-statistics` | `players` | Match write (UI/API) |
| `championship-id` | `matches` | `seasons` / `championships` | Match create/edit |
| `championship-name`, `format` | `seasons` | `championships` | Season creation; avoid drift on rename (optional re-sync) |
| `championship-name`, `season` | `players.aggregated-stats.by-season` | `seasons` + recalc jobs | After match CRUD (background job) |
| `team-name` in API responses | not persisted on `players` | `teams` | Read path: `logic/players` attaches team name |

**Rules:**

- Do not rely on `players.team-name` stored on the document; the detail bundle resolves the name via `teams`.
- `aggregated-stats` on `players` is recalculable; `matches.player-statistics` is authoritative for per-game statistics.
- New denormalized fields require an entry in this table and, if needed, an index in `scripts/mongodb/mongodb-indexes.js`.

### 4.2 Denormalization (historical summary)

- `player-name` and `position` are denormalized in `matches.player-statistics` to avoid frequent lookups
- `championship-name`, `championship-id`, and `season` are denormalized in `players.aggregated-stats.by-season` for fast dashboard queries
- `championship-name` and `format` are denormalized in `seasons` to avoid joining `championships` in listings
- `championship-id` is denormalized in `matches` to preserve compatibility with existing aggregation queries

### 4.3 Performance

- Aggregated statistics in `players.aggregated-stats` are updated via a background job after match insert/update
- Dashboard queries use data cached in `aggregated-stats`
- Detailed queries use aggregation pipelines on `matches`
- `seasons.match-ids` and `seasons.enrolled-player-ids` are synchronized when creating/removing matches and enrollments

### 4.4 Consistency and Invariants

- Statistics in `matches` are the source of truth
- `aggregated-stats` on `players` is a recalculable cache at any time
- At most one season with `status: "active"` per championship (enforced in the backend)
- `team-id` in `matches.player-statistics` must match `players.team-id` (validated in the backend — BRM-09)
- `enrolled-player-ids` on `seasons` must include all players with statistics in `match-ids` for the same season

## 5. Indexes

Source of truth for creation: `scripts/mongodb/mongodb-indexes.js` (mirrored in `config/database/init-indexes.js` on first container boot).

| Collection | Index | Type | Usage |
|---------|--------|------|-----|
| `championships` | `{ name: 1, season: 1 }` | unique | Legacy; current root uses `name` + `season-ids` |
| `championships` | `{ status: 1 }` | — | Filter by status |
| `championships` | `{ start-date: 1, end-date: 1 }` | — | Date ranges |
| `seasons` | `{ championship-id: 1, season: 1 }` | unique | One season per year/label per championship |
| `seasons` | `{ championship-id: 1, status: 1 }` | — | Active season per championship |
| `players` | `{ name: 1 }` | — | List by name |
| `players` | `{ team-id: 1, active: 1 }` | — | Roster by team |
| `players` | `{ position: 1 }` | — | Filter by position |
| `players` | `{ aggregated-stats.total.games: -1 }` | — | Games ranking |
| `players` | `{ aggregated-stats.by-championship.championship-id: 1 }` | — | Stats by championship |
| `players` | `{ nickname: 1 }` | — | Nickname search |
| `matches` | `{ championship-id: 1, date: -1 }` | — | History by championship |
| `matches` | `{ date: -1 }` | — | Global sort order |
| `matches` | `{ player-statistics.player-id: 1 }` | — | Matches by player |
| `matches` | `{ season-id: 1, date: -1 }` | — | Matches by season (recommended on new deploys) |
| `teams` | `{ name: 1 }` | unique | Unique name |
| `admins` | `{ username: 1 }` | unique | Login |

**Maintenance:** when adding a new frequent query, update the JS script, run `./bin/galaticos db:setup` on existing environments, and reflect the row in this table.
