(ns galaticos.handlers.seasons
  "Request handlers for season operations"
  (:require [galaticos.db.seasons :as seasons-db]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private allowed-season-fields
  #{:season :status :format :start-date :end-date :titles-count})

(def ^:private required-season-fields
  #{:season})

(defn- handle-exception [e user-message]
  (if (= 400 (-> e ex-data :status))
    (resp/error (or user-message (.getMessage e)) 400)
    (do
      (log/error e user-message)
      (resp/server-error user-message))))

(defn- normalize-season-body [body]
  (let [out (cond-> body
              (:enrolled-player-ids body)
              (update :enrolled-player-ids #(mapv resp/->object-id %))
              (:winner-player-ids body)
              (update :winner-player-ids #(mapv resp/->object-id %)))]
    out))

(defn- validate-season-body
  ([body] (validate-season-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-season-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-season-fields)))
           normalized (normalize-season-body body)]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else {:data normalized})))))

(defn list-seasons
  "List all seasons of a championship root"
  [request]
  (try
    (let [championship-id (get-in request [:params :id])]
      (resp/success
       (seasons-db/find-all-by-championship championship-id)))
    (catch Exception e
      (handle-exception e "Failed to list seasons"))))

(defn get-season
  "Get a season by ID"
  [request]
  (try
    (let [season-id (get-in request [:params :id])]
      (if-let [season (seasons-db/find-by-id season-id)]
        (resp/success season)
        (resp/not-found "Season not found")))
    (catch Exception e
      (handle-exception e "Failed to get season"))))

(defn create-season
  "Create a new season under a championship root"
  [request]
  (try
    (let [championship-id (get-in request [:params :id])
          body (:json-body request)
          {:keys [error data]} (validate-season-body body)]
      (if error
        (resp/error error 400)
        (let [championship (championships-db/find-by-id championship-id)
              format (or (:format data) (:format championship))
              created (seasons-db/create (merge data {:championship-id championship-id
                                                       :format format}))]
          ;; If created active, enforce unique active
          (when (= "active" (:status created))
            (seasons-db/activate! (:_id created)))
          (resp/success created 201))))
    (catch Exception e
      (let [status (or (-> e ex-data :status) 500)]
        (if (= 409 status)
          (resp/error (or (.getMessage e) "Conflict") 409)
          (handle-exception e "Failed to create season"))))))

(defn update-season
  "Update an existing season"
  [request]
  (try
    (let [season-id (get-in request [:params :id])
          updates (:json-body request)
          {:keys [error data]} (validate-season-body updates false)]
      (if error
        (resp/error error 400)
        (if (seasons-db/exists? season-id)
          (do
            (seasons-db/update-by-id season-id data)
            (if-let [updated (seasons-db/find-by-id season-id)]
              (resp/success updated)
              (resp/server-error "Failed to retrieve updated season")))
          (resp/not-found "Season not found"))))
    (catch Exception e
      (handle-exception e "Failed to update season"))))

(defn delete-season
  "Delete a season by ID"
  [request]
  (try
    (let [season-id (get-in request [:params :id])]
      (if (seasons-db/exists? season-id)
        (do
          (seasons-db/delete-by-id season-id)
          (resp/success {:message "Season deleted"}))
        (resp/not-found "Season not found")))
    (catch Exception e
      (handle-exception e "Failed to delete season"))))

(defn activate-season
  "Activate one season and deactivate other seasons of the same championship root."
  [request]
  (try
    (let [season-id (get-in request [:params :id])]
      (if (seasons-db/exists? season-id)
        (do
          (seasons-db/activate! season-id)
          (resp/success {:message "Season activated"}))
        (resp/not-found "Season not found")))
    (catch Exception e
      (handle-exception e "Failed to activate season"))))

(defn enroll-player
  "Enroll a player in a season"
  [request]
  (try
    (let [season-id (get-in request [:params :id])
          player-id (get-in request [:params :player-id])]
      (if-not (and season-id player-id)
        (resp/error "Season ID and player ID are required" 400)
        (let [season (seasons-db/find-by-id season-id)]
          (if season
            (do
              (seasons-db/add-player season-id player-id)
              (resp/success {:message "Player enrolled"}))
            (resp/not-found "Season not found")))))
    (catch Exception e
      (handle-exception e "Failed to enroll player"))))

(defn unenroll-player
  "Unenroll a player from a season"
  [request]
  (try
    (let [season-id (get-in request [:params :id])
          player-id (get-in request [:params :player-id])]
      (if-not (and season-id player-id)
        (resp/error "Season ID and player ID are required" 400)
        (if (seasons-db/exists? season-id)
          (do
            (seasons-db/remove-player season-id player-id)
            (resp/success {:message "Player unenrolled"}))
          (resp/not-found "Season not found"))))
    (catch Exception e
      (handle-exception e "Failed to unenroll player"))))

(defn get-season-players
  "Get enrolled players for a specific season"
  [request]
  (try
    (let [season-id (get-in request [:params :id])
          season (seasons-db/find-by-id season-id)]
      (if season
        (let [player-ids (:enrolled-player-ids season [])
              players (players-db/find-by-ids player-ids)]
          (resp/success players))
        (resp/not-found "Season not found")))
    (catch Exception e
      (handle-exception e "Failed to list season enrolled players"))))

(defn finalize-season
  "Finalize a season and optionally award titles to winners.
   Payload: {:winner-player-ids [...] :titles-award-count N}"
  [request]
  (try
    (let [season-id (get-in request [:params :id])
          body (get-in request [:json-body] {})
          winner-player-ids (mapv resp/->object-id (get body :winner-player-ids []))
          titles-award-count (get body :titles-award-count 1)]
      (if (seasons-db/exists? season-id)
        (do
          (seasons-db/finalize! season-id winner-player-ids titles-award-count)
          (resp/success {:message "Season finalized"}))
        (resp/not-found "Season not found")))
    (catch Exception e
      (handle-exception e "Failed to finalize season"))))

