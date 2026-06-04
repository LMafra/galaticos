# Development guide

Detailed setup, scripts, testing, and backend REPL examples for Galáticos. For a quick overview and minimal commands, see the [root README](../../README.md).

## Contents

- [Project layout](#project-layout)
- [Local setup](#local-setup)
- [Quick start (expanded)](#quick-start-expanded)
- [Scripts and Makefile](#scripts-and-makefile)
- [Validation](#validation)
- [Docker without host tools](#docker-without-host-tools)
- [Testing](#testing)
- [Replicate CI locally](#replicate-ci-locally)
- [Coverage](#coverage)
- [REPL and database API](#repl-and-database-api)
- [Conventions and schema](#conventions-and-schema)

## Project layout

```
galaticos/
├── bin/galaticos          # Main CLI entry point
├── config/docker/         # Docker Compose and Dockerfiles
├── data/raw/              # Source data (e.g. galaticos.xlsm for seed)
├── docs/                  # Documentation hub (docs/README.md)
├── resources/             # config.edn, templates, compiled JS output
├── scripts/               # Shell scripts invoked by bin/galaticos
├── src/galaticos/         # Clojure backend
│   ├── db/                # MongoDB (Monger)
│   ├── handlers/          # Request handlers
│   ├── middleware/        # Auth, CORS, errors, gzip
│   └── routes/            # Compojure routes
├── src-cljs/galaticos/    # ClojureScript UI (Reagent)
├── test/                  # Clojure tests
└── test-cljs/             # ClojureScript tests
```

Recommended backend namespaces under `src/galaticos/`: `db/`, `routes/`, `middleware/`, `handlers/`.

## Local setup

### 1. Install dependencies

```bash
clj -M:dev
```

For manual ClojureScript builds, run `npm ci` once (Node.js 18+).

### 2. MongoDB configuration

Edit `resources/config.edn`:

```clojure
{:dev {:database-url "mongodb://localhost:27017/galaticos"
       :database-name "galaticos"}}
```

You can also use a `.env` file based on `.env.example` when available.

### 3. Create indexes

```bash
./bin/galaticos db:setup
```

This checks MongoDB connectivity, creates collection indexes, and verifies they exist.

### 4. Seed the database (optional)

```bash
./bin/galaticos db:seed
```

Requires `data/raw/galaticos.xlsm`. The seed script:

- Creates the "Galáticos" team
- Imports players from the "Base de dados" sheet
- Creates championships from other sheets
- Updates per-championship aggregated player stats

Ensure MongoDB is running before seeding. For minimal deterministic data (E2E/smoke): `./bin/galaticos db:seed-smoke`.

### 5. ClojureScript compilation

The SPA uses Shadow-CLJS. `./bin/galaticos run` compiles the frontend automatically; output goes to `resources/public/js/compiled/`.

Manual builds:

```bash
# One-off dev build
clj -M:build:frontend dev

# Watch (hot reload)
npm run cljs:watch

# Production (advanced optimizations)
clj -M:build:frontend prod
```

## Quick start (expanded)

```bash
./bin/galaticos check-deps
./bin/galaticos docker:dev start
./bin/galaticos db:setup
./bin/galaticos db:seed          # optional
./bin/galaticos run
```

In another terminal:

```bash
./bin/galaticos validate
```

App URL: `http://localhost:3000`.

## Scripts and Makefile

All commands go through `./bin/galaticos`, which delegates to `scripts/`. Run `./bin/galaticos help` for the full list.

### Script layout

```
scripts/
  dev/         run, console, test, coverage, validate, watch-cljs
  e2e/         Playwright E2E and coverage:e2e
  database/    db:setup, db:seed, db:seed-smoke, check-stats
  docker/      docker:dev, docker:prod, validate:docker
  utils/       check-deps, clean, common.sh
  python/      Excel seed helpers
  mongodb/     index and aggregation JS helpers
```

### Common commands

```bash
./bin/galaticos run
./bin/galaticos console
./bin/galaticos test
./bin/galaticos db:setup
./bin/galaticos db:seed
./bin/galaticos docker:dev start
./bin/galaticos docker:dev stop
./bin/galaticos build
./bin/galaticos clean
./bin/galaticos check-deps
```

### Makefile (optional)

```bash
make help
make run
make test
make db:setup
make db:seed
make docker:dev CMD=start
make check-deps
```

### Running scripts directly

```bash
./scripts/dev/run.sh
./scripts/dev/test.sh
./scripts/database/setup.sh
./scripts/e2e/run.sh http://localhost:3000
```

## Validation

**Local** (`./bin/galaticos validate`):

- Server on port 3000
- `/health` responds
- `/` returns HTML with correct `Content-Type`
- Compiled JS is served (not downloaded as attachment)

**Docker** (`./bin/galaticos validate:docker` or `./bin/galaticos docker:dev validate`):

- Containers are up and healthy
- Same endpoint checks inside the stack
- Compiled JS present in the app container

The local validator detects Docker and adjusts messages when relevant.

## Docker without host tools

With `./bin/galaticos docker:dev start` running, database commands can run without Python or `mongosh` on the host:

| Command | Behavior when host tools are missing |
|---------|--------------------------------------|
| `db:setup` | Runs index creation inside the MongoDB container |
| `db:seed` | Runs seed in a temporary Python container |
| `db:seed-smoke` | Uses a temporary Clojure container if CLI is absent |
| `db:check-stats` | Uses MongoDB shell inside the container |

**Production:** `./bin/galaticos docker:prod start`. For backups, seed without reset, and indexes in production, see [production-runbook.md](operacao/production-runbook.md).

## Testing

```bash
# Unit tests (Clojure + ClojureScript)
./bin/galaticos test

# E2E (app must be running)
./bin/galaticos e2e
```

PR requirements (coverage thresholds, commit style): [CONTRIBUTING.md](../../CONTRIBUTING.md). Strategy and thresholds: [testing-coverage.md](domain/testing-coverage.md).

## Replicate CI locally

Run these before opening a PR:

### 1. Lint (clj-kondo)

```bash
# Install once (Linux amd64 example)
curl -sSLO "https://github.com/clj-kondo/clj-kondo/releases/download/v2024.08.01/clj-kondo-2024.08.01-linux-amd64.zip"
unzip -o clj-kondo-2024.08.01-linux-amd64.zip
sudo mv clj-kondo /usr/local/bin/
chmod +x /usr/local/bin/clj-kondo

clj-kondo --lint src src-cljs --fail-level error
```

### 2. Frontend compile

```bash
docker compose -f config/docker/docker-compose.dev.yml run --rm app \
  clj -M:frontend -m shadow.cljs.devtools.cli compile app
```

### 3. Unit tests

```bash
./bin/galaticos docker:dev start
./bin/galaticos test
./bin/galaticos docker:dev stop
```

### 4. E2E (Playwright)

```bash
./bin/galaticos docker:dev start
until curl -sf http://localhost:3000/health; do sleep 2; done
./bin/galaticos db:seed-smoke
npm ci --no-fund --no-audit
npx playwright install --with-deps chromium
./bin/galaticos e2e
./bin/galaticos docker:dev stop
```

Optional filter: `./bin/galaticos e2e http://localhost:3000 -- --grep @smoke`

### All-in-one (lint + frontend + unit)

```bash
clj-kondo --lint src src-cljs --fail-level error && \
./bin/galaticos docker:dev start && \
docker compose -f config/docker/docker-compose.dev.yml run --rm app \
  clj -M:frontend -m shadow.cljs.devtools.cli compile app && \
./bin/galaticos test && \
./bin/galaticos docker:dev stop
```

**Prerequisites:** Docker, Node.js 18+, clj-kondo for lint. Prefer `./bin/galaticos` over ad-hoc compose commands when possible.

## Coverage

Backend Cloverage uses `--fail-threshold` in `deps.edn` (currently **70**). CI fails when the **minimum** of line % and form % falls below the threshold (not the same as JS branch coverage).

```bash
./bin/galaticos coverage
./bin/galaticos coverage:e2e
./bin/galaticos coverage:all
```

Reports:

```bash
open target/coverage-report/index.html   # consolidated
open target/coverage/index.html          # backend only
```

GitHub Actions enforces backend coverage on pull requests. See [testing-coverage.md](domain/testing-coverage.md).

## REPL and database API

Examples for use in `./bin/galaticos console` or `clj -M:dev`.

### MongoDB connection

```clojure
(require '[galaticos.db.core :as db])
(db/connect!)
(db/db)
(db/disconnect!)
```

### Championships

```clojure
(require '[galaticos.db.championships :as ch])
(ch/create {:name "Boleiro fut7" :season "2025" :format "society-7" :status "active"})
(ch/find-by-id "507f1f77bcf86cd799439011")
(ch/find-active)
```

### Players

```clojure
(require '[galaticos.db.players :as players])
(players/create {:name "Gabriel Leal" :nickname "Leal" :position "Atacante"
                 :team-id "507f1f77bcf86cd799439015"})
(players/find-by-position "Atacante")
(players/find-active)
```

### Matches

```clojure
(require '[galaticos.db.matches :as matches])
(matches/create {:championship-id "507f1f77bcf86cd799439011"
                 :date (java.util.Date.)
                 :opponent "Opponent"}
                [{:player-id "507f1f77bcf86cd799439012"
                  :player-name "Gabriel Leal"
                  :position "Atacante"
                  :goals 2
                  :assists 1}])
```

### Aggregations

```clojure
(require '[galaticos.db.aggregations :as agg])
(agg/player-stats-by-championship "507f1f77bcf86cd799439011")
(agg/avg-goals-by-position "507f1f77bcf86cd799439011")
(agg/player-performance-evolution "507f1f77bcf86cd799439012")
(agg/search-players {:position "Atacante" :min-games 10 :sort-by :goals-per-game :limit 20})
(agg/championship-comparison)
(agg/top-players-by-metric :goals 10)
(agg/update-all-player-stats)
(agg/update-player-stats-for-match "match-id")
```

## Conventions and schema

- **Naming:** kebab-case for fields and collections
- **Dates:** ISODate in MongoDB, `java.util.Date` in Clojure
- **IDs:** MongoDB ObjectId
- **Stats:** embedded in `matches`, cached in `players.aggregated-stats`

Indexes are created by `./bin/galaticos db:setup`. Main indexes:

- `championships`: name + season (unique), status, dates
- `players`: name, team-id + active, position, aggregated-stats
- `matches`: championship-id + date, date, player-statistics.player-id
- `teams`: name (unique)
- `admins`: username (unique)

Full schema, JSON examples, and embedding vs referencing: [mongodb-schema.md](domain/mongodb-schema.md).
