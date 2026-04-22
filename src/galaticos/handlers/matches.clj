(ns galaticos.handlers.matches
  "Request handlers for match operations"
  (:require [galaticos.db.matches :as matches-db]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.db.seasons :as seasons-db]
            [galaticos.db.teams :as teams-db]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private allowed-match-fields
  #{:championship-id :season-id :home-team-id :away-team-id :date :location :round
    :status :player-statistics :notes :opponent :venue :result :away-score})

(def ^:private required-match-fields
  #{:championship-id :player-statistics})

(def ^:private allowed-player-stat-fields
  #{:player-id :player-name :position :team-id :goals :assists :yellow-cards :red-cards
    :minutes-played})

(def ^:private required-player-stat-fields
  #{:player-id :team-id})

(defn- validate-player-team-coherence
  "BRM-09: Validate that `team-id` in match stats matches the player's current team,
   and that the player is listed in the team's `active-player-ids`."
  [player-statistics]
  (when (seq player-statistics)
    (let [player-ids (map :player-id player-statistics)
          players-by-id (->> (players-db/find-by-ids player-ids)
                             (map (fn [p] [(:_id p) p]))
                             (into {}))]
      (doseq [{:keys [player-id team-id]} player-statistics]
        (let [player (get players-by-id player-id)
              team (teams-db/find-by-id team-id)]
          (when-not player
            (throw (ex-info (str "Player not found: " (str player-id)) {:status 400})))
          (when-not team
            (throw (ex-info (str "Team not found: " (str team-id)) {:status 400})))
          (let [player-team-id (:team-id player)]
            (when-not player-team-id
              (throw (ex-info (str "Player has no team assigned: " (or (:name player) (str player-id)))
                              {:status 400})))
            (when (not= player-team-id team-id)
              (throw (ex-info (str "Invalid team-id for player " (or (:name player) (str player-id))
                                   ": expected " (str player-team-id) ", got " (str team-id))
                              {:status 400}))))
          (when-not (contains? (set (:active-player-ids team)) player-id)
            (throw (ex-info (str "Player " (or (:name player) (str player-id))
                                 " is not active in team " (or (:name team) (str team-id)))
                            {:status 400}))))))))

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

(defn- validate-players-enrolled
  "Validate that `player-ids` are enrolled in the targeted season.
   If `season-id` is provided, validate against that season; otherwise validate
   against the active season for the championship. Fallback to legacy
   championship.enrolled-player-ids if no season info exists."
  [championship-id season-id player-ids]
  (let [season (or (when season-id (seasons-db/find-by-id season-id))
                   (seasons-db/find-active-by-championship championship-id))]
    (if season
      (let [enrolled-ids (set (:enrolled-player-ids season []))
            not-enrolled (remove enrolled-ids player-ids)]
        (when (seq not-enrolled)
          (throw (ex-info (str "Players not enrolled in season: "
                               (str/join ", " (map str not-enrolled)))
                          {:status 400}))))
      (let [championship (championships-db/find-by-id championship-id)]
        (when-not championship
          (throw (ex-info "Championship not found" {:status 400})))
        (let [enrolled-ids (set (:enrolled-player-ids championship []))
              not-enrolled (remove enrolled-ids player-ids)]
          (when (seq not-enrolled)
            (throw (ex-info (str "Players not enrolled in championship: "
                                 (str/join ", " (map str not-enrolled)))
                            {:status 400}))))))))

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
                        (:season-id body) (update :season-id resp/->object-id)
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

(defn- enrich-match-view
  "Attach :player-name and :team-name to each player-statistics row for read APIs (not persisted)."
  [match]
  (if-not (seq (:player-statistics match))
    match
    (let [stats (:player-statistics match)
          p-ids (distinct (keep :player-id stats))
          t-ids (distinct (keep :team-id stats))
          players (players-db/find-by-ids p-ids)
          teams (if (seq t-ids) (teams-db/find-by-ids t-ids) [])
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

(defn list-matches
  "List all matches"
  [request]
  (try
    (let [championship-id (get-in request [:params :championship-id])
          season-id (get-in request [:params :season-id])
          matches (cond
                    season-id (matches-db/find-by-season season-id)
                    championship-id (matches-db/find-by-championship championship-id)
                    :else (matches-db/find-all))]
      (resp/success matches))
    (catch Exception e
      (handle-exception e "Failed to list matches"))))

(defn get-match
  "Get a single match by ID"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [match (matches-db/find-by-id id)]
        (resp/success (enrich-match-view match))
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
              player-statistics (:player-statistics data)
              championship-id (:championship-id match-data)
              active-season (seasons-db/find-active-by-championship championship-id)
              season-id (when active-season (:_id active-season))
              match-data (cond-> match-data
                           season-id (assoc :season-id season-id))]
          (when (seq player-statistics)
            (validate-players-enrolled championship-id season-id (map :player-id player-statistics)))
          (validate-player-team-coherence player-statistics)
          (let [created (matches-db/create match-data player-statistics)]
            (when season-id
              (seasons-db/add-match season-id (:_id created)))
            ;; Full recompute from all matches in DB (same as delete) so every player stays consistent.
            (agg/update-all-player-stats)
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
                season-id (or (:season-id data)
                              (:season-id existing)
                              (some-> (seasons-db/find-active-by-championship championship-id) :_id))
                player-statistics (or (:player-statistics data) (:player-statistics existing))]
            (when (seq player-statistics)
              (validate-players-enrolled championship-id season-id
                                         (map :player-id player-statistics)))
            (validate-player-team-coherence player-statistics)
            (matches-db/update-by-id id
                                      (cond-> (assoc data :player-statistics player-statistics)
                                        season-id (assoc :season-id season-id)))
            (if-let [updated (matches-db/find-by-id id)]
              (do
                (when season-id
                  (seasons-db/add-match season-id (:_id updated)))
                (agg/update-all-player-stats)
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
        (let [existing (matches-db/find-by-id id)
              season-id (:season-id existing)]
          (matches-db/delete-by-id id)
          (when season-id
            (seasons-db/remove-match season-id (:_id existing)))
          (agg/update-all-player-stats)
          (resp/success {:message "Match deleted"}))
        (resp/not-found "Match not found")))
    (catch Exception e
      (handle-exception e "Failed to delete match"))))

