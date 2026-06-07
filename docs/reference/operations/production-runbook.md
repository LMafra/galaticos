# Production runbook — data preservation

**Summary:** This runbook describes how to deploy and maintain Galáticos in production without losing MongoDB data. Application containers are replaceable; data lives in the named Docker volume `mongodb-data-prod`. Use the safe commands below for build, start, and redeploy; never run `docker compose down -v` or remove the data volume in production. For backups, indexes, seeding, and aligning derived player stats with matches, follow the sections in this document and the linked VPS and reconciliation guides.

For typical operations on a **VPS with an external domain** (SSH, `.env` location, Nginx, Clojars timeouts during `docker build`, seed with `MONGO_URI`), see [vps-hosting.md](vps-hosting.md). For a **synthetic timeline** of a real incident (May 2026) in that environment, see [incident-deploy-vps-frontend-2026-05.md](incident-deploy-vps-frontend-2026-05.md).

## Before you start

- You need SSH access to the production host and the production `.env` (never commit secrets).
- Confirm the MongoDB volume name is `mongodb-data-prod` before any `docker compose` command.
- Take a backup (`db:backup`) before seed with `--reset` or any destructive data operation.

## Principles

- Data persists in the named Docker volume **`mongodb-data-prod`** ([config/docker/docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml)).
- The **`app`** container is replaceable: rebuild/restart **does not** wipe the database, as long as the MongoDB volume is kept.
- The file [config/database/init-indexes.js](../../../config/database/init-indexes.js) runs **only on the first startup** of MongoDB with an empty data directory. Existing databases need indexes applied using the flow documented below. Keep it aligned with [scripts/mongodb/mongodb-indexes.js](../../../scripts/mongodb/mongodb-indexes.js).

### Baseline: `data/raw/galaticos.xlsm` vs data in MongoDB

- The file **`data/raw/galaticos.xlsm`** (or the path in **`EXCEL_FILE`**) is the project’s documented **baseline**: the official seed materializes the Excel file into the database ([`scripts/python/seed_mongodb.py`](../../../scripts/python/seed_mongodb.py), [`scripts/database/seed.sh`](../../../scripts/database/seed.sh)).
- **After** loading the Excel file, the **source of truth in production** for matches and **per-match** statistics is the **`matches`** collection (and `player-statistics` within each document). The `aggregated-stats` field on **`players`** is **derived** (read cache) and must stay aligned with matches after [reconciliation](../analytics/reconciliation-runbook.md) (`POST /api/aggregations/reconcile`, admin).
- **Validate** odd totals: review or export matches for the championship/player; fix or delete the affected match in the app; then **reconcile** to recalculate aggregates.
- **Clear** incorrect data: fix **at the source** (match / stat line) — delete the duplicate match or adjust goals/assists — and reconcile. Deleting local Excel files alone is not enough: production reads MongoDB.
- **Return to Excel-only state** (discard **all** changes and matches made after the last import): requires a maintenance window, **backup** (`db:backup`), and seed with `--reset` (and `ALLOW_DESTRUCTIVE_SEED=1` if `GALATICOS_ENV=production`). This removes data not present in the Excel file; treat as a deliberate operation.

## Deploy and container lifecycle

### Safe commands (preserve the volume)

```bash
./bin/galaticos docker:prod build
./bin/galaticos docker:prod start
./bin/galaticos docker:prod restart   # reinicia containers; não reconstrói imagem (código novo exige build)
./bin/galaticos docker:prod stop   # usa `docker compose down` sem `-v`
```

### Never in production

- `docker compose ... down -v` (or `docker volume rm` on the data volume): **wipes the database**.
- Recreating the stack with explicit volume removal.

To update only the application after code changes:

```bash
./bin/galaticos docker:prod deploy
```

(This runs `docker build --network host` on `Dockerfile.prod` and recreates the `app` service; MongoDB and the `mongodb-data-prod` volume remain. On a VPS with Clojars timeouts, **do not** replace this with `docker compose … build` without host networking end to end — see [vps-hosting.md §4](vps-hosting.md).)

Equivalent Compose alternative (may fail on the VPS if the build does not use host networking throughout):

```bash
docker compose -f config/docker/docker-compose.prod.yml up -d --build app
```

## Seed and data scripts

### Official seed (Excel)

- **Idempotent** mode (recommended to complement data without a wipe):

  ```bash
  export MONGO_URI='mongodb://USER:PASSWORD@HOST:27017/galaticos?authSource=admin'
  export DB_NAME=galaticos
  export GALATICOS_ENV=production   # recomendado ao apontar para produção
  ./bin/galaticos db:seed
  ```

- **Do not** use `--reset` in production except with an explicit decision, backup, and maintenance window. With `GALATICOS_ENV=production`, the Python script requires `ALLOW_DESTRUCTIVE_SEED=1` to allow `--reset` (see [scripts/python/seed_mongodb.py](../../../scripts/python/seed_mongodb.py)).

