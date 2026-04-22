(ns galaticos.handlers.players
  "Request handlers for player operations"
  (:require [galaticos.db.players :as players-db]
            [galaticos.db.teams :as teams-db]
            [galaticos.db.aggregations :as agg]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private allowed-player-fields
  #{:name :nickname :position :team-id :birth-date :nationality :height :weight
    :preferred-foot :shirt-number :active :email :phone :number :photo-url :notes})

(def ^:private required-player-fields
  #{:name :position})

(defn- validate-player-body
  ([body] (validate-player-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-player-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-player-fields)))]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else {:data (cond-> body
                         (:team-id body) (update :team-id resp/->object-id))})))))

(defn- handle-exception [e user-message]
  (if (= 400 (-> e ex-data :status))
    (let [raw (.getMessage e)
          msg (when-not (str/blank? raw) (str/trim raw))]
      (resp/error (or msg user-message) 400))
    (do
      (log/error e user-message)
      (resp/server-error user-message))))

(defn list-players
  "List all players"
  [request]
  (try
    (let [filters (if-let [team-id (get-in request [:params :team-id])]
                    {:team-id (resp/->object-id team-id)}
                    {})
          active-only (get-in request [:params :active])
          players (if (= active-only "true")
                    (players-db/find-active)
                    (players-db/find-all filters))]
      (resp/success players))
    (catch Exception e
      (log/error e "Error listing players")
      (resp/server-error "Failed to list players"))))

(defn get-player
  "Get a single player by ID"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [player (players-db/find-by-id id)]
        (resp/success player)
        (resp/not-found "Player not found")))
    (catch Exception e
      (handle-exception e "Failed to get player"))))

(defn get-player-detail-bundle
  "Player document + performance evolution in one response (fewer round-trips for the detail page)."
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [player (players-db/find-by-id id)]
        (resp/success {:player player
                       :evolution (agg/player-performance-evolution id)})
        (resp/not-found "Player not found")))
    (catch Exception e
      (handle-exception e "Failed to get player detail"))))

(defn create-player
  "Create a new player"
  [request]
  (try
    (let [player-data (:json-body request)
          {:keys [error data]} (validate-player-body player-data)]
      (if error
        (resp/error error 400)
        (do
          (when-let [tid (:team-id data)]
            (when-not (teams-db/exists? tid)
              (throw (ex-info "Team not found" {:status 400}))))
          (let [created (players-db/create data)]
            (when-let [tid (:team-id created)]
              (teams-db/add-player tid (:_id created)))
            (resp/success created 201)))))
    (catch Exception e
      (handle-exception e "Failed to create player"))))

(defn update-player
  "Update an existing player"
  [request]
  (try
    (let [id (get-in request [:params :id])
          updates (:json-body request)
          {:keys [error data]} (validate-player-body updates false)]
      (if error
        (resp/error error 400)
        (if (players-db/exists? id)
          (do
            (players-db/update-by-id id data)
            (if-let [updated (players-db/find-by-id id)]
              (resp/success updated)
              (resp/server-error "Failed to retrieve updated player")))
          (resp/not-found "Player not found"))))
    (catch Exception e
      (handle-exception e "Failed to update player"))))

(defn delete-player
  "Delete a player (soft delete)"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if (players-db/exists? id)
        (do
          (players-db/delete-by-id id)
          (resp/success {:message "Player deleted"}))
        (resp/not-found "Player not found")))
    (catch Exception e
      (handle-exception e "Failed to delete player"))))

