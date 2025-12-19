## Build/Dev Gap Report (updated)

Scope: backend/CLJS builds and Docker (dev/prod). Status: gaps addressed; notes capture what changed and any residual items.

### Backend build (deps/uberjar)
- ✅ Dependency lock added (`deps-lock.edn`) via `:deps-lock` alias and `scripts/build/lock.clj`.
- ✅ Frontend CLJS deps separated into `:frontend`; backend classpath remains lean (`deps.edn`).
- ✅ Uberjar pipeline now builds CLJS before packaging; prod image copies compiled assets. Files: `build.clj`, `Dockerfile.prod`.
- ✅ Build script moved to shadow-cljs; legacy `cljs.build.api` + foreign-libs removed. Files: `build.clj`, removed `public/js/react-*`.
- ✅ JVM opts provided in prod image (`JAVA_OPTS` in `Dockerfile.prod`). AOT still default (acceptable for now).

### Frontend build (CLJS)
- ✅ Shadow-cljs added with watch/release targets; npm deps pinned (`package.json`/`package-lock.json`). Files: `shadow-cljs.edn`, `build.clj`.
- ✅ Prod asset pipeline integrated; release build outputs to `resources/public/js/compiled` and is baked into uberjar/docker. Files: `Dockerfile.prod`, `scripts/build/build.sh`.
- ✅ Dev DX: watcher script and npm scripts (`npm run cljs:watch`), new `scripts/dev/watch-cljs.sh`; assets volume-writable in dev compose.
- ⚠ API base URL still static; add env-based config if multi-env frontend hosting is needed.

### Docker/dev/prod
- ✅ Dev compose Mongo now bound to localhost only; compiled assets volume persists rebuilds; added frontend watch service. Files: `docker-compose.dev.yml`.
- ✅ Dev image caches clj/npm deps and prebuilds CLJS. Files: `Dockerfile.dev`.
- ✅ Prod image builds CLJS + uberjar; includes JVM tuning. Files: `Dockerfile.prod`.
- ✅ Prod compose secures Mongo (no published port; credentials required) and adds app healthcheck. Files: `docker-compose.prod.yml`.
- ✅ `dev.sh stop` no longer deletes images/volumes by default. File: `scripts/docker/dev.sh`.
- ⚠ No Mongo healthcheck in app container; rely on Mongo’s own check. Add app-level readiness for Mongo if desired.

### Follow-ups (optional)
- Add API base URL/env injection for frontend.
- Add CI targets (`build:frontend`, `build:backend`, `test`, `lint`) wired to docker builds.
- Add Mongo readiness probe in app container if required.