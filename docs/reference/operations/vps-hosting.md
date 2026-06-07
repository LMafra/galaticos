# VPS operations (e.g. Kinghost) and external domain (e.g. Locaweb)

**Summary:** Practical runbook for typical interactions between your **local machine**, the **hosting panel**, and the **Ubuntu server** where Galáticos runs production Docker. Complements [production-runbook.md](production-runbook.md), which focuses on data safety and generic commands.

**Post-incident note (May 2026):** Real production chain on the VPS (Mongo/seed, Clojars timeouts, `deploy` with only cache, browser cache, `pages.js` chunk failures) and decisions taken — see [incident-deploy-vps-frontend-2026-05.md](incident-deploy-vps-frontend-2026-05.md).

**Assumptions in this guide**

- Code at `/opt/galaticos` on the VPS.
- Stack: [config/docker/docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml).
- Example domain: `galaticosfr.com.br` / `www` at Locaweb; VPS hostname: `galaticosfr.vps-kinghost.net` (adjust to your DNS records).

## Before you start

- You need SSH key access to the VPS panel and `~/.ssh/config` with a dedicated host entry.
- Production code lives at `/opt/galaticos`; data safety rules are in [production-runbook.md](production-runbook.md) — never run `docker compose down -v` on prod.

---

## 1. SSH access

### Public key in the VPS panel

- The hosting wizard accepts only the **public** key (`.pub` file).
- The **private** key stays on your PC; never upload it to the panel or repositories.

### Two keys (e.g. GitHub + VPS)

Use `~/.ssh/config` so identities do not mix:

```sshconfig
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/id_ed25519
  IdentitiesOnly yes

Host galaticos-vps
  HostName galaticosfr.vps-kinghost.net
  User root
  IdentityFile ~/.ssh/id_ed25519_galaticos
  IdentitiesOnly yes
```

Connect: `ssh galaticos-vps`.

### “REMOTE HOST IDENTIFICATION HAS CHANGED” warning

After reinstalling the OS or changing the image, the server host key changes. On your PC:

```bash
ssh-keygen -f ~/.ssh/known_hosts -R 'IP_OR_VPS_HOSTNAME'
```

Then accept the new fingerprint on the next connection.

### Password

If the panel says “use the password or the configured key”, the password is the **one you set** in the panel (or access is key-only). That message does not imply a generic password.

---

## 2. Environment variables and Compose `.env` file

### Where to put `.env`

The file [docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml) lives under `config/docker/`. Docker Compose resolves variables from the **compose file directory** (or project root); the simplest approach is:

**`config/docker/.env`**

(repo root: `galaticos/config/docker/.env`)

This avoids having `.env` only at `/opt/galaticos/.env` while Compose **does not** interpolate `JWT_SECRET` / `MONGO_INITDB_*`.

### Special characters in `.env`

- **`$`** is interpreted by Compose (e.g. `$vM` becomes variable `vM`). For a literal dollar in a password, use **`$$`** or avoid `$` in the password.
- **`@` and `:` in the Mongo password** break parsing of `mongodb://user:pass@host` in **DATABASE_URL**. Prefer a **URL-safe** password (letters, numbers, `_`, `-`) or percent-encode the password if you build the URI manually.

### After changing `.env`

Recreate services that read those variables, for example:

```bash
cd /opt/galaticos
docker compose -f config/docker/docker-compose.prod.yml up -d --force-recreate
```

---

## 3. Update code and Docker image (deploy)

**`./bin/galaticos docker:prod restart` does not apply new code**: it only restarts the same container with the **same image**.

Correct flow after `git pull`:

```bash
cd /opt/galaticos
git fetch origin && git checkout main && git pull origin main
./bin/galaticos docker:prod deploy:clean
```

(`deploy:clean` uses `docker build --network host` and avoids Clojars timeouts mid-Dockerfile; see section 4. For incremental cache only: `./bin/galaticos docker:prod deploy`.)

MongoDB and the `mongodb-data-prod` volume are preserved; see [production-runbook.md](production-runbook.md) for what you must **never** do (`down -v` without backup).

---

## 4. Build on the VPS: Clojars timeout (`repo.clojars.org`)

**Scripts (from repo root):** see [`scripts/docker/prod-vps-build-app.sh`](../../../scripts/docker/prod-vps-build-app.sh) and the wrapper `./bin/galaticos docker:prod …`:

| Situation | Typical command |
|----------|------------------|
| Normal deploy (build on **host network** + recreate `app`) | `./bin/galaticos docker:prod deploy` or `deploy:clean` |
| Build `app` image only (host network) | `./bin/galaticos docker:prod build` / `build:app` / `build:app:clean` |
| Build with `docker compose build app` only (bridge network; may fail mid-Dockerfile on VPS) | `./bin/galaticos docker:prod build:app:compose` |
| Deploy with compose build (not recommended on VPS with timeouts) | `./bin/galaticos docker:prod deploy:app:compose` |
| MTU / daemon.json hint | `./bin/galaticos docker:prod hint:vps-docker-mtu` |
| CI + registry build hint | `./bin/galaticos docker:prod hint:vps-ci-build` |

Symptom: `RUN clj …` in the Dockerfile fails with **Connect timed out** to `repo.clojars.org` (e.g. at `clj -M:build:frontend` **after** `clj -P` already succeeded), while on the **host** `curl https://repo.clojars.org` works.

