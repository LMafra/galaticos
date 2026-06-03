(ns galaticos.logic.players
  "Player orchestration."
  (:require [galaticos.db.aggregations :as agg]
            [galaticos.db.players :as players-db]
            [galaticos.db.teams :as teams-db]
            [galaticos.domain.errors :as errors]
            [galaticos.domain.players :as domain]
            [galaticos.util.response :as resp]))

(defn- require-ok [result]
  (if-let [err (:error result)]
    (case (:type err)
      :not-found (errors/not-found! (:message err))
      :conflict (errors/conflict! (:message err))
      :validation (errors/validation! (:message err))
      (errors/validation! (or (:message err) "Invalid request")))
    (:ok result)))

(defn list-all
  [request]
  (let [filters (if-let [team-id (get-in request [:params :team-id])]
                  {:team-id (resp/->object-id team-id)}
                  {})
        active-only (get-in request [:params :active])]
    (if (= active-only "true")
      (players-db/find-active)
      (players-db/find-all filters))))

(defn get-by-id
  [id]
  (if-let [player (players-db/find-by-id id)]
    player
    (errors/not-found! "Player not found")))

(defn- resolve-team-name
  [player]
  (try
    (some-> (:team-id player) teams-db/find-by-id :name)
    (catch Exception _ nil)))

(defn detail-bundle
  [id]
  (if-let [player (players-db/find-by-id id)]
    (let [team-name (resolve-team-name player)
          player* (domain/attach-team-name player team-name)]
      {:player player*
       :evolution (agg/player-performance-evolution id)})
    (errors/not-found! "Player not found")))

(defn- assert-team-exists! [data]
  (when-let [tid (:team-id data)]
    (require-ok (domain/team-assignment-decision tid (teams-db/exists? tid)))))

(defn create!
  [data]
  (assert-team-exists! data)
  (let [created (players-db/create data)]
    (when-let [tid (:team-id created)]
      (teams-db/add-player tid (:_id created)))
    created))

(defn update!
  [id data]
  (if (players-db/exists? id)
    (do
      (players-db/update-by-id id data)
      (if-let [updated (players-db/find-by-id id)]
        updated
        (throw (ex-info "Failed to retrieve updated player"
                        {:status 500 :message "Failed to retrieve updated player"}))))
    (errors/not-found! "Player not found")))

(defn delete!
  [id]
  (if (players-db/exists? id)
    (do
      (players-db/delete-by-id id)
      {:message "Player deleted"})
    (errors/not-found! "Player not found")))
