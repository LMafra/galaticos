## Backend Gap Report

Scope: server middleware, auth, CRUD handlers/DB, and aggregation pipelines. Priority favors bug risk over design polish.

### Critical Gaps (bugs and correctness)
- JSON body parsing **fixed**: middleware now slurps InputStreams, enforces `application/json` for mutating methods, and returns 400 on malformed JSON. Handlers consume `:json-body`. Files: `src/galaticos/util/response.clj`, `src/galaticos/handler.clj`.
- CORS/preflight **fixed**: explicit `OPTIONS` responder with allowlisted origins, exposed `Authorization`, and proper methods/headers. Files: `src/galaticos/middleware/cors.clj`, `src/galaticos/handler.clj`.
- Session/CSRF issue **addressed by switch to tokens**: API now uses stateless Bearer JWT; CSRF no longer applicable. `DISABLE_AUTH` guarded to dev/test and logged. Files: `src/galaticos/middleware/auth.clj`, `src/galaticos/handlers/auth.clj`.
- Insert responses/IDs **fixed**: `create` fns insert `_id` up front and return full docs; inbound IDs coerced to `ObjectId`. Files: `src/galaticos/db/players.clj`, `teams.clj`, `matches.clj`, `championships.clj`, `admins.clj`.
- ObjectId mismatches **fixed**: foreign keys coerced on ingest; responses stringify via `prepare-for-json`. Files: `src/galaticos/db/*`, `src/galaticos/handlers/players.clj`, `matches.clj`.
- Aggregated stats drift **fixed**: stats recomputed on match create/update/delete; uses shared aggregation pipeline. Files: `src/galaticos/handlers/matches.clj`, `src/galaticos/db/aggregations.clj`.
- Input validation **added**: per-resource whitelists/required fields; protected fields (stats/timestamps) rejected with 400. Files: `src/galaticos/handlers/players.clj`, `teams.clj`, `matches.clj`, `championships.clj`.
- Error transparency **improved**: invalid IDs raise 400; DB errors bubble instead of masking as 404. Files: `src/galaticos/util/response.clj`, `src/galaticos/db/*`, `src/galaticos/handlers/*`.
- Remaining gap: referential integrity on deletes/cascades still needs policy/implementation. Files: `src/galaticos/handlers/*.clj`, `src/galaticos/db/*.clj`.

### Recommended Refactors (shortlist)
- Done: robust JSON parsing middleware.
- Done: proper CORS + `OPTIONS`.
- Done: stateless token auth (JWT) replaced session/CSRF dependency.
- Done: `create` returns inserted doc with `_id`; IDs coerced.
- Done: stats recompute on match update/delete.
- Done: validation/whitelists per resource.
- Done: `DISABLE_AUTH` guarded to dev/test with logging.
- Done: bad ObjectIds -> 400, DB errors surface as 5xx.
- Still open: referential integrity strategy (cascade/deny on delete) and reconciliation tooling for legacy data.

### Next Steps
- Ship JWT secret config per env; verify allowed-origins env var.
- Define delete/cascade policy and implement checks or soft-delete propagation.
- Add reconciliation/repair commands for legacy stats & dangling refs.
- Add automated tests for CORS preflight, auth, validation, and ID coercion.

### Priority Fix Batch (status)
- JSON body parsing: ✅ middleware slurps InputStreams, enforces JSON for mutating methods, 400 on malformed JSON.
- CORS + auth compatibility: ✅ `OPTIONS` handled; origins allowlisted; headers expose `Authorization`; using JWT tokens (no cookies/CSRF dependency).
- CSRF/session safety: ✅ moved to stateless tokens; CSRF not required for APIs.
- Correct create responses and IDs: ✅ creates return inserted doc with `_id`; inbound IDs coerced to `ObjectId`; responses stringify IDs.
- Aggregated stats consistency: ✅ recompute on match create/update/delete via shared pipeline.
- Input validation/whitelists: ✅ per-resource allowed/required fields; protected fields rejected.
- Auth toggle safety: ✅ `DISABLE_AUTH` allowed only in dev/test; logs when bypassing.
- Error transparency: ✅ invalid IDs -> 400; DB errors bubble as server errors, not 404.

### Validation Checklist (remaining areas)
- Preflight: OPTIONS handled on all `/api/*` routes with correct headers.
- Session/cookie flow: CORS headers align with `with-credentials`; CSRF token present/validated when cookies in use.
- Payloads: All POST/PUT paths parse JSON reliably; non-JSON requests get 415/400.
- ID hygiene: All inbound IDs validated/coerced; outbound responses include stringified `_id`.
- Stats drift: Background/manual reconciliation available; metrics reflect latest matches.
- Deletion effects: Deleting entities handles or refuses when referenced; provides clear errors.
- Logging/monitoring: Auth bypass and error branches log with context; sensitive data not logged.