Typical cause: Docker **bridge** network on the VPS (MTU, routing, or concurrency limits) behaves worse than the host network.

### Option A (preferred) — `./bin/galaticos docker:prod deploy`

Commands **`deploy`**, **`deploy:clean`**, **`build`**, **`build:app`**, **`deploy:app`** use **`docker build --network host`** ([`prod-vps-build-app.sh`](../../../scripts/docker/prod-vps-build-app.sh)) so **all** `RUN clj …` steps in [Dockerfile.prod](../../../config/docker/Dockerfile.prod) see the host network. This avoids `docker compose build` + `build.network: host` in YAML still leaving **some** BuildKit steps on the bridge and `clj -M:build:frontend` timing out to Clojars again.

For the legacy path (`docker compose build app`), use **`build:app:compose`** / **`deploy:app:compose`** (not recommended on VPS with timeouts).

### Option A′ (YAML) — `build.network: host` in Compose

[docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml) may set `build.network: host` on the `app` service; it helps manual **`docker compose … build app`**, but is **less reliable** than `docker build --network host` in the scripts above across Docker/BuildKit versions.

### Manual (without `./bin/galaticos`, equivalent to host-network deploy)

```bash
cd /opt/galaticos
docker inspect galaticos-app-prod --format '{{.Config.Image}}'
```

Note the image name (e.g. `docker-app:latest`). Then:

```bash
docker build --network host --no-cache \
  -f config/docker/Dockerfile.prod \
  -t docker-app:latest \
  .
docker compose -f config/docker/docker-compose.prod.yml up -d --force-recreate --no-deps app
```

Adjust `-t` to the name Compose expects, if different (or set `PROD_APP_IMAGE=…` when using the script).

### Option B — Docker MTU

In `/etc/docker/daemon.json`:

```json
{ "mtu": 1400 }
```

Restart Docker: `systemctl restart docker`. Retry the build.

### Option C — Build off the VPS

CI (e.g. GitHub Actions) runs `docker build`, **pushes** to a registry; on the VPS only `docker pull` + `up`. The VPS then does not depend on downloading Clojure dependencies during build.

---

## 5. Nginx, HTTPS, and DNS

### Proxy to the app

The app exposes port `3000` on the host; Nginx should `proxy_pass` to `http://127.0.0.1:3000` with `Host`, `X-Forwarded-For`, and `X-Forwarded-Proto` headers.

### Let’s Encrypt (Certbot)

- **A** records for `@` and `www` must point to the VPS IP **before** the HTTP challenge.
- “No valid A records” on secondary validation: wait for global DNS propagation; confirm with `dig @8.8.8.8` and `dig @1.1.1.1`.
- Empty **AAAA** is normal if you do not use IPv6.

### 502 Bad Gateway

Almost always: the app is not listening on 3000 (container restarting, crash connecting to Mongo, invalid URI). Check `docker logs galaticos-app-prod` and `curl -sf http://127.0.0.1:3000/health` on the VPS.

---

## 6. Seed with Excel and Mongo in Docker prod

### File

The script expects **`data/raw/galaticos.xlsm`** (see [scripts/database/seed.sh](../../../scripts/database/seed.sh)). The players sheet must be named exactly **`Base de dados`**.

### Copy Excel from Windows to the VPS

PowerShell:

```powershell
scp "C:\Users\YOUR_USER\Downloads\galaticos.xlsm" root@galaticosfr.vps-kinghost.net:/opt/galaticos/data/raw/galaticos.xlsm
```

WSL:

```bash
scp /mnt/c/Users/YOUR_USER/Downloads/galaticos.xlsm root@HOST:/opt/galaticos/data/raw/galaticos.xlsm
```

### MongoDB reachable from the host (seed / `mongosh`)

By default the `mongodb` service in compose **does not** publish `27017`. To run `./bin/galaticos db:seed` on the VPS with Docker fallback (`--network host`) or host tools, map only on localhost:

```yaml
ports:
  - "127.0.0.1:27017:27017"
```

on the `mongodb` service. Remove or keep per your security policy after the operation.

### Authentication

Always export a URI with user and password (and `authSource=admin` when applicable), aligned with `.env`:

```bash
export MONGO_URI='mongodb://galaticos:PASSWORD@127.0.0.1:27017/galaticos?authSource=admin'
./bin/galaticos db:seed
```

Without this you get “Command find requires authentication”.

### Clear and re-seed

Only with full awareness of risk and project safety variables (see [production-runbook.md](production-runbook.md) for `GALATICOS_ENV` and `ALLOW_DESTRUCTIVE_SEED`):

```bash
./bin/galaticos db:seed --reset
```

---

## 7. Quick post-maintenance checklist

| Check | Command / note |
|-------------|----------------|
| Current code | `cd /opt/galaticos && git log -1 --oneline` |
| Containers | `docker compose -f config/docker/docker-compose.prod.yml ps` |
| App health | `curl -sf http://127.0.0.1:3000/health` |
| Recent image | `docker images` / creation date of `galaticos-app-prod` container |
| Site | `https://www.yourdomain.tld` (hard refresh or private window after deploy) |

---

## 8. Repository references

- [README.md](../../../README.md) — overview and `./bin/galaticos` scripts.
- [production-runbook.md](production-runbook.md) — volumes, safe seed, backup, indexes.
- [docker-compose.prod.yml](../../../config/docker/docker-compose.prod.yml) — `app` and `mongodb` services.
