(ns galaticos.handlers.matches
  "Request handlers for match operations"
  (:require [galaticos.db.matches :as matches-db]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.championships :as championships-db]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private allowed-match-fields
  #{:championship-id :home-team-id :away-team-id :date :location :round
    :status :player-statistics :notes :opponent :venue :result :away-score})

(def ^:private required-match-fields
  #{:championship-id :player-statistics})

(def ^:private allowed-player-stat-fields
  #{:player-id :player-name :position :team-id :goals :assists :yellow-cards :red-cards
    :minutes-played})

(def ^:private required-player-stat-fields
  #{:player-id :team-id})

(defn- validate-player-stats [stats]
  (cond
    (not (sequential? stats)) {:error "player-statistics must be a non-empty vector"}
    (empty? stats) {:error "player-statistics must be a non-empty vector"}
    :else
    (let [validated (mapv (fn [stat]
                            (when-not (map? stat)
                              (throw (ex-info "Each player-statistics entry must be an object" {:status 400})))
                            (let [unknown (seq (remove allowed-player-stat-fields (keys stat)))
                                  missing (seq (filter #(nil? (get stat %)) required-player-stat-fields))]
                              (when unknown
                                (throw (ex-info (str "Unknown player-statistics fields: "
                                                     (str/join ", " unknown)) {:status 400})))
                              (when missing
                                (throw (ex-info (str "Missing required player-statistics fields: "
                                                     (str/join ", " missing)) {:status 400})))
                              (cond-> stat
                                (:player-id stat) (update :player-id resp/->object-id)
                                (:team-id stat) (update :team-id resp/->object-id))))
                          stats)]
      {:data validated})))

(defn- validate-players-enrolled [championship-id player-ids]
  (let [championship (championships-db/find-by-id championship-id)]
    (when-not championship
      (throw (ex-info "Championship not found" {:status 400})))
    (let [enrolled-ids (set (:enrolled-player-ids championship []))
          not-enrolled (remove #(contains? enrolled-ids %) player-ids)]
      (when (seq not-enrolled)
        (throw (ex-info (str "Players not enrolled in championship: "
                             (str/join ", " (map str not-enrolled)))
                        {:status 400}))))))

(defn- validate-match-body
  ([body] (validate-match-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-match-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-match-fields)))
           stats-present? (contains? body :player-statistics)]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else
         (try
           (let [{stats :data stats-error :error}
                 (when stats-present?
                   (validate-player-stats (:player-statistics body)))]
             (if stats-error
               {:error stats-error}
               {:data (cond-> body
                         (:championship-id body) (update :championship-id resp/->object-id)
                         (:home-team-id body) (update :home-team-id resp/->object-id)
                         (:away-team-id body) (update :away-team-id resp/->object-id)
                         stats (assoc :player-statistics stats))}))
           (catch Exception e
             {:error (.getMessage e)})))))))

(defn- handle-exception [e user-message]
  (if (= 400 (-> e ex-data :status))
    (resp/error (or user-message (.getMessage e)) 400)
    (do
      (log/error e user-message)
      (resp/server-error user-message))))

(defn list-matches
  "List all matches"
  [request]
  (try
    (let [championship-id (get-in request [:params :championship-id])
          matches (if championship-id
                    (matches-db/find-by-championship championship-id)
                    (matches-db/find-all))]
      (resp/success matches))
    (catch Exception e
      (handle-exception e "Failed to list matches"))))

(defn get-match
  "Get a single match by ID"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [match (matches-db/find-by-id id)]
        (resp/success match)
        (resp/not-found "Match not found")))
    (catch Exception e
      (handle-exception e "Failed to get match"))))

(defn create-match
  "Create a new match with player statistics"
  [request]
  (try
    (let [body (:json-body request)
          {:keys [error data]} (validate-match-body body)]
      (if error
        (resp/error error 400)
        (let [match-data (dissoc data :player-statistics)
              player-statistics (:player-statistics data)]
          (when (seq player-statistics)
            (validate-players-enrolled (:championship-id match-data)
                                       (map :player-id player-statistics)))
          (let [created (matches-db/create match-data player-statistics)]
          (agg/update-player-stats-for-match (str (:_id created)))
            (resp/success created 201)))))
    (catch Exception e
      (handle-exception e "Failed to create match"))))

(defn update-match
  "Update an existing match"
  [request]
  (try
    (let [id (get-in request [:params :id])
          updates (:json-body request)
          {:keys [error data]} (validate-match-body updates false)]
      (if error
        (resp/error error 400)
        (if (matches-db/exists? id)
          (let [existing (matches-db/find-by-id id)
                championship-id (or (:championship-id data) (:championship-id existing))
                player-statistics (or (:player-statistics data) (:player-statistics existing))]
            (when (seq player-statistics)
              (validate-players-enrolled championship-id (map :player-id player-statistics)))
            (matches-db/update-by-id id (assoc data :player-statistics player-statistics))
            (if-let [updated (matches-db/find-by-id id)]
              (do
                (agg/update-player-stats-for-match id)
                (resp/success updated))
              (resp/server-error "Failed to retrieve updated match")))
          (resp/not-found "Match not found"))))
    (catch Exception e
      (handle-exception e "Failed to update match"))))

(defn delete-match
  "Delete a match"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if (matches-db/exists? id)
        (do
          (matches-db/delete-by-id id)
          (agg/update-all-player-stats)
          (resp/success {:message "Match deleted"}))
        (resp/not-found "Match not found")))
    (catch Exception e
      (handle-exception e "Failed to delete match"))))

