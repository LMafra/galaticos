# Galáticos

Sports roster management with an analytics dashboard — JSON API (Ring/Compojure) and Reagent SPA (ClojureScript).

## About

Galáticos helps manage players, championships, and matches for a sports squad, with aggregated stats and dashboard views. A single admin authenticates via JWT.

**Production:** [https://www.galaticosfr.com.br](https://www.galaticosfr.com.br) · **Local dev:** http://localhost:3000

## Features

- Player roster and team management
- Championships and match recording with per-player stats
- Aggregated statistics and analytics-oriented queries
- Dashboard UI (ClojureScript + Tailwind)
- Optional seed from Excel (`data/raw/galaticos.xlsm`)
- Docker-based dev stack and unified CLI (`./bin/galaticos`)

## Tech stack

| Layer | Technologies |
|-------|----------------|
| Backend | Clojure 1.11, Ring, Compojure, Jetty, Buddy JWT |
| Database | MongoDB via Monger 3.x |
| Frontend | ClojureScript, shadow-cljs, Reagent, reitit-frontend |
| Tooling | tools.deps, Playwright (E2E), clj-kondo |

See [deps.edn](deps.edn) for exact versions.

## Prerequisites

- JDK and [Clojure CLI](https://clojure.org/guides/install_clojure)
- [Docker](https://docs.docker.com/get-docker/) (recommended for MongoDB and CI-like runs)
- Node.js 18+ and `npm ci` if you compile ClojureScript outside `./bin/galaticos run`

## Quick start

```bash
./bin/galaticos check-deps
./bin/galaticos docker:dev start
./bin/galaticos db:setup
./bin/galaticos run              # optional: ./bin/galaticos db:seed
```

Open http://localhost:3000. In another terminal: `./bin/galaticos validate`.

## Configuration

MongoDB URL and database name live in [resources/config.edn](resources/config.edn):

```clojure
{:dev {:database-url "mongodb://localhost:27017/galaticos"
       :database-name "galaticos"}}
```

Seed data expects `data/raw/galaticos.xlsm`. Production deploy, backup, and seed without data loss: [production-runbook.md](docs/reference/operations/production-runbook.md).

## Development

| Command | Purpose |
|---------|---------|
| `./bin/galaticos run` | Start app (compiles CLJS) |
| `./bin/galaticos test` | Unit tests (Clojure + CLJS) |
| `./bin/galaticos validate` | Smoke-check local server |
| `./bin/galaticos console` | Clojure REPL |
| `./bin/galaticos build` | Uberjar |

Full command list: `./bin/galaticos help`. Setup, scripts, CI locally, coverage, and REPL examples: **[Development guide](docs/reference/development.md)**.

## Documentation

- [Documentation hub](docs/README.md) — map by audience (domain, engineering, operations, analytics)
- [Concepts](docs/concepts.md) — canonical vocabulary for humans and LLMs
- [llms.txt](llms.txt) — agent index (repository root)
- [Business rules](docs/reference/domain/business-rules.md)
- [MongoDB schema](docs/reference/domain/mongodb-schema.md)
- [Testing and coverage](docs/reference/domain/testing-coverage.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for pull request guidelines, commit conventions, and coverage requirements.

## License

MIT — see [LICENSE](LICENSE).