### Smoke seed (E2E)

- **Do not** run `./bin/galaticos db:seed-smoke` against the same database (`DB_NAME`) as production. It mixes test data with real data, and the official seed may refuse to run without `--reset`.
- In **dev**, the official seed (`scripts/python/seed_mongodb.py`, optional `--reset`) loads `data/BASE_DADOS.csv` and the Base spreadsheet. Smoke (`db:seed-smoke`) creates a minimal set (“Smoke Championship”, etc.). Running both on the **same** MongoDB **without** clearing collections **accumulates** records (the Python seed does not “read” smoke data — it is mixed data in the database). For an Excel-only database: reset + Python seed and **do not** run smoke afterward.

## Indexes on already-initialized databases

New indexes must be added in:

1. [scripts/mongodb/mongodb-indexes.js](../../../scripts/mongodb/mongodb-indexes.js) — source for reapplication in any environment.
2. [config/database/init-indexes.js](../../../config/database/init-indexes.js) — keep aligned for **new** Docker volumes (first boot).

Apply indexes idempotently on a server already in production:

```bash
export MONGO_URI='mongodb://USER:PASSWORD@HOST:27017/?authSource=admin'
export DB_NAME=galaticos
./bin/galaticos db:setup
```

`db:setup` runs `mongodb-indexes.js` via `mongosh`; `createIndex` with the same options is safe if the index already exists.

## Backup and restore

### Backup (`mongodump`)

```bash
export MONGO_URI='mongodb://USER:PASSWORD@HOST:27017/?authSource=admin'
export DB_NAME=galaticos
./bin/galaticos db:backup
```

By default, files are stored under `backups/mongodb/` with a timestamp. See variables in [scripts/database/backup-mongodb.sh](../../../scripts/database/backup-mongodb.sh).

### Schedule (cron example)

On the server or a bastion with MongoDB access (adjust path and URI):

```cron
0 3 * * * cd /caminho/para/galaticos && MONGO_URI='mongodb://...' DB_NAME=galaticos ./bin/galaticos db:backup >> /var/log/galaticos-mongo-backup.log 2>&1
```

### Restore

**Always** test the restore procedure in a staging environment before production.

```bash
export MONGO_URI='mongodb://USER:PASSWORD@HOST:27017/?authSource=admin'
export DB_NAME=galaticos
./bin/galaticos db:restore --archive backups/mongodb/galaticos-YYYYMMDD-HHMMSS.archive.gz
```

Without `--drop`, `mongorestore` **merges** with existing data. With `--drop`, it **replaces** collections in `DB_NAME` — use only with full awareness of the risk. Details in [scripts/database/restore-mongodb.sh](../../../scripts/database/restore-mongodb.sh).

## Relevant environment variables (seed)

| Variable | Purpose |
|----------|---------|
| `GALATICOS_ENV=production` | Recommended when running tools against production; with this value, `--reset` in the seed requires `ALLOW_DESTRUCTIVE_SEED=1`. |
| `ALLOW_DESTRUCTIVE_SEED=1` | Allows `seed_mongodb.py --reset` when `GALATICOS_ENV` is production. |
| `MONGO_URI` / `DB_NAME` | MongoDB connection (aligned with the string used by the app, including `authSource=admin` if applicable). |

## When things go wrong

| Symptom | What to do |
|---------|------------|
| Aggregated player stats look wrong | Fix match documents, then `POST /api/aggregations/reconcile` — [reconciliation-runbook](../analytics/reconciliation-runbook.md) |
| Database empty after deploy | You likely ran `docker compose down -v`; restore from backup — never use `-v` in production |
| Seed fails with Mongo auth error | Set `MONGO_URI` with credentials and `authSource=admin` — see [vps-hosting.md](vps-hosting.md) |
| App serves old frontend after deploy | Run `./bin/galaticos docker:prod deploy` (rebuild), hard-refresh browser; see [incident notes](incident-deploy-vps-frontend-2026-05.md) |

## Quick reference

| Action | Command / note |
|------|----------------|
| Start stack | `./bin/galaticos docker:prod start` |
| Redeploy app only | `./bin/galaticos docker:prod deploy` (or `deploy:clean` if dependencies changed); see [vps-hosting.md §4](vps-hosting.md) if `docker compose … build` fails on Clojars |
| Indexes | `MONGO_URI=... DB_NAME=galaticos ./bin/galaticos db:setup` |
| Seed without wipe | `GALATICOS_ENV=production MONGO_URI=... ./bin/galaticos db:seed` |
| Backup | `MONGO_URI=... ./bin/galaticos db:backup` |
| Align `aggregated-stats` with `matches` | `POST /api/aggregations/reconcile` (admin); see [reconciliation-runbook](../analytics/reconciliation-runbook.md) |

For local development: [README.md](../../../README.md) (quick start) and [development.md](../development.md) (detailed setup, tests, and local CI).
