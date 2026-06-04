(ns galaticos.domain.matches
  "Pure match validation and view transforms.
  
  ## Regras de Temporada para Partidas
  
  | Operação | Validação de temporada |
  |----------|------------------------|
  | create   | Só temporada active (RN-MATCH-08); sem ativa → 400, explícita concluída → 403 |
  | update   | Permitido mesmo com temporada concluída (RN-MATCH-09) |
  | delete   | Permitido mesmo com temporada concluída (RN-MATCH-09) |
  
  Ver: docs/reference/domain/matches-seasons-hybrid-stats.md"
  (:require [clojure.string :as str]))

(defn- error [type message]
  {:error {:type type :message message}})

(defn- ok [data]
  {:ok data})

(defn season-accepts-new-matches?
  "Verifica se uma temporada aceita novas partidas (status = active).
  
  IMPORTANTE: Esta validação se aplica apenas ao CREATE de partidas.
  Update e delete são permitidos mesmo em temporadas concluídas (RN-MATCH-09)."
  [season]
  (= "active" (:status season)))

(defn validate-season-for-new-match
  "Valida se a temporada permite criação de novas partidas (RN-MATCH-08).
  
  Retorna {:ok season} se válido, {:error {:type :message}} se inválido.
  
  Erros possíveis:
  - :validation + 'No active season' → nenhuma temporada ativa (API 400)
  - :forbidden + 'completed season' → temporada explícita concluída (API 403)
  
  NOTA: Esta validação NÃO se aplica a update/delete (ver RN-MATCH-09)."
  [season]
  (cond
    (nil? season)
    (error :validation "No active season for this championship")

    (not (season-accepts-new-matches? season))
    (error :forbidden "Cannot create matches in a completed season")

    :else
    (ok season)))

(defn validate-players-enrolled
  [player-ids {:keys [season championship]}]
  (if season
    (let [enrolled-ids (set (:enrolled-player-ids season []))
          not-enrolled (remove enrolled-ids player-ids)]
      (if (seq not-enrolled)
        (error :validation
               (str "Players not enrolled in season: "
                    (str/join ", " (map str not-enrolled))))
        (ok true)))
    (if championship
      (let [enrolled-ids (set (:enrolled-player-ids championship []))
            not-enrolled (remove enrolled-ids player-ids)]
        (if (seq not-enrolled)
          (error :validation
                 (str "Players not enrolled in championship: "
                      (str/join ", " (map str not-enrolled))))
          (ok true)))
      (error :validation "Championship not found"))))

(defn- team-coherence-error
  [{:keys [player-id team-id]} players-by-id teams-by-id]
  (let [player (get players-by-id player-id)
        team (get teams-by-id team-id)]
    (cond
      (not player)
      (error :validation (str "Player not found: " (str player-id)))

      (not team)
      (error :validation (str "Team not found: " (str team-id)))

      :else
      (let [player-team-id (:team-id player)]
        (cond
          (not player-team-id)
          (error :validation
                 (str "Player has no team assigned: " (or (:name player) (str player-id))))

          (not= player-team-id team-id)
          (error :validation
                 (str "Invalid team-id for player " (or (:name player) (str player-id))
                      ": expected " (str player-team-id) ", got " (str team-id)))

          (not (contains? (set (:active-player-ids team)) player-id))
          (error :validation
                 (str "Player " (or (:name player) (str player-id))
                      " is not active in team " (or (:name team) (str team-id))))

          :else
          nil)))))

(defn validate-player-team-coherence
  [player-statistics players-by-id teams-by-id]
  (if-not (seq player-statistics)
    (ok true)
    (if-let [err (some #(team-coherence-error % players-by-id teams-by-id)
                        player-statistics)]
      err
      (ok true))))

(defn enrich-match-view
  "Attach :player-name and :team-name to player-statistics (read-only)."
  [match players teams]
  (if-not (seq (:player-statistics match))
    match
    (let [stats (:player-statistics match)
          pby (into {} (map (fn [p] [(str (:_id p)) p]) players))
          tby (into {} (map (fn [t] [(str (:_id t)) t]) teams))]
      (assoc match
             :player-statistics
             (mapv
              (fn [row]
                (let [pn (get pby (str (:player-id row)))
                      tn (get tby (str (:team-id row)))]
                  (cond-> row
                    pn (assoc :player-name (or (:name pn) (:player-name row)))
                    tn (assoc :team-name (or (:name tn) (:team-name row))))))
              stats)))))

(defn can-modify-match?
  [match force-update? python-seed-source]
  (if (and (= (:data-source match) python-seed-source) (not force-update?))
    (error :forbidden
           "Cannot modify historical data (python-seed). Use ?force=true to override.")
    (ok true)))

(defn can-delete-match?
  [match force-delete? python-seed-source]
  (if (and (= (:data-source match) python-seed-source) (not force-delete?))
    (error :forbidden
           "Cannot delete historical data (python-seed). Use ?force=true to override.")
    (ok true)))

(defn match-recalc-intent
  "Cria intent map para recálculo de aggregated-stats após CRUD de partida.
  
  O intent é processado pelo job incremental (player-stats-jobs) que aplica
  merge-aggregated-stats preservando baseline e calculando delta.
  
  Fluxo: persistir partida → add-match na temporada → submit job incremental
  Ver: docs/reference/analytics/architecture.md"
  [{:keys [reason crud-op match-id player-ids]}]
  {:op :recalc-stats
   :reason reason
   :crud-op crud-op
   :match-id match-id
   :player-ids (vec player-ids)
   :affected-player-ids (vec player-ids)})
