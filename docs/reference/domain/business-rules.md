# Business rules — Galáticos

**Summary:** Canonical catalog of Galáticos business rules. Each rule has a stable `RN-*` identifier, description, implementation references, and expected behavior. Read this when you implement or change domain behavior. Terms: [concepts.md](../../concepts.md). Implementation audit: [business-rules-audit.md](business-rules-audit.md). Metrics: [metrics-catalog.md](../analytics/metrics-catalog.md) — do not redefine KPIs here.

**Last updated:** April 23, 2026

Cross-check: [business-rules-audit.md](business-rules-audit.md).

---

## Table of contents

1. [Authentication and Authorization](#authentication-and-authorization)
2. [Championships](#championships)
3. [Teams](#teams)
4. [Players](#players)
5. [Matches](#matches)
6. [Statistics and Aggregations](#statistics-and-aggregations)
7. [Data Validation](#data-validation)
8. [Referential Integrity](#referential-integrity)
9. [Response Formatting](#response-formatting)
10. [Timestamp Rules](#timestamp-rules)
11. [Query Rules](#query-rules)
12. [Initialization Rules](#initialization-rules)
13. [Final Notes](#final-notes)
14. [Analytics Alignment](#analytics-alignment)
15. [Appendix: Business Rules Manifesto model](#appendix-business-rules-manifesto-model)

---

## Authentication and Authorization

### RN-AUTH-01: Stateless JWT Authentication
- **Description**: The system uses JWT (JSON Web Token) authentication without server-side session state.
- **File**: `src/galaticos/middleware/auth.clj`
- **Behavior**:
  - Tokens are signed with the HS256 algorithm
  - Tokens include claims: `sub` (username), `iat` (issued at), `exp` (expiration)
  - Default token TTL: 86400 seconds (24 hours), configurable via `JWT_TTL_SECONDS`

### RN-AUTH-02: JWT Secret Required in Production
- **Description**: In production environments, a JWT secret must be configured via environment variable.
- **File**: `src/galaticos/middleware/auth.clj` (lines 27–29)
- **Behavior**:
  - Dev/test environments may use the default secret `"dev-secret"`
  - Production requires `JWT_SECRET` to be set
  - The system throws an exception if the secret is not defined in production

### RN-AUTH-03: Bearer Token Authentication
- **Description**: All authenticated requests must include the header `Authorization: Bearer <token>`.
- **File**: `src/galaticos/middleware/auth.clj` (lines 47–52)
- **Behavior**:
  - Token is extracted from the Authorization header
  - Format: `Bearer <token>` (case-insensitive)
  - Invalid or missing token returns 401 Unauthorized

### RN-AUTH-04: Development Bypass Mode
- **Description**: Authentication can be disabled in dev/test via `DISABLE_AUTH=true`.
- **File**: `src/galaticos/middleware/auth.clj` (lines 31–42)
- **Behavior**:
  - Allowed only in dev/development/test/testing environments
  - The system throws an exception if attempted in production
  - A warning is logged when enabled

### RN-AUTH-05: Admin Credential Verification
- **Description**: Login validates credentials against the `admins` collection in MongoDB.
- **File**: `src/galaticos/handlers/auth.clj` (lines 7–23)
- **Behavior**:
  - Username and password are required
  - Password is verified with bcrypt hash
  - Updates `last-login` on success
  - Returns a JWT on success
  - Returns 401 for invalid credentials

### RN-AUTH-06: Stateless Logout
- **Description**: Logout is a client-side operation; the server does not maintain state.
- **File**: `src/galaticos/handlers/auth.clj` (lines 25–33)
- **Behavior**:
  - Endpoint exists for API parity only
  - Client must discard the token locally
  - Always returns success

---

## Championships

### RN-CHAMP-01: Required Fields
- **Description**: Championships must have name, season, and titles count.
- **File**: `src/galaticos/handlers/championships.clj` (lines 11–12)
- **Required fields**:
  - `name` (string): Championship name
  - `season` (string): Season (e.g. `"2024"`, `"2023/2024"`)
  - `titles-count` (number): Number of titles contested

### RN-CHAMP-02: Allowed Fields
- **Description**: Only specific fields are accepted when creating or updating championships.
- **File**: `src/galaticos/handlers/championships.clj` (lines 8–9)
- **Allowed fields**:
  - `name`: Championship name
  - `season`: Season
  - `status`: Status (e.g. `"active"`, `"completed"`, `"cancelled"`)
  - `format`: Format (e.g. `"league"`, `"cup"`, `"knockout"`)
  - `start-date`: Start date
  - `end-date`: End date
  - `location`: Location
  - `notes`: Notes
  - `titles-count`: Number of titles

### RN-CHAMP-03: Unknown Field Validation
- **Description**: Fields not listed as allowed produce a 400 error.
- **File**: `src/galaticos/handlers/championships.clj` (lines 20–24)
- **Behavior**:
  - Returns message: `"Unknown fields: <field list>"`
  - HTTP status: 400 Bad Request

### RN-CHAMP-04: Filter by Status
- **Description**: Championship listing can be filtered by status.
- **File**: `src/galaticos/handlers/championships.clj` (lines 35–45)
- **Behavior**:
  - Query param `status` filters results
  - Example: `GET /api/championships?status=active`

### RN-CHAMP-05: Deletion Protection When Matches Exist
- **Description**: Championships with associated matches cannot be deleted.
- **File**: `src/galaticos/handlers/championships.clj` (lines 90–103)
- **Behavior**:
  - Checks for matches before delete
  - Returns 409 Conflict if matches exist
  - Message: `"Cannot delete championship: it has associated matches. Please delete or reassign matches first."`

### RN-CHAMP-06: Automatic Timestamps
- **Description**: Championships receive creation and update timestamps automatically.
- **File**: `src/galaticos/db/championships.clj` (lines 14–24)
- **Behavior**:
  - `created-at`: set on create
  - `updated-at`: updated on every modification

---

## Teams

### RN-TEAM-01: Required Fields
- **Description**: Teams must have at least a name.
- **File**: `src/galaticos/handlers/teams.clj` (lines 11–12)
- **Required fields**:
  - `name` (string): Team name

### RN-TEAM-02: Allowed Fields
- **Description**: Only specific fields are accepted when creating or updating teams.
- **File**: `src/galaticos/handlers/teams.clj` (lines 8–9)
- **Allowed fields**:
  - `name`: Team name
  - `city`: City
  - `coach`: Coach
  - `stadium`: Stadium
  - `founded-year`: Year founded
  - `logo-url`: Logo URL
  - `active-player-ids`: Active player IDs (array)
  - `notes`: Notes

### RN-TEAM-03: Active Player List
- **Description**: Teams maintain a list of active player IDs.
- **File**: `src/galaticos/db/teams.clj` (lines 11–22)
- **Behavior**:
  - Field `active-player-ids` is an array of ObjectIds
  - Initialized as an empty array on create
  - Managed via add/remove player operations

### RN-TEAM-04: Add Player to Team
- **Description**: Players can be added to a team’s active list.
- **File**: `src/galaticos/handlers/teams.clj` (lines 105–119)
- **Behavior**:
  - Uses `$addToSet` to avoid duplicates
  - Requires valid `team-id` and `player-id`
  - Returns updated team

### RN-TEAM-05: Remove Player from Team
- **Description**: Players can be removed from a team’s active list.
- **File**: `src/galaticos/handlers/teams.clj` (lines 121–135)
- **Behavior**:
  - Uses `$pull` to remove the ID
  - Requires valid `team-id` and `player-id`
  - Returns updated team

### RN-TEAM-06: Deletion Protection When Players Exist
- **Description**: Teams with active players cannot be deleted.
- **File**: `src/galaticos/handlers/teams.clj` (lines 90–103)
- **Behavior**:
  - Checks for active players before delete
  - Returns 409 Conflict if players exist
  - Message: `"Cannot delete team: it has associated players. Please remove players from team first."`

### RN-TEAM-07: Player ID Normalization
- **Description**: Player IDs in `active-player-ids` are converted to ObjectId.
- **File**: `src/galaticos/handlers/teams.clj` (lines 23–25)
- **Behavior**:
  - Automatic conversion from strings to ObjectId
  - Validates ObjectId format (returns 400 if invalid)

---

## Players

### RN-PLAYER-01: Required Fields
- **Description**: Players must have name and position.
- **File**: `src/galaticos/handlers/players.clj` (lines 12–13)
- **Required fields**:
  - `name` (string): Player name
  - `position` (string): Position (e.g. `"goleiro"`, `"zagueiro"`, `"atacante"`)

### RN-PLAYER-02: Allowed Fields
- **Description**: Only specific fields are accepted when creating or updating players.
- **File**: `src/galaticos/handlers/players.clj` (lines 8–10)
- **Allowed fields**:
  - `name`: Player name
  - `position`: Position
  - `team-id`: Team ID (ObjectId)
  - `birth-date`: Birth date
  - `nationality`: Nationality
  - `height`: Height (cm)
  - `weight`: Weight (kg)
  - `preferred-foot`: Preferred foot
  - `shirt-number`: Shirt number
  - `active`: Active/inactive status (boolean)
  - `email`: Email
  - `phone`: Phone
  - `number`: Number
  - `photo-url`: Photo URL
  - `notes`: Notes

### RN-PLAYER-03: Active by Default
- **Description**: Players are created with active status (`active: true`).
- **File**: `src/galaticos/db/players.clj` (lines 15–29)
- **Behavior**:
  - Field `active` set to `true` on create
  - Used to filter listings

### RN-PLAYER-04: Soft Delete
- **Description**: Player deletion is soft delete (marked inactive).
- **File**: `src/galaticos/db/players.clj` (lines 79–82)
- **Behavior**:
  - Sets `active: false` instead of removing the document
  - Inactive players are excluded from listings by default
  - Preserves historical data and references

### RN-PLAYER-05: Initialized Aggregated Statistics
- **Description**: Players are created with zeroed aggregated statistics structure.
- **File**: `src/galaticos/db/players.clj` (lines 24–25)
- **Behavior**:
  - Field `aggregated-stats` initialized with:
    - `total`: `{games: 0, goals: 0, assists: 0, titles: 0}`
    - `by-championship`: `[]` (empty array)
  - Populated by match aggregation process

### RN-PLAYER-06: Active Player Filtering
- **Description**: Listing can filter to active players only.
- **File**: `src/galaticos/handlers/players.clj` (lines 37–51)
- **Behavior**:
  - Query param `active=true` filters active only
  - Query param `team-id` filters by team
  - Default: returns all players (active and inactive)

### RN-PLAYER-07: Team ID Normalization
- **Description**: Team ID is converted to ObjectId on create/update.
- **File**: `src/galaticos/handlers/players.clj` (lines 27–28)
- **Behavior**:
  - Automatic string-to-ObjectId conversion
  - Validates ObjectId format (returns 400 if invalid)

---

## Matches

### RN-MATCH-01: Required Fields
- **Description**: Matches must have `championship-id` and player statistics.
- **File**: `src/galaticos/handlers/matches.clj` (lines 13–14)
- **Required fields**:
  - `championship-id` (ObjectId): Championship ID
  - `player-statistics` (array): Player statistics (cannot be empty)

### RN-MATCH-02: Allowed Fields
- **Description**: Only specific fields are accepted when creating or updating matches.
- **File**: `src/galaticos/handlers/matches.clj` (lines 10–12)
- **Allowed fields**:
  - `championship-id`: Championship ID
  - `home-team-id`: Home team ID (our team)
  - `away-team-id`: Away team ID (optional; not used on single-team platform)
  - `date`: Match date
  - `location`: Location
  - `round`: Round
  - `status`: Status
  - `opponent`: Opponent team name (text)
  - `venue`: Venue
  - `result`: Result (text)
  - `away-score`: Opponent score (manual input — no opponent player statistics)
  - `player-statistics`: Array of player statistics
  - `notes`: Notes
- **Computed fields (read-only)**:
  - `home-score`: Computed automatically as the sum of goals in home-side `player-statistics`; not accepted on create/update.

### RN-MATCH-03: Player Statistics Structure
- **Description**: Each entry in `player-statistics` must follow a specific structure.
- **File**: `src/galaticos/handlers/matches.clj` (lines 16–21)
- **Required fields in statistics**:
  - `player-id` (ObjectId): Player ID
- **Allowed fields in statistics**:
  - `player-id`: Player ID
  - `player-name`: Player name
  - `position`: Position
  - `goals`: Goals scored
  - `assists`: Assists
  - `yellow-cards`: Yellow cards
  - `red-cards`: Red cards
  - `minutes-played`: Minutes played

### RN-MATCH-04: Statistics Array Validation
- **Description**: `player-statistics` must be a non-empty array of objects.
- **File**: `src/galaticos/handlers/matches.clj` (lines 23–42)
- **Behavior**:
  - Returns 400 if not an array
  - Returns 400 if array is empty
  - Message: `"player-statistics must be a non-empty vector"`

### RN-MATCH-05: Automatic Statistics Recalculation on Create
- **Description**: When a match is created, player statistics are recalculated.
- **File**: `src/galaticos/handlers/matches.clj`; `src/galaticos/analytics/player_stats_jobs.clj`
- **Behavior**:
  - After insert, triggers `submit-incremental-recalc-after-match!` with `{:reason :after-match-create, :op :create, :match-id, :affected-player-ids}`; the worker applies incremental recompute by default (`update-incremental-player-stats!`) on a single-thread executor without blocking the response (unless `GALATICOS_PLAYER_STATS_SYNC=true`)
  - Updates `aggregated-stats` for affected players via `merge-aggregated-stats` in `galaticos.db.aggregations` (**hybrid model**): historical spreadsheet/seed stats live in `:pre-match-stats` (per championship) or `:pre-match-total` (orphan totals); displayed games/goals/assists = baseline + match rollup. Championships without matches keep the baseline; titles always come from the table, never from matches. `GALATICOS_PLAYER_STATS_FORCE_FULL=true` forces full recompute
  - **Operations:** retries with limit and backoff (`GALATICOS_PLAYER_STATS_MAX_ATTEMPTS`, `GALATICOS_PLAYER_STATS_RETRY_BACKOFF_MS`); last successful job stored in MongoDB (`player_stats_job_meta` via `player-stats-job-store`); authenticated read at `GET /api/aggregations/player-stats-jobs` (see [architecture.md — Player stats aggregate jobs](../analytics/architecture.md#jobs-de-agregados-player-stats))

### RN-MATCH-06: Automatic Statistics Recalculation on Update
- **Description**: When a match is updated, player statistics are recalculated.
- **File**: `src/galaticos/handlers/matches.clj`; `src/galaticos/analytics/player_stats_jobs.clj`
- **Behavior**:
  - After update, triggers `submit-incremental-recalc-after-match!` with union of players from existing document and request body, reason `:after-match-update` (async by default, same as RN-MATCH-05)
  - Ensures consistency via scheduled incremental recompute (not in-request); retry, store, and GET behave as in RN-MATCH-05

### RN-MATCH-07: Statistics Recalculation on Delete
- **Description**: When a match is deleted, statistics for players who participated are recalculated.
- **File**: `src/galaticos/handlers/matches.clj`; `src/galaticos/analytics/player_stats_jobs.clj`
- **Behavior**:
  - After removal, triggers `submit-incremental-recalc-after-match!` with reason `:after-match-delete` and players from the match’s `player-statistics` (before delete)
  - Recalculates aggregates incrementally over the current match set; job failure does not undo deletion; retry, store, and GET as in RN-MATCH-05

### RN-MATCH-08: Filter by Championship
- **Description**: Match listing can be filtered by championship.
- **File**: `src/galaticos/handlers/matches.clj` (lines 79–89)
- **Behavior**:
  - Query param `championship-id` filters results
  - Results sorted by date (descending)

### RN-MATCH-09: Block New Match in Completed Season
- **Description**: New matches can only be created in seasons with status `active`.
- **File**: `src/galaticos/handlers/matches.clj`; `src/galaticos/db/seasons.clj`; `src-cljs/galaticos/components/matches.cljs`
- **Behavior**:
  - `POST /api/matches` resolves target season via explicit `:season-id` or `find-active-by-championship`
  - No active season → `400` (`No active season for this championship`)
  - Non-active season (e.g. `completed`, `inactive`) → `403` (`Cannot create matches in a completed season`)
  - Update and delete of existing matches are **not** blocked by season status
  - UI hides “New Match” when no active season and disables the create form

---

## Statistics and Aggregations

### RN-STATS-01: Aggregation by Championship
- **Description**: The system computes player statistics per championship.
- **Implementation**: `player-stats-by-championship` in `src/galaticos/db/aggregations.clj`
- **Metrics computed**:
  - `games`: Matches played
  - `goals`: Total goals
  - `assists`: Total assists
  - `yellow-cards`: Total yellow cards
  - `red-cards`: Total red cards
  - `goals-per-game`: Goals per match average
  - `assists-per-game`: Assists per match average

### RN-STATS-02: Average Goals by Position
- **Description**: The system computes average goals by position in a championship.
- **Implementation**: `avg-goals-by-position` in `src/galaticos/db/aggregations.clj`
- **Metrics computed**:
  - `avg-goals`: Position average goals
  - `total-goals`: Position total goals
  - `total-assists`: Position total assists
  - `player-count`: Number of player entries
  - `unique-games`: Unique match count

### RN-STATS-03: Player Performance Evolution
- **Description**: The system tracks temporal performance evolution for players.
- **Implementation**: `player-performance-evolution` in `src/galaticos/db/aggregations.clj`
- **Behavior**:
  - Aggregates by year, month, and week
  - Computes metrics per time period
  - Returns chronologically ordered time series

### RN-STATS-04: Statistics Update Pipeline
- **Description**: The system uses MongoDB aggregation pipelines to compute statistics.
- **Implementation**: `update-aggregated-stats-pipeline`, `update-aggregated-stats-pipeline-vec` in `src/galaticos/db/aggregations.clj`
- **Behavior**:
  - Unwinds player statistics from matches
  - Groups by player and championship
  - Lookups championship information
  - Computes overall and per-championship totals from match rollups
  - Structure: `{total: {...}, by-championship: [...], pre-match-total?: {...}}`; each `by-championship` row may include `:pre-match-stats` (historical baseline) summed with match rollup in `merge-aggregated-stats`

### RN-STATS-05: Update All Players’ Statistics
- **Description**: The system can recalculate statistics for all players.
- **Implementation**: `update-all-player-stats` in `src/galaticos/db/aggregations.clj`
- **Behavior**:
  - Runs full aggregation pipeline
  - Updates each player’s `aggregated-stats`
  - Updates `updated-at` timestamp
  - Returns count of players updated

### RN-STATS-06: Update Statistics per Match
- **Description**: The system can recalculate statistics only for players in a match.
- **Implementation**: `update-player-stats-for-match`, `update-incremental-player-stats!` in `src/galaticos/db/aggregations.clj`
- **Behavior**:
  - Identifies players involved in the match
  - Runs aggregation pipeline filtered to those players
  - Updates only match players, preserving `:pre-match-stats` / `:pre-match-total` and summing match rollups (does not replace historical baseline)
  - Players with no remaining matches but historical baseline revert to baseline-only (not zeroed)
  - Optimization to avoid full recalculation

### RN-STATS-07: Advanced Player Search
- **Description**: The system supports player search with multiple filters.
- **Implementation**: `search-players` in `src/galaticos/db/aggregations.clj` (text normalization via `galaticos.util.string`)
- **Available filters**:
  - `position`: Player position
  - `min-games`: Minimum matches played
  - `min-goals`: Minimum goals
  - `min-age`: Minimum age
  - `max-age`: Maximum age
  - `sort-by`: Sort field
  - `sort-order`: Sort order (ascending/descending)
  - `limit`: Result limit

### RN-STATS-08: Championship Comparison
- **Description**: The system compares aggregated statistics across championships.
- **Implementation**: `championship-comparison` in `src/galaticos/db/aggregations.clj`
- **Behavior**:
  - Aggregates from matches when data exists
  - Falls back to aggregation from players’ `aggregated-stats`
  - Computes comparative metrics across championships
- **Metrics computed**:
  - `championship-name`: Championship name
  - `championship-format`: Championship format
  - `matches-count`: Match count
  - `players-count`: Unique player count
  - `total-goals`: Total goals
  - `total-assists`: Total assists
  - `avg-goals-per-match`: Average goals per match

### RN-STATS-09: Top Players by Metric
- **Description**: The system returns player rankings by a specific metric.
- **Implementation**: `top-players-by-metric`, `championship-table-leaderboards` in `src/galaticos/db/aggregations.clj`
- **Behavior**:
  - Filters active players with `aggregated-stats`
  - Can filter by specific championship
  - Sorts by requested metric (descending)
  - Limits result count
- **Supported metrics**:
  - `goals`: Goals scored
  - `assists`: Assists
  - `games`: Matches played
  - `titles`: Titles won

### RN-STATS-10: Data Integrity Validation
- **Description**: The system validates data integrity before aggregations.
- **Implementation**: `validate-data-integrity` (private) and handlers in `src/galaticos/handlers/aggregations.clj`
- **Checks**:
  - Matches referencing non-existent championships
  - Matches without `player-statistics`
  - Matches with empty `player-statistics`
- **Behavior**:
  - Logs warnings for issues found
  - Does not abort operation (continues despite problems)

---

## Data Validation

### RN-VALID-01: Required Field Validation
- **Description**: All handlers validate presence of required fields.
- **Files**: `src/galaticos/handlers/*.clj`
- **Behavior**:
  - Returns 400 if required fields are missing
  - Message: `"Missing required fields: <field list>"`

### RN-VALID-02: Unknown Field Validation
- **Description**: Fields not listed as allowed are rejected.
- **Files**: `src/galaticos/handlers/*.clj`
- **Behavior**:
  - Returns 400 if unknown fields are sent
  - Message: `"Unknown fields: <field list>"`
  - Prevents database pollution

### RN-VALID-03: ObjectId Validation
- **Description**: ID strings are validated and converted to ObjectId.
- **File**: `src/galaticos/util/response.clj`
- **Behavior**:
  - Attempts string-to-ObjectId conversion
  - Returns 400 if format is invalid
  - Message: `"Invalid ID format"`

### RN-VALID-04: Request Body Validation
- **Description**: Request body must be a valid JSON object.
- **Files**: `src/galaticos/handlers/*.clj`
- **Behavior**:
  - Verifies body is a map
  - Returns 400 if not: `"Invalid request body"`

### RN-VALID-05: Partial Update Validation
- **Description**: Updates do not require all mandatory fields.
- **Files**: `src/galaticos/handlers/*.clj`
- **Behavior**:
  - `validate-*-body` accepts flag `require-required?`
  - On updates, `require-required?` is `false`
  - Allows partial entity updates

---

## Referential Integrity

### RN-REF-01: Championship Cannot Be Deleted With Matches
- **Description**: The system prevents deleting championships that have matches.
- **File**: `src/galaticos/handlers/championships.clj` (lines 96–97)
- **Behavior**:
  - Checks via `has-matches?`
  - Returns 409 Conflict if matches exist
  - User must delete/reassign matches first

### RN-REF-02: Team Cannot Be Deleted With Players
- **Description**: The system prevents deleting teams that have active players.
- **File**: `src/galaticos/handlers/teams.clj` (lines 96–97)
- **Behavior**:
  - Checks via `has-players?`
  - Returns 409 Conflict if players exist
  - User must remove players from the team first

### RN-REF-03: Players Are Soft Deleted
- **Description**: Players are not physically removed to preserve references.
- **File**: `src/galaticos/db/players.clj` (lines 79–82)
- **Behavior**:
  - Sets `active: false` instead of deleting
  - Preserves references in historical matches
  - Maintains historical data integrity

### RN-REF-04: Existence Check Before Update
- **Description**: The system verifies the entity exists before update.
- **Files**: `src/galaticos/handlers/*.clj`
- **Behavior**:
  - Calls `exists?` before `update-by-id`
  - Returns 404 Not Found if missing
  - Prevents accidental create via update

### RN-REF-05: Existence Check Before Delete
- **Description**: The system verifies the entity exists before delete.
- **Files**: `src/galaticos/handlers/*.clj`
- **Behavior**:
  - Calls `exists?` before `delete-by-id`
  - Returns 404 Not Found if missing
  - Idempotent (deleting non-existent returns 404)

---

## Response Formatting

### RN-RESP-01: Standard Response Format
- **Description**: All responses follow a standard format with `success` and `data`/`error`.
- **File**: `src/galaticos/util/response.clj`
- **Success format**:
  ```json
  {
    "success": true,
    "data": <any>
  }
  ```
- **Error format**:
  ```json
  {
    "success": false,
    "error": "error message"
  }
  ```

### RN-RESP-02: Standard HTTP Status Codes
- **Description**: The system uses semantic HTTP status codes.
- **File**: `src/galaticos/util/response.clj`
- **Codes used**:
  - `200 OK`: Successful read or update
  - `201 Created`: Successful create
  - `400 Bad Request`: Validation failed, invalid JSON, invalid ID
  - `401 Unauthorized`: Not authenticated
  - `403 Forbidden`: Not permitted
  - `404 Not Found`: Resource not found
  - `409 Conflict`: Referential integrity conflict
  - `500 Internal Server Error`: Internal server error

### RN-RESP-03: ObjectId Serialization
- **Description**: MongoDB ObjectIds are serialized as strings.
- **File**: `src/galaticos/util/response.clj`
- **Behavior**:
  - ObjectId converted to hexadecimal string
  - Format: 24 hexadecimal characters
  - Example: `"65a1b2c3d4e5f6a7b8c9d0e1"`

### RN-RESP-04: Date Serialization
- **Description**: Java dates are serialized as ISO-8601.
- **File**: `src/galaticos/util/response.clj`
- **Behavior**:
  - `java.util.Date` converted to ISO_INSTANT string
  - Format: `"2024-01-29T10:30:00.000Z"`
  - Timezone: UTC

### RN-RESP-05: Exception Handling
- **Description**: Exceptions are caught and converted to standard responses.
- **Files**: `src/galaticos/handlers/*.clj`
- **Behavior**:
  - Exceptions with status 400 return validation error
  - Other exceptions are logged and return 500
  - User-friendly message
  - Full stack trace in logs

---

## Timestamp Rules

### RN-TIME-01: Automatic Created At
- **Description**: All entities receive a creation timestamp.
- **Files**: `src/galaticos/db/*.clj`
- **Behavior**:
  - Field `created-at` set to current `java.util.Date()`
  - Set only on create
  - Never modified afterward

### RN-TIME-02: Automatic Updated At
- **Description**: All entities receive a last-update timestamp.
- **Files**: `src/galaticos/db/*.clj`
- **Behavior**:
  - Field `updated-at` set to current `java.util.Date()`
  - Set on create
  - Updated on every modification (update)

---

## Query Rules

### RN-QUERY-01: Match Ordering by Date
- **Description**: Matches for a championship are returned ordered by date (most recent first).
- **Implementation**: `find-by-championship` and related functions in `src/galaticos/db/matches.clj` (`sort-by` on `:date`, most recent first).
- **Behavior**:
  - Descending order by `date`
  - Applied automatically in `find-by-championship`

### RN-QUERY-02: Statistics Ordering by Metric
- **Description**: Statistics are ordered by the relevant metric.
- **Implementation** (`src/galaticos/db/aggregations.clj`): `:$sort` stages in pipelines — e.g. `search-players` (`sort-by` / `sort-order`), `top-players-by-metric`, `championship-table-leaderboards`, and ordering in queries such as `player-stats-by-championship` / `avg-goals-by-position`. Helpers at the top of the file (e.g. `agg-entity-id-str`) do not define result ordering.
- **Behavior**:
  - General rule: descending order for performance metrics (highest values first)
  - Secondary sort when applicable (e.g. goals then assists)

---

## Initialization Rules

### RN-INIT-01: Zeroed Statistics Structure
- **Description**: Players are created with initialized statistics structure.
- **File**: `src/galaticos/db/players.clj` (lines 24–25)
- **Initial structure**:
  ```clojure
  {:aggregated-stats 
    {:total {:games 0 :goals 0 :assists 0 :titles 0}
     :by-championship []}}
  ```

### RN-INIT-02: Empty Player List on Teams
- **Description**: Teams are created with an empty player list.
- **File**: `src/galaticos/db/teams.clj` (line 18)
- **Behavior**:
  - Field `active-player-ids` initialized as `[]`
  - Populated via add-player operations

---

## Final Notes

### Data Consistency

The system implements several strategies to ensure consistency:

1. **Rigorous validation**: All fields are validated before persistence
2. **Soft delete**: Referenced entities are not physically removed
3. **Referential integrity**: Cascade deletes are blocked
4. **Automatic recalculation**: Statistics are recalculated automatically
5. **Timestamps**: All modifications are tracked
6. **Implicit transactions**: MongoDB ensures atomicity for individual operations

### Maintainability

The code follows patterns that ease maintenance:

1. **Centralized validation**: `validate-*-body` functions in each handler
2. **Separation of concerns**: Handlers, DB, and util are separated
3. **Structured logging**: Errors are logged with adequate context
4. **Clear messages**: Errors have descriptive messages for users
5. **Documentation**: Docstrings on all public functions

### Extensibility

The system is prepared for extensions:

1. **Optional fields**: Many fields are optional for flexibility
2. **Dynamic filters**: Queries support multiple filters
3. **Aggregation pipeline**: MongoDB pipelines allow complex analysis
4. **Soft delete**: Historical data is preserved
5. **Flexible statistics**: The aggregation system is extensible

---

## Analytics Alignment

For sports data analytics evolution, this document holds operational and functional domain rules, while metric semantics and contracts should be centralized in the `docs/reference/analytics` track.

### Source of truth for metrics

- Metric definitions, formulas, and granularity: [metrics-catalog.md](../analytics/metrics-catalog.md).
- Predictive/derived insights in the API: `player_insights_response` contract in [data-contracts.md](../analytics/data-contracts.md).
- This file should reference metrics and avoid duplicating formulas.

### Source of truth for data contracts

- Versioned analytics data structures: [data-contracts.md](../analytics/data-contracts.md) (includes CSV export `include-derived` and insights response).
- Changes to payload/document shape require coordinated updates to rules and contracts.

### Document consistency governance

When a rule affects analytics calculation:

1. Update the rule in this document.
2. Update catalog/contract in the `docs/reference/analytics` track.
3. Add regression coverage in `docs/reference/domain/testing-coverage.md`.
4. Record operational validation in [reconciliation-runbook.md](../analytics/reconciliation-runbook.md).

### Recalculation jobs (player stats)

- The same module `galaticos.analytics.player-stats-jobs` applies to match rules (RN-MATCH-05/06/07) with controlled retry, persistence of last-success metadata, and a query endpoint; details in [architecture.md — Player stats aggregate jobs](../analytics/architecture.md#jobs-de-agregados-player-stats).

---

## Appendix: Business Rules Manifesto model

This appendix structures a subset of Galáticos business rules following the Business Rules Group mantra:

- **Terms** (business vocabulary)
- **Facts** (assertions about those terms)
- **Rules** (constraints or derivations based on those facts)

Focus areas from the original requirements:

- CRUD for players, teams, championships, and matches
- Championship seasons (active and past)
- Championship finalization and title increments
- Viewing players and per-match statistics
- Dashboard navigation
- Player search
- Automatic enrollment via spreadsheets (championships and players, including goalkeepers)

### Business vocabulary (terms)

- **Player**: Person registered in the system who can participate in matches and championships, with attributes such as name, position, statistics, and titles.
- **Team**: Entity representing a club or squad; players may be associated with it.
- **Championship**: Competition comprising one or more seasons and a set of matches and enrolled players.
- **Championship season**: Period (e.g. year or year/year) associated with a championship; may be active or past.
- **Active season**: Current season of a championship where matches and statistics are being recorded.
- **Past season**: Historically completed season of a championship, preserved for lookup and statistics.
- **Match**: Individual sporting event associated with a championship, with date, opponent, result, and player statistics.
- **Player match statistics**: Record of a player’s events/participation in a match (goals, assists, cards, minutes, etc.).
- **Title**: Achievement attributed to players when their club is declared champion in a specific championship/finalization.
- **Dashboard**: Main overview screen with indicators (cards, lists, shortcuts) for navigation to CRUD and query screens.
- **Championship spreadsheet**: External source (file) with championship data and player enrollments to import into the system.
- **Player spreadsheet**: External source (file) with player data (name, position, etc.) to import into the system.
- **Goalkeeper**: Player whose primary position is goalkeeper; may be inferred from markers such as `"GK"` in the player spreadsheet.

### Structural domain facts

- **F-01**: A championship has one or more seasons identified by a label (e.g. `"2023/2024"`).
- **F-02**: For each championship there is at most one active season at a given time.
- **F-03**: A championship may have zero or more associated past (historical) seasons.
- **F-04**: A match belongs to exactly one championship.
- **F-05**: Each match records statistics for one or more players.
- **F-06**: A player may be enrolled in zero or more championships.
- **F-07**: A team may have zero or more associated active players.
- **F-08**: Players who participate in a championship’s matches (via statistics) are considered participants in that championship.
- **F-09**: Each player accumulates aggregated statistics per championship and for their overall career on the platform.
- **F-10**: The dashboard presents aggregate indicators on players, teams, championships, and matches with links to detail screens.

### Business rules derived from requirements

Each rule below is classified per the Manifesto:

- **Structural (E)**: Constraint on structure/data.
- **Action (A)**: Rules that control or command behavior.
- **Derivation (D)**: Rules that compute or derive new facts from existing facts.

#### Group 1 – Entity CRUD

- **BRM-01 (E/A – Player CRUD)**  
  - **Description**: The system must allow creating, reading, updating, and deactivating players, requiring name and position, and preserving historical references (soft delete).  
  - **Relation to existing rules**: Aligned with `RN-PLAYER-01` through `RN-PLAYER-04`, `RN-INIT-01`, `RN-STATS-05`.

- **BRM-02 (E/A – Team CRUD)**  
  - **Description**: The system must allow creating, reading, updating, and deleting teams, respecting the constraint that teams with active players cannot be deleted.  
  - **Relation to existing rules**: Aligned with `RN-TEAM-01` through `RN-TEAM-07`, `RN-REF-02`, `RN-INIT-02`.

- **BRM-03 (E/A – Championship CRUD)**  
  - **Description**: The system must allow creating, reading, updating, and deleting championships, requiring mandatory fields (name, season, titles) and preventing deletion when associated matches exist.  
  - **Relation to existing rules**: Aligned with `RN-CHAMP-01` through `RN-CHAMP-06`, `RN-REF-01`.

- **BRM-04 (E/A – Match CRUD)**  
  - **Description**: The system must allow creating, reading, updating, and deleting matches, requiring a championship link and at least one player statistic, ensuring consistency of aggregated statistics on any match CRUD operation.  
  - **Relation to existing rules**: Aligned with `RN-MATCH-01` through `RN-MATCH-08`, `RN-STATS-01` through `RN-STATS-07`.

#### Group 2 – Championship seasons

- **BRM-05 (E – Active season per championship)**  
  - **Description**: Each championship must have at most one active season; season changes must preserve prior seasons as past seasons.  
  - **Type**: Structural.  
  - **Implementation**: MongoDB `seasons` collection with `championship-id`, `status` (`active` / `inactive` / etc.), uniqueness on `(championship-id, season)`, and `activate!` which deactivates others. Root `championships` document enriches responses with active season (`active-season-id`, denormalized fields). Root `season` field remains for compatibility/aggregate reads.

- **BRM-06 (A – Season management)**  
  - **Description**: An administrator must be able to add new seasons to a championship and select which season is active, with the active season highlighted on the championship detail screen together with the championship name.  
  - **Type**: Action.

#### Group 3 – Championship finalization and titles

- **BRM-07 (A – Championship finalization)**  
  - **Description**: An administrator must be able to finalize a championship, setting its status to completed and recording the finalization date. A championship can only be finalized once.  
  - **Type**: Action.  
  - **Relation to existing rules**: Finalization in `handlers/championships.clj` (`finalize-championship`) and `handlers/seasons.clj` (`finalize-season`). See [RN-PEND mapping](#rn-pend-mapping) (alias **RN-PEND-03**).

- **BRM-08 (A/D – Title increment)**  
  - **Description**: When finalizing a championship, player titles are incremented only if the administrator explicitly indicates the club was champion. In that case, all players are treated as winners.
  - **Type**: Action + Derivation (derives new title counts from winners and `titles-award-count`).  
  - **Relation to existing rules**: Same flow as **RN-PEND-03** — `titles-award-count` and `winner-player-ids`; see [RN-PEND mapping](#rn-pend-mapping).

- **BRM-09 (E – Participation and team membership)**  
  - **Description**: All players who participate in a championship via matches (with recorded statistics) must belong to a valid team and be enrolled in the championship, keeping enrollment, team, and match participation consistent.  
  - **Type**: Structural.  
  - **Relation to existing rules**: `validate-player-team-coherence` and `validate-players-enrolled` in `handlers/matches.clj`. See [RN-PEND mapping](#rn-pend-mapping) (alias **RN-PEND-04**).

#### Group 4 – Matches and statistics

- **BRM-10 (A – View match players)**  
  - **Description**: When viewing a match, the administrator must see all championship players who participated in that match, with their statistics (assists, goals, cards, minutes, etc.).  
  - **Type**: Action (UI/query oriented).  
  - **Relation to existing rules**: Aligned with `RN-MATCH-03` through `RN-MATCH-07` and match/statistics query endpoints.

- **BRM-11 (A/D – Record match statistics)**  
  - **Description**: On the match screen, the administrator must be able to record or edit each player’s statistics (goals, assists, participation), triggering recalculation of the player’s aggregated statistics and automatic match score computation.  
  - **Type**: Action + Derivation.  
  - **Relation to existing rules**: Connects `RN-MATCH-05/06/07` with `RN-STATS-01` through `RN-STATS-07` and jobs in `galaticos.analytics.player-stats-jobs` — see [RN-PEND mapping](#rn-pend-mapping) (alias **RN-PEND-05**).

#### Group 5 – Dashboard navigation

- **BRM-12 (A – Dashboard item navigation)**  
  - **Description**: The administrator must be able to click each dashboard item (card, link, or shortcut) and be redirected to the corresponding screen (e.g. player list, team list, championships, matches, aggregate statistics).  
  - **Type**: Action (UX/navigation rule).  
  - **Card → route mapping** (implementation `src-cljs/galaticos/components/dashboard.cljs`): Players → `:players`; Matches (total) → `:matches`; Seasons → `:championships`; Teams → `:teams`. “Goals” cards use route `:players` (roster shortcut). Deferred tables (tops) link to player detail via link component.

#### Group 6 – Player search

- **BRM-13 (A – Player search when enrolling in championship)**  
  - **Description**: When adding players to a championship, the administrator must be able to search players by name (full or partial), supporting enrollment via autocomplete or filtered list.  
  - **Type**: Action.  
  - **Relation to existing rules**: `player_picker.cljs` (local filter by name/nickname). Enrollment persists in active season `enrolled-player-ids` — see [RN-PEND mapping](#rn-pend-mapping) (aliases **RN-PEND-01**, **RN-PEND-02**).

- **BRM-14 (A – Player search from dashboard)**  
  - **Description**: From the dashboard, the administrator must be able to search registered players; additional filters may be used on the roster screen.  
  - **Type**: Action.  
  - **Implemented scope**: On the dashboard, the “Search player…” field sends query `q` to route `:players`. On the **Players** page, `GET` with `q`, `position`, pagination, and sort uses `search-players` / **RN-STATS-07**. Global search filter by **team** is not on the dashboard (only on roster/team management where applicable).  
  - **Relation to existing rules**: **RN-STATS-07**; UI `dashboard.cljs`, `players.cljs`, `api.cljs`.

#### Group 7 – Spreadsheet import

- **BRM-15 (A – Automatic player enrollment on championship import)**  
  - **Description**: When importing championships from the championship spreadsheet, players listed for each championship must be automatically enrolled in that championship, respecting player-limit constraints and ID consistency.  
  - **Type**: Action.  
  - **Origin**: Import business rule (scripts `scripts/python/*.py` and/or future UI flows).

- **BRM-16 (D – Goalkeeper position from `"GK"` in name)**  
  - **Description**: When importing players from the player spreadsheet, if the player name contains `"GK"` (e.g. `"João Silva GK"`), the system must infer position as goalkeeper when no more specific position was set.  
  - **Type**: Derivation.  
  - **Origin**: Import logic in **`scripts/python/seed_mongodb.py`** (inference only when position is missing; explicit spreadsheet position wins). `scripts/python/read_excel.py` is an Excel inspection/read utility without this rule.

#### RN-PEND mapping

Aliases used in this appendix before consolidation into the `RN-*` catalog. There are no separate `### RN-PEND-*` entries; behavior lives in the modules below.

| Alias | Code location |
|-------|----------------|
| **RN-PEND-01**, **RN-PEND-02** | Enrollment: `enrolled-player-ids` on `seasons` documents (and legacy on root `championships`); UI `championships.cljs`, `player_picker.cljs`; championship/season handlers. |
| **RN-PEND-03** | `finalize-championship` (`handlers/championships.clj`), `finalize-season` (`handlers/seasons.clj`), `finalize!` (`db/seasons.clj`). |
| **RN-PEND-04** | `validate-players-enrolled`, `validate-player-team-coherence` (`handlers/matches.clj`). |
| **RN-PEND-05** | Recalculation: `galaticos.analytics.player-stats-jobs` + `db/aggregations.clj`. |

### Open questions and identified gaps

- **Q-01 – Season model**:  
  - **Resolved in implementation**: `seasons` collection linked to `championship-id`, single `active` status per championship (via `activate!`), unique `season` label per `(championship-id, season)` in `seasons-db/create`. Product documentation should use this model as reference.  
  - **Documentation gap**: align diagrams or external materials that still refer only to `season` on the `championships` document.

- **Q-02 – Reopen / undo championship finalization**:  
  - Requirements do not specify whether a finalized championship may be reopened or titles granted in error corrected.  
  - **Suggested additional rules**:  
    - Define whether a “reopen championship” operation exists, including rules to reverse titles already incremented.

- **Q-03 – Player search criteria**:  
  - **Current implementation**: API `search-players` uses `galaticos.util.string/normalize-text` on term `q`, case-insensitive regex on `name`/`nickname`, and `search-name` when present; pagination `page`/`limit`; configurable sort (`sort-by`, `sort-order`). BRM-13 (picker) filters in memory by name/nickname without mandatory accent-insensitive normalization.  
  - **Product suggestion**: document the above as the official rule or require picker/API unification.

- **Q-04 – Spreadsheet import conflicts**:  
  - **Policy applied in seed** (`seed_mongodb.py`): player identification by name; when existing, current spreadsheet may update `aggregated-stats.total` (see implementation and log messages), preserving `by-championship` and structural fields; `create_championship` avoids duplicate `name+season`.  
  - **Q-04 closed** (seed policy; see also [business-rules-audit.md](business-rules-audit.md)). Optional evolution: exportable conflict report, etc.

- **Q-05 – `"GK"` derivation when position already exists**:  
  - **Resolved in implementation** (`seed_mongodb.py`): explicit spreadsheet position wins; `"GK"` in name inferred only when position is missing or empty.

- **Q-06 – Dashboard navigation for new features**:  
  - **Base mapping** documented in **BRM-12**. New cards must update that table and `dashboard.cljs`.

### Completeness assessment

- **Coverage**: Rules BRM-01 through BRM-16 cover the main points from the original requirements (CRUD, seasons, finalization, statistics, dashboard, search, and spreadsheet import).  
- **Integration with existing rules**: Most of these rules are mapped and/or implemented in the `RN-*` sections above, especially championships, matches, statistics, and finalization.  
- **Gaps**: Multiple seasons are modeled in code; **business gaps** remain for reopening championships (Q-02) and possible extension of global search filters (BRM-14 partial). Import merge policy (Q-04) is closed in the seed per the checklist, subject to product evolution.  
- **Recommended next steps**: Validate open questions with the business team and, once decided, promote candidate rules to official rules (new `RN-*` identifiers) and align implementations (API, import scripts, and UI).

---

**Document generated from source-code analysis.**  
**Last updated: April 23, 2026**  
**For questions or suggestions, consult the source files referenced above.**
