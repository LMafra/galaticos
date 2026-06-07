# Operational incident: VPS deploy, Clojars, and frontend loading (May 2026)

**Summary:** Post-mortem (May 2026): VPS Docker build timeouts, stale images, browser cache, and ClojureScript lazy chunk failures. Read this when production deploy or frontend loading fails after a seemingly successful build.

**Related:** [vps-hosting.md](vps-hosting.md), [production-runbook.md](production-runbook.md), [`scripts/docker/prod-vps-build-app.sh`](../../../scripts/docker/prod-vps-build-app.sh), [`shadow-cljs.edn`](../../../shadow-cljs.edn), [`src-cljs/galaticos/lazy_pages.cljs`](../../../src-cljs/galaticos/lazy_pages.cljs).

---

## 1. Observed symptoms

1. **`bin/galaticos db:seed`** (and Python scripts via Docker) failed with MongoDB **`Unauthorized` / `Command aggregate requires authentication`** when the URI was only `mongodb://localhost:27017` without credentials — prod Mongo had auth; the seed flow needed a URI with user/password or loading `config/docker/.env` (fixed in `scripts/database/seed.sh`).

2. **Build on the VPS** (`docker compose … build app`): **`Connect timed out`** to **`repo.clojars.org`** during **`RUN clj -P`** or later steps — Docker **bridge** (or BuildKit) network on the VPS worse than the host.

3. After **`docker build --network host`**, **`clj -P`** could succeed and **`clj -M:build:frontend prod`** still fail with Clojars timeout — **`docker compose build`** with `build.network: host` in YAML **does not** guarantee the same behavior for **all** BuildKit steps.

4. **`./bin/galaticos docker:prod deploy`** (without `--no-cache`) finished in seconds with **all layers `CACHED`** — **no new code** in the JAR; the browser still showed old UI if the user expected changes from another branch.

5. **`git log` on the VPS** showed only the expected merge on **`main`**; UI changes on another branch **did not** appear until merge + pull + a new build.

6. **Private window** showed the new layout; the **usual browser** did not — typical **cache** for `app.js` / assets (Ctrl+F5 is not always enough; “clear site data” usually fixes it).

7. UI errors: **`Falha ao carregar módulo: Error loading pages: Consecutive load failures`** — **shadow.lazy** runtime tried to load the second bundle **`/js/compiled/pages.js`**; the request failed (network, inconsistent cache between `app.js` and `pages.js`, proxy, extension, etc.).

---

## 2. Root causes (summary)

| Area | Cause |
|------|--------|
| Mongo / seed | URI without auth; seed script did not read `config/docker/.env` like other scripts. |
| Clojars build | HTTPS timeout from **container** network (bridge / BuildKit) to Clojars; intermittent. |
| “Deploy changes nothing” | Docker **layer cache** + code on `main` without expected commits. |
| Browser | Aggressive cache or profile with extensions; **two JS files** (`app.js` + `pages.js`) increase **inconsistent state** risk. |
| Shadow loader | Runtime dependency on **`pages.js`**; failure breaks heavy routes. |

---

## 3. Mitigations and repository changes

### 3.1. Seed and Mongo

- **`scripts/database/seed.sh`**: if `MONGO_URI` is the default and **`config/docker/.env`** exists with `MONGO_INITDB_ROOT_*`, build URI with **`authSource=admin`** (aligned with `setup.sh` / `reset.sh`). Log URI with password **redacted**.

### 3.2. Build and deploy on the VPS

- **`config/docker/docker-compose.prod.yml`**: `build.network: host` on `app` (helps those who use **only** `docker compose build`; does not fix all BuildKit cases).
- **`scripts/docker/prod-vps-build-app.sh`** + **`./bin/galaticos docker:prod …`**: `deploy`, `deploy:clean`, `build`, `build:app`, etc. use **`docker build --network host`** for **`Dockerfile.prod`**, then **`docker compose up`** to recreate `app` — **default reliable path** for Clojars on the VPS.
- Documentation updated in **`vps-hosting.md`** and **`production-runbook.md`** (`deploy` flow, MTU, CI).

### 3.3. Frontend: end of `:pages` code-split (stable decision)

To **eliminate** the second server round-trip (**`pages.js`**) and **“Consecutive load failures”** errors:

- **`shadow-cljs.edn`**: single **`:app`** module with all heavy page entry points; removed **`module-loader`** and **`:pages`** module.
- **`src-cljs/galaticos/lazy_pages.cljs`**: direct `require` of page namespaces; **`loadable-route`** is only **`(into [comp] args)`** (no `shadow.lazy`).
- **`.clj-kondo/config.edn`**: removed lazy-pages-specific exception.

**Trade-off:** first JS download is **larger** (everything in `app.js`); gain **robustness** in production (fewer failure points, less confusion with two-bundle cache).

---

## 4. Quick checklist for next time

1. **`main`** (or deploy branch) contains the **same** commit with UI changes.  
2. On the VPS: **`git pull`**, then **`./bin/galaticos docker:prod deploy:clean`** if dependencies changed or cache is uncertain; otherwise **`deploy`**.  
3. If build fails on Clojars: host network is already in the flow; see **MTU** / **CI+registry** in `vps-hosting.md` §4.  
4. After deploy: **clear site data** or private window to validate; confirm **`/health`** and a request to **`/js/compiled/app.js`** (200).  
5. **`/js/compiled/pages.js`** no longer needs validation on the current release pipeline (file no longer produced).

---

## 5. State after the incident

- VPS application deploy documented with scripts and **host-network build** by default.  
- Production CLJS **monolithic** frontend (no lazy `pages` chunk), matching “**it worked**” after merge and new deploy.

Reference date for symptoms and fixes: **May 2026** (Galáticos repository).
