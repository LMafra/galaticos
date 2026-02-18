(ns galaticos.handlers.championships
  "Request handlers for championship operations"
  (:require [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private allowed-championship-fields
  #{:name :season :status :format :start-date :end-date :location :notes :titles-count
    :max-players :enrolled-player-ids :winner-player-ids :finished-at})

(def ^:private required-championship-fields
  #{:name :season :titles-count})

(defn- normalize-championship-body [body]
  (let [max-players (:max-players body)]
    (cond-> body
      (some? max-players)
      (update :max-players (fn [value]
                             (cond
                               (number? value) value
                               (string? value) (let [trimmed (str/trim value)]
                                                 (when (not (str/blank? trimmed))
                                                   (Long/parseLong trimmed)))
                               :else value)))
      (:enrolled-player-ids body)
      (update :enrolled-player-ids (fn [ids]
                                     (mapv resp/->object-id ids))))))

(defn- validate-championship-body
  ([body] (validate-championship-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-championship-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-championship-fields)))
           normalized (normalize-championship-body body)
           max-players (:max-players normalized)]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         (and (some? max-players) (or (not (number? max-players)) (neg? max-players)))
         {:error "max-players must be a non-negative number"}
         :else {:data normalized})))))

(defn- handle-exception [e user-message]
  (if (= 400 (-> e ex-data :status))
    (resp/error (or user-message (.getMessage e)) 400)
    (do
      (log/error e user-message)
      (resp/server-error user-message))))

(defn list-championships
  "List all championships"
  [request]
  (try
    (let [status (get-in request [:params :status])
          championships (if status
                          (championships-db/find-all {:status status})
                          (championships-db/find-all))]
      (resp/success championships))
    (catch Exception e
      (handle-exception e "Failed to list championships"))))

(defn get-championship
  "Get a single championship by ID"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [championship (championships-db/find-by-id id)]
        (resp/success championship)
        (resp/not-found "Championship not found")))
    (catch Exception e
      (handle-exception e "Failed to get championship"))))

(defn create-championship
  "Create a new championship"
  [request]
  (try
    (let [championship-data (:json-body request)
          {:keys [error data]} (validate-championship-body championship-data)]
      (if error
        (resp/error error 400)
        (let [created (championships-db/create data)]
          (resp/success created 201))))
    (catch Exception e
      (handle-exception e "Failed to create championship"))))

(defn update-championship
  "Update an existing championship"
  [request]
  (try
    (let [id (get-in request [:params :id])
          updates (:json-body request)
          {:keys [error data]} (validate-championship-body updates false)]
      (if error
        (resp/error error 400)
        (if (championships-db/exists? id)
          (do
            (championships-db/update-by-id id data)
            (if-let [updated (championships-db/find-by-id id)]
              (resp/success updated)
              (resp/server-error "Failed to retrieve updated championship")))
          (resp/not-found "Championship not found"))))
    (catch Exception e
      (handle-exception e "Failed to update championship"))))

(defn delete-championship
  "Delete a championship"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if (championships-db/exists? id)
        (if (championships-db/has-matches? id)
          (resp/error "Cannot delete championship: it has associated matches. Please delete or reassign matches first." 409)
          (do
            (championships-db/delete-by-id id)
            (resp/success {:message "Championship deleted"})))
        (resp/not-found "Championship not found")))
    (catch Exception e
      (handle-exception e "Failed to delete championship"))))

(defn enroll-player
  "Enroll a player in a championship"
  [request]
  (try
    (let [championship-id (get-in request [:params :id])
          player-id (get-in request [:params :player-id])]
      (if-not (and championship-id player-id)
        (resp/error "Championship ID and player ID are required" 400)
        (if-let [championship (championships-db/find-by-id championship-id)]
          (if-let [_player (players-db/find-by-id player-id)]
            (let [enrolled (set (:enrolled-player-ids championship []))
                  max-players (:max-players championship)
                  already-enrolled? (contains? enrolled (resp/->object-id player-id))]
              (if (and max-players (>= (count enrolled) max-players) (not already-enrolled?))
                (resp/error "Championship has reached maximum number of players" 409)
                (do
                  (championships-db/add-player championship-id player-id)
                  (resp/success {:message "Player enrolled"}))))
            (resp/not-found "Player not found"))
          (resp/not-found "Championship not found"))))
    (catch Exception e
      (handle-exception e "Failed to enroll player"))))

(defn unenroll-player
  "Unenroll a player from a championship"
  [request]
  (try
    (let [championship-id (get-in request [:params :id])
          player-id (get-in request [:params :player-id])]
      (if-not (and championship-id player-id)
        (resp/error "Championship ID and player ID are required" 400)
        (if (championships-db/exists? championship-id)
          (do
            (championships-db/remove-player championship-id player-id)
            (resp/success {:message "Player unenrolled"}))
          (resp/not-found "Championship not found"))))
    (catch Exception e
      (handle-exception e "Failed to unenroll player"))))

(defn get-championship-players
  "Get enrolled players for a championship"
  [request]
  (try
    (let [championship-id (get-in request [:params :id])]
      (if-let [championship (championships-db/find-by-id championship-id)]
        (let [player-ids (:enrolled-player-ids championship [])
              players (players-db/find-by-ids player-ids)]
          (resp/success players))
        (resp/not-found "Championship not found")))
    (catch Exception e
      (handle-exception e "Failed to list enrolled players"))))


(defn finalize-championship
  "Finalize a championship and award titles to winners"
  [request]
  (try
    (let [championship-id (get-in request [:params :id])
          winner-player-ids (mapv resp/->object-id (get-in request [:json-body :winner-player-ids] []))]
      (if-let [championship (championships-db/find-by-id championship-id)]
        (let [status (or (:status championship) "active")
              finished-at (:finished-at championship)
              enrolled-ids (set (:enrolled-player-ids championship []))
              not-enrolled (remove #(contains? enrolled-ids %) winner-player-ids)]
          (cond
            (not= status "active")
            (resp/error "Only active championships can be finalized" 400)

            finished-at
            (resp/error "Championship has already been finalized" 400)

            (empty? winner-player-ids)
            (resp/error "At least one winner must be specified" 400)

            (seq not-enrolled)
            (resp/error "Winners must be enrolled in the championship" 400)

            :else
            (do
              (championships-db/update-by-id championship-id
                                             {:status "finished"
                                              :finished-at (java.util.Date.)
                                              :winner-player-ids winner-player-ids})
              (players-db/increment-titles winner-player-ids)
              (resp/success {:message "Championship finalized"}))))
        (resp/not-found "Championship not found")))
    (catch Exception e
      (handle-exception e "Failed to finalize championship"))))
