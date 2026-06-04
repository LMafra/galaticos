(ns galaticos.logic.matches
  "Match orchestration over MatchStore.
  
  ## Fluxo de Operações
  
  | Operação | Fluxo |
  |----------|-------|
  | create!  | validar temporada ativa → validar players → persistir → add-match na season → job recalc |
  | update!  | validar existência → validar players → persistir → add-match na season → job recalc |
  | delete!  | validar existência → deletar → remove da season → job recalc |
  
  ## Regras de Temporada (RN-MATCH-08/09)
  
  - CREATE: só temporada active; sem ativa → 400, explícita concluída → 403
  - UPDATE/DELETE: permitidos mesmo com temporada concluída
  
  ## Job de Estatísticas
  
  Após cada operação, submit-recalc! envia intent para recálculo incremental
  que aplica merge-aggregated-stats preservando baseline híbrido.
  
  Ver: docs/reference/domain/matches-seasons-hybrid-stats.md"
  (:require [galaticos.analytics.player-stats-jobs :as player-stats-jobs]
            [galaticos.db.match-store :as store]
            [galaticos.db.matches :as matches-db]
            [galaticos.db.protocol.match-store :as protocol]
            [galaticos.domain.errors :as errors]
            [galaticos.domain.matches :as domain]))

(defn- throw-domain-error [{:keys [type message]}]
  (case type
    :not-found (errors/not-found! message)
    :forbidden (errors/forbidden! message)
    :validation (errors/validation! message)
    (errors/validation! (or message "Invalid request"))))

(defn- require-ok [result]
  (if-let [err (:error result)]
    (throw-domain-error err)
    (:ok result)))

(defn- submit-recalc! [intent]
  (player-stats-jobs/submit-incremental-recalc-after-match! intent))

(defn- resolve-season-for-enrollment
  [store championship-id season-id]
  (or (when season-id (protocol/find-season-by-id store season-id))
      (protocol/find-default-season-for-championship store championship-id)))

(defn- players-by-id [store player-statistics]
  (let [player-ids (map :player-id player-statistics)
        players (protocol/find-players-by-ids store player-ids)]
    (into {} (map (fn [p] [(:_id p) p]) players))))

(defn- teams-by-id [store player-statistics]
  (let [team-ids (distinct (keep :team-id player-statistics))
        teams (if (seq team-ids)
                (protocol/find-teams-by-ids store team-ids)
                [])]
    (into {} (map (fn [t] [(:_id t) t]) teams))))

(defn- validate-player-stats!
  [store championship-id season-id player-statistics]
  (when (seq player-statistics)
    (let [player-ids (map :player-id player-statistics)
          season (resolve-season-for-enrollment store championship-id season-id)
          championship (when-not season
                           (protocol/find-championship-by-id store championship-id))]
      (require-ok (domain/validate-players-enrolled player-ids {:season season
                                                                :championship championship}))))
  (require-ok (domain/validate-player-team-coherence player-statistics
                                                     (players-by-id store player-statistics)
                                                     (teams-by-id store player-statistics))))

(defn- enrich-for-read [store match]
  (let [stats (:player-statistics match)
        p-ids (distinct (keep :player-id stats))
        t-ids (distinct (keep :team-id stats))]
    (domain/enrich-match-view match
                              (protocol/find-players-by-ids store p-ids)
                              (if (seq t-ids)
                                (protocol/find-teams-by-ids store t-ids)
                                []))))

