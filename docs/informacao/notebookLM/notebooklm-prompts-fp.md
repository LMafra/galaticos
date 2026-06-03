# NotebookLM Prompts – Programação Funcional (Galáticos)

Use estes prompts no notebook **Clojure / ClojureScript** do NotebookLM. Cole **um prompt de cada vez** no chat.

**Stack do projecto:** Clojure 1.11, Ring, Compojure, Monger 3.x, MongoDB, shadow-cljs, Reagent, reitit-frontend.

**Contexto:** Um refactor anterior (notebook OO) introduziu camadas `handlers → service → repository → db` com DI via `ns-resolve`. O piloto de championships já está implementado; estes prompts orientam a evolução funcional para matches, analytics e CLJS.

**Fluxo após respostas:**

1. Guardar em [notebooklm-response-fp.md](notebooklm-response-fp.md)
2. Extrair tarefas para [fp-improvement-checklist.md](../../a-fazer/notebookLM/fp-improvement-checklist.md)
3. Actualizar [fp-design-improvements.md](../../a-fazer/notebookLM/fp-design-improvements.md)
4. Revisar [planos 03–07](../../plans/README.md) antes de implementar

---

## 1. Arquitectura idiomática (substituir Service/Repository/DI)

**Prompt to paste in NotebookLM:**

```
Based on the Clojure/ClojureScript sources in this notebook, how should I structure
a Ring + Compojure + Monger API without copying Java-style Service and Repository classes?

My current approach (from a previous OO-oriented refactor):
- HTTP handlers parse requests and call service namespaces
- Services use (repo-call repo-ns 'find-by-id id) with ns-resolve for dependency injection
- Repositories are thin facades over db/* namespaces

Example of current DI pattern:

(defn- repo-call [repo sym & args]
  (apply (ns-resolve repo sym) args))

(defn delete-championship!
  ([id] (delete-championship! id 'galaticos.repository.championships))
  ([id repo]
   (if (repo-call repo 'exists? id)
     (if (repo-call repo 'has-matches? id)
       (errors/conflict! "Cannot delete...")
       (do (repo-call repo 'delete-by-id id) {:message "Deleted"}))
     (errors/not-found! "Not found"))))

Handlers are thin and delegate:

(defn delete-championship [request]
  (try
    (resp/success (service/delete-championship! (get-in request [:params :id])))
    (catch Exception e (hutil/handle-service-exception e "..."))))

The app manages championships, seasons, matches, player statistics per match,
and derived aggregated-stats (materialized cache from matches).

Please recommend:
1. Namespace layout and naming (avoiding "Service" and "Repository" if inappropriate)
2. How to pass dependencies in tests without with-redefs on production vars
3. Where multi-collection orchestration should live (e.g. create championship + season + activate)
4. Safe refactoring order when integration/contract tests already exist

Include short Clojure examples, not Java classes. Prefer maps of functions, protocols,
or other idiomatic Clojure patterns over constructor injection.
```

---

## 2. Regras de negócio como funções puras

**Prompt to paste in NotebookLM:**

```
In idiomatic Clojure, how should I separate pure business rules from side effects
in a sports roster API?

My current code mixes DB reads, validation, and writes in one function. Example:
finalize-championship! has duplicated cond branches for "active season" vs "legacy championship":

(cond
  finished-at (errors/validation! "Already finalized")
  (nil? titles-award-count) (errors/validation! "Must be a number")
  (neg? titles-award-count) (errors/validation! "Must be non-negative")
  (and (pos? titles-award-count) (empty? winner-player-ids))
  (errors/validation! "Winners required")
  (seq not-enrolled) (errors/validation! "Winners must be enrolled")
  :else (do (repo-call repo 'finalize-season! ...) {:message "Finalized"}))

Similar duplication exists for enroll-player! (season active vs championship-level enrollment).

Business rules include:
- Cannot delete championship with associated matches (409)
- Finalize only active championship/season; winners must be enrolled
- max-players limit on enrollment
- Match player must be enrolled in championship and team-coherent

Please recommend:
1. How to express preconditions as pure functions returning {:ok decision} or {:error ...}
2. Where to throw ex-info vs return error values (boundary vs domain)
3. How to test BRM rules without MongoDB or with-redefs
4. Naming conventions for pure decision fns vs effectful command fns

Use Clojure examples. The domain uses ex-info with {:status :message :code} today.
```

---

## 3. Erros e fronteiras HTTP

**Prompt to paste in NotebookLM:**