(defn list-matches
  ([request] (list-matches store/*store* request))
  ([store request]
   (let [championship-id (get-in request [:params :championship-id])
         season-id (get-in request [:params :season-id])]
     (cond
       season-id (protocol/find-matches-by-season store season-id)
       championship-id (protocol/find-matches-by-championship store championship-id)
       :else (protocol/find-all-matches store)))))

(defn get-by-id
  ([id] (get-by-id store/*store* id))
  ([store id]
   (if-let [match (protocol/find-match-by-id store id)]
     (enrich-for-read store match)
     (errors/not-found! "Match not found"))))

(defn create!
  "Cria nova partida com validação de temporada ativa (RN-MATCH-08).
  
  Fluxo:
  1. Resolve temporada: find-season-for-new-match (ativa ou season-id explícito)
  2. Valida: temporada ativa? → sim: continua; não/nil: erro 400/403
  3. Valida: players inscritos e coerência de times
  4. Persiste partida com season-id
  5. Registra partida na temporada (add-match-to-season!)
  6. Submete job de recálculo incremental
  
  IMPORTANTE: Esta é a ÚNICA operação que valida status da temporada."
  ([request data] (create! store/*store* request data))
  ([store request data]
   (let [match-data (dissoc data :player-statistics)
         player-statistics (:player-statistics data)
         championship-id (:championship-id match-data)
         season (require-ok
                 (domain/validate-season-for-new-match
                  (protocol/find-season-for-new-match store championship-id (:season-id match-data))))
         season-id (:_id season)
         match-data (assoc match-data :season-id season-id)
         admin-id (get-in request [:admin :_id])]
     (validate-player-stats! store championship-id season-id player-statistics)
     (let [created (protocol/create-match store match-data player-statistics
                                          {:created-by admin-id
                                           :data-source matches-db/data-source-ui-create})]
       (protocol/add-match-to-season! store season-id (:_id created))
       (submit-recalc!
        (domain/match-recalc-intent
         {:reason :after-match-create
          :crud-op :create
          :match-id (:_id created)
          :player-ids (map :player-id player-statistics)}))
       created))))

(defn update!
  "Atualiza partida existente. NÃO valida status da temporada (RN-MATCH-09).
  
  Fluxo:
  1. Valida existência da partida
  2. Valida permissão (força para python-seed)
  3. Resolve season-id: do payload, existente, ou default do campeonato
  4. Valida players inscritos e coerência de times
  5. Persiste alterações
  6. Atualiza registro na temporada (add-match-to-season!)
  7. Submete job de recálculo (inclui players antigos e novos)
  
  IMPORTANTE: Permitido mesmo com temporada concluída."
  ([id request data] (update! store/*store* id request data))
  ([store id request data]
   (if-not (protocol/match-exists? store id)
     (errors/not-found! "Match not found")
     (let [existing (protocol/find-match-by-id store id)
           force-update? (= "true" (get-in request [:params :force]))]
       (require-ok (domain/can-modify-match? existing force-update?
                                             matches-db/data-source-python-seed))
       (let [championship-id (or (:championship-id data) (:championship-id existing))
             season-id (or (:season-id data)
                           (:season-id existing)
                           (some-> (protocol/find-default-season-for-championship store championship-id)
                                   :_id))
             player-statistics (or (:player-statistics data) (:player-statistics existing))]
         (validate-player-stats! store championship-id season-id player-statistics)
         (protocol/update-match-by-id store id
                                      (cond-> (assoc data :player-statistics player-statistics)
                                        season-id (assoc :season-id season-id))
                                      {:force-overwrite force-update?})
         (if-let [updated (protocol/find-match-by-id store id)]
           (do
             (when season-id
               (protocol/add-match-to-season! store season-id (:_id updated)))
             (submit-recalc!
              (domain/match-recalc-intent
               {:reason :after-match-update
                :crud-op :update
                :match-id (:_id updated)
                :player-ids (distinct (concat (map :player-id (:player-statistics existing))
                                              (map :player-id player-statistics)))}))
             updated)
           (throw (ex-info "Failed to retrieve updated match"
                             {:status 500 :message "Failed to retrieve updated match"}))))))))

(defn delete!
  "Deleta partida existente. NÃO valida status da temporada (RN-MATCH-09).
  
  Fluxo:
  1. Valida existência da partida
  2. Valida permissão (força para python-seed)
  3. Deleta partida
  4. Remove da temporada (remove-match-from-season!) se havia season-id
  5. Submete job de recálculo para players afetados
  
  IMPORTANTE: Permitido mesmo com temporada concluída."
  ([id request] (delete! store/*store* id request))
  ([store id request]
   (if-not (protocol/match-exists? store id)
     (errors/not-found! "Match not found")
     (let [existing (protocol/find-match-by-id store id)
           force-delete? (= "true" (get-in request [:params :force]))]
       (require-ok (domain/can-delete-match? existing force-delete?
                                             matches-db/data-source-python-seed))
       (protocol/delete-match-by-id store id)
       (when-let [season-id (:season-id existing)]
         (protocol/remove-match-from-season! store season-id (:_id existing)))
       (submit-recalc!
        (domain/match-recalc-intent
         {:reason :after-match-delete
          :crud-op :delete
          :match-id (:_id existing)
          :player-ids (map :player-id (:player-statistics existing))}))
       {:message "Match deleted"}))))