```
What is the idiomatic functional approach to error handling in a Ring API written in Clojure?

Current setup:
- domain/errors.clj throws ex-info with {:status 404/409/400 :message :code}
- middleware/errors.clj wrap-errors catches ExceptionInfo and maps :status to JSON
- Each handler still has try/catch calling handle-service-exception as fallback

(defn wrap-errors [handler]
  (fn [request]
    (try (handler request)
         (catch clojure.lang.ExceptionInfo e
           (let [data (ex-data e)]
             (if-let [status (:status data)]
               (resp/error (:message data) status)
               (resp/server-error "An error occurred"))))
         (catch Exception e (resp/server-error "Internal server error")))))

Handlers repeat:

(defn get-championship [request]
  (try
    (resp/success (service/get-championship (get-in request [:params :id])))
    (catch Exception e (hutil/handle-service-exception e "Failed to get championship"))))

Please recommend:
1. Should handlers return Result/Either maps instead of throwing?
2. When is ex-info appropriate in Clojure vs explicit {:error {:status :message}}?
3. How to eliminate per-handler try/catch while keeping the same JSON envelope
4. Railway-oriented programming or similar patterns in Clojure without heavy libraries

Give concrete Ring handler/middleware examples in Clojure.
```

---

## 4. Validação na fronteira (sem Strategy pattern OO)

**Prompt to paste in NotebookLM:**

```
How should I validate and normalize HTTP request bodies in Clojure at the API boundary?

Current approach in validation/entity.clj:
- Manual allowed-fields and required-fields sets per entity
- validate-championship-body returns {:error "..."} or {:data normalized-body}
- Normalization inline (e.g. parse max-players string to long, convert enrolled-player-ids to ObjectIds)

(defn validate-championship-body [body require-required?]
  (cond
    (not (map? body)) {:error "Invalid request body"}
    (seq unknown-fields) {:error (str "Unknown fields: " ...)}
    (seq missing) {:error (str "Missing required fields: " ...)}
    :else {:data (normalize-championship-body (select-keys body allowed-championship-fields))}))

Entities: championship, match (with nested player-statistics), player.

Please compare for this use case:
1. clojure.spec / spec-tools
2. Malli
3. Keeping manual validation but making it more composable

Also recommend:
- How to integrate validation errors with HTTP 400 responses
- Pure normalization pipelines (transduce? comp of fns?)
- Coercion of string IDs to ObjectId at boundary vs in db layer

Prefer practical Clojure snippets over abstract pattern names.
```

---

## 5. Testabilidade sem mocks orientados a objetos

**Prompt to paste in NotebookLM:**

```
What are idiomatic Clojure testing strategies for business logic that today depends on
database access, without using OO-style mocks?

Current service tests use with-redefs on repository namespace vars:

(with-redefs [repo/exists? (fn [x] (= x id))
              repo/has-matches? (fn [_] true)]
  (try
    (service/delete-championship! id repo-ns)
    (is false "should throw")
    (catch clojure.lang.ExceptionInfo e
      (is (= 409 (-> e ex-data :status))))))

We also have HTTP contract tests (handler/app) that assert JSON envelope and status codes.

Please recommend:
1. Passing a map of dependency functions {:find-by-id f :has-matches? f} vs defprotocol vs records
2. How to split tests: pure domain fns (no IO) vs integration (MongoDB) vs contract (HTTP)
3. Anti-patterns: with-redefs on production vars, global db atom, testing through ns-resolve
4. In-memory fakes or test fixtures for MongoDB in Clojure

Include example test code for a rule like "cannot delete championship with matches".
```

---

## 6. Camada de dados e fronteira de efeitos (Monger)

**Prompt to paste in NotebookLM:**

```
How should I structure the data access layer in Clojure with Monger 3.x to keep
pure transformations separate from side effects?

Current db/*.clj namespaces:
- Call (db) for connection, mc/find-maps, mc/insert, mc/update directly
- Mix normalization (->object-id), query building, and IO in same functions
- aggregations.clj runs Mongo pipelines then doseq + mc/update to persist cache

Example side-effect loop:

(doseq [player-stats rows]
  (let [pid (->object-id (:player-id player-stats))]
    (when-let [player (mc/find-one-as-map (db) "players" {:_id pid})]
      (mc/update (db) "players" {:_id pid}
                 {:$set {:aggregated-stats merged-stats :updated-at (java.util.Date.)}})))

Collections: championships, seasons, matches, players, teams, admins.

Please recommend:
1. Functions that take db as first argument vs implicit (db) — tradeoffs in Clojure
2. Where ObjectId conversion belongs (db boundary vs util vs handler)
3. Naming: when to use trailing ! for effectful functions
4. Testing aggregation logic with in-memory match data before hitting Mongo

Use Monger 3.x APIs (monger.collection/find-maps, insert, update) in examples.
```

---

## 7. Analytics: derivados como funções puras + materialização

**Prompt to paste in NotebookLM:**

```
How should I model derived/cached analytics in functional Clojure for a sports app?

Architecture today:
- matches collection = source of truth (player-statistics per game: goals, assists, cards, minutes)
- players.aggregated-stats = materialized cache (total + by-championship rollups)
- After match CRUD, incremental recalc job updates affected players
- POST /api/aggregations/reconcile recomputes from matches

Planned derived metrics (pure formulas):
- goal_contribution = goals + assists
- goal_contribution_per_game = (goals + assists) / games (0 if games = 0)
- discipline_index = (yellow-cards + 3 * red-cards) / games
- minutes_per_goal = minutes-played / goals (when goals > 0)

Current code mixes Mongo aggregation pipeline + imperative merge in Clojure:

(defn- merge-aggregated-stats [existing match-derived opts]
  ;; reconciles existing cache with new match rollups
  ...)

Please recommend:
1. Pure function (compute-stats-from-matches matches) -> stats-map — when to use vs Mongo $group
2. Property: recompute(all-matches) == cached aggregated-stats (testable invariant)
3. Transducers or group-by in Clojure for rollups vs keeping pipeline in Mongo
4. Where derived metric formulas should live (domain/analytics ns?)
5. How to add new metrics (cards, minutes) without dual-write bugs

Include Clojure examples for rollup and one derived metric function.
```

---

## 8. Jobs e efeitos assíncronos (backend)

**Prompt to paste in NotebookLM:**

```
What are idiomatic Clojure patterns for scheduling background work after HTTP writes,
without Observer/EventBus OO patterns?

Current setup:
- Match create/update/delete persists to Mongo, then submits job to in-process ThreadPoolExecutor
- player-stats-jobs.clj: single-thread executor, retry with backoff, job store for status
- HTTP response returns before recalc completes (async by default)

(defn submit-incremental-recalc-after-match! [player-ids]
  ;; enqueues Runnable on executor
  ...)

Please recommend:
1. Separating intent (recalc-players [ids]) from execution (executor, queue)
2. core.async vs agents vs simple ThreadPoolExecutor for this scale
3. Making the recalc handler a pure plan + effectful run — testability
4. Idempotency and observability (job status API) in functional style
5. When to keep it simple vs introduce a message queue later

The app is a single JVM deployment (Jetty), not serverless. Prefer minimal dependencies.
```

---

## 9. Frontend CLJS: estado e efeitos funcionais

**Prompt to paste in NotebookLM:**

```
How should I structure Reagent + ClojureScript state and side effects in a functional style?

Current frontend:
- Single app-state atom with many keys (:players, :championships, :matches, loading flags, errors)
- Many setter functions: set-players!, set-championships!, set-resource-loading!, etc.
- effects.cljs: route-driven fetching with guarded-fetch! (in-flight atom, callbacks)
- Components mix local r/atom (dashboard filters) with global app-state

(defn guarded-fetch! [k loaded? fetch-fn on-success]
  (when (and (not loaded?) (not (in-flight? k)))
    (mark-in-flight! k)
    (state/set-resource-loading! k true)
    (fetch-fn on-success* on-error*)))

Routes use reitit-frontend; API via cljs-http with JWT token from app-state.

Upcoming: analytics dashboard (derived metrics, charts, CSV export links).

Please recommend:
1. Event → reduce → app-state vs many setters (re-frame-style without full re-frame?)
2. Derived state with Reagent reactions/track for loading/error
3. Keeping render functions pure; isolating effects in effects ns
4. Patterns for analytics UI: selectors, memoized derived data, chart inputs

Use ClojureScript/Reagent examples, not React hooks.
```

---

## 10. Ordem de refactor FP (full-stack)

**Prompt to paste in NotebookLM:**

```
I have a sequential refactor plan for a Clojure/ClojureScript sports app.
Phase 1 (done): contract tests + domain errors + validation at boundary.
Phase 2 (done): championships vertical slice with service/repository/handler layers (OO-style pilot).
Phases 3–7 pending: matches, players/teams/seasons rollout, analytics data layer, analytics API, analytics UI.

I now want to apply functional programming principles from this notebook instead of
replicating the OO service/repository pattern everywhere.

Please recommend:
1. Refactoring order for matches → analytics → CLJS with FP approach
2. What to keep from the championships pilot vs simplify before replicating
3. Safe migration: contract tests exist; rule is no refactor + feature in same PR
4. Definition of done per vertical slice in functional terms (pure fns tested, effects at edges)
5. Whether to refactor championships pilot now or only apply FP to new slices

The stack is Ring, Compojure, Monger, MongoDB, shadow-cljs, Reagent.
Analytics has materialized player stats cache reconcilable from matches.

Give a phased checklist I can turn into implementation plans.
```

---

## Referências do projecto

- Respostas OO anteriores: [notebooklm-response.md](notebooklm-response.md)
- Checklist OO (histórico): [improvement-checklist.md](../../a-fazer/notebookLM/improvement-checklist.md)
- Regras de negócio: [regras-de-negocio.md](../dominio/regras-de-negocio.md)
- Schema MongoDB: [mongodb-schema.md](../dominio/mongodb-schema.md)
- Arquitectura analytics: [architecture.md](../analytics/architecture.md)
- Auditoria IA (APIs válidas): [auditoria-alucinacoes-ia.md](../qualidade/auditoria-alucinacoes-ia.md)
