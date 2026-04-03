(ns galaticos.handlers.aggregations
  "Request handlers for aggregation and analytics endpoints"
  (:require [galaticos.db.aggregations :as agg]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :as resp]
            [monger.collection :as mc]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(defn- validate-data-integrity
  "Check data integrity and log warnings for common issues"
  []
  (try
    (let [all-matches (mc/find-maps (db) "matches" {})
          all-championship-ids (set (map :_id (mc/find-maps (db) "championships" {})))
          matches-without-championship (filter (fn [match]
                                                 (let [champ-id (:championship-id match)]
                                                   (and champ-id (not (contains? all-championship-ids champ-id)))))
                                               all-matches)
          matches-without-player-stats (filter (fn [match]
                                                 (let [player-stats (:player-statistics match)]
                                                   (or (nil? player-stats)
                                                       (not (sequential? player-stats))
                                                       (empty? player-stats))))
                                               all-matches)
          matches-with-empty-player-stats (filter (fn [match]
                                                    (let [player-stats (:player-statistics match)]
                                                      (and (sequential? player-stats) (empty? player-stats))))
                                                  all-matches)]
      (when (seq matches-without-championship)
        (log/warn (str "Found " (count matches-without-championship)
                      " match(es) referencing non-existent championships. "
                      "Match IDs: " (map :_id matches-without-championship))))
      (when (seq matches-without-player-stats)
        (log/warn (str "Found " (count matches-without-player-stats)
                      " match(es) missing or empty player-statistics. "
                      "Match IDs: " (map :_id matches-without-player-stats))))
      (when (seq matches-with-empty-player-stats)
        (log/warn (str "Found " (count matches-with-empty-player-stats)
                      " match(es) with empty player-statistics arrays. "
                      "Match IDs: " (map :_id matches-with-empty-player-stats)))))
    (catch Exception e
      (log/warn e "Error during data integrity validation - continuing anyway"))))

(defn player-stats-by-championship
  "Get player statistics for a championship"
  [request]
  (try
    (let [championship-id (get-in request [:params :championship-id])]
      (if championship-id
        (let [stats (agg/player-stats-by-championship championship-id)]
          (resp/success stats))
        (resp/error "Championship ID required")))
    (catch Exception e
      (log/error e "Error getting player stats by championship")
      (resp/server-error "Failed to get player statistics"))))

(defn championship-table-leaderboards
  "Top 5 per metric from table/hybrid aggregated-stats.by-championship for a championship root."
  [request]
  (try
    (let [championship-id (get-in request [:params :id])]
      (if (str/blank? (str championship-id))
        (resp/error "Championship ID required")
        (resp/success (agg/championship-table-leaderboards championship-id))))
    (catch Exception e
      (log/error e "Error getting championship table leaderboards")
      (resp/server-error "Failed to get championship leaderboards"))))

(defn dashboard-stats
  "Get dashboard statistics"
  [_request]
  (try
    (log/info "Fetching dashboard stats...")
    ;; Validate data integrity and log warnings
    (validate-data-integrity)
    ;; Collect diagnostic information about database state
    (let [total-matches (mc/count (db) "matches")
          matches-with-championship-id (mc/count (db) "matches" {:championship-id {:$exists true :$ne nil}})
          matches-with-player-stats (mc/count (db) "matches" {:player-statistics {:$exists true :$ne [] :$type "array"}})
          matches-with-both (mc/count (db) "matches" {:championship-id {:$exists true :$ne nil}
                                                       :player-statistics {:$exists true :$ne [] :$type "array"}})
          total-championships (mc/count (db) "championships")
          total-teams (mc/count (db) "teams")
          total-players (mc/count (db) "players" {:active true})
          players-with-stats (mc/count (db) "players" {:active true "aggregated-stats" {:$exists true}})]
      (log/info (str "Database diagnostic - Total matches: " total-matches
                     ", Matches with championship-id: " matches-with-championship-id
                     ", Matches with player-statistics: " matches-with-player-stats
                     ", Matches with both: " matches-with-both
                     ", Total championships: " total-championships
                     ", Total teams: " total-teams
                     ", Active players: " total-players
                     ", Players with aggregated-stats: " players-with-stats)))
    ;; Fetch the stats directly - the aggregation functions handle empty data gracefully
    (let [total-teams (mc/count (db) "teams")
          players-total (agg/total-registered-players)
          seasons-count (agg/total-seasons)
          player-goals-total (agg/total-player-goals-tallied)
          championships (try
                          (log/info "Fetching championship comparison...")
                          (let [result (agg/championship-comparison)]
                            (log/info (str "Championship comparison returned " (count result) " championships"))
                            result)
                          (catch Exception e
                            (log/error e "Error in championship-comparison")
                            []))
          top-goals (try
                      (log/info "Fetching top goals players...")
                      (let [result (agg/top-players-by-metric :goals 10)]
                        (log/info (str "Top goals returned " (count result) " players"))
                        result)
                      (catch Exception e
                        (log/error e "Error in top-players-by-metric for goals")
                        []))
          top-assists (try
                        (log/info "Fetching top assists players...")
                        (let [result (agg/top-players-by-metric :assists 10)]
                          (log/info (str "Top assists returned " (count result) " players"))
                          result)
                        (catch Exception e
                          (log/error e "Error in top-players-by-metric for assists")
                          []))
          top-matches (try
                        (log/info "Fetching top matches players...")
                        (let [result (agg/top-players-by-metric :games 10)]
                          (log/info (str "Top matches returned " (count result) " players"))
                          result)
                        (catch Exception e
                          (log/error e "Error in top-players-by-metric for games")
                          []))
          top-titles (try
                       (log/info "Fetching top titles players...")
                       (let [result (agg/top-players-by-metric :titles 10)]
                         (log/info (str "Top titles returned " (count result) " players"))
                         result)
                       (catch Exception e
                         (log/error e "Error in top-players-by-metric for titles")
                         []))]
      (log/info (str "Dashboard stats retrieved - Championships: " (count championships)
                     ", Top goals: " (count top-goals)
                     ", Top assists: " (count top-assists)
                     ", Top matches: " (count top-matches)
                     ", Top titles: " (count top-titles)))
      (when (empty? championships)
        (log/warn "No championships found - ensure matches with player-statistics exist in database"))
      (when (empty? top-goals)
        (log/warn "No top goals players found - check if players have aggregated-stats.total.goals populated"))
      (when (empty? top-assists)
        (log/warn "No top assists players found - check if players have aggregated-stats.total.assists populated"))
      (when (empty? top-matches)
        (log/warn "No top matches players found - check if players have aggregated-stats.total.games populated"))
      (when (empty? top-titles)
        (log/warn "No top titles players found - check if players have aggregated-stats.total.titles populated"))
      (let [response-data {:championships championships
                           :top-goals top-goals
                           :top-assists top-assists
                           :top-matches top-matches
                           :top-titles top-titles
                           :teams-count total-teams
                           :players-total players-total
                           :player-goals-total player-goals-total
                           :seasons-count seasons-count}]
        (log/info (str "Dashboard stats response data: " (pr-str response-data)))
        (log/info (str "Response structure - Championships count: " (count championships)
                       ", Top goals count: " (count top-goals)
                       ", Top assists count: " (count top-assists)
                       ", Top matches count: " (count top-matches)
                       ", Top titles count: " (count top-titles)
                       ", Teams count: " total-teams
                       ", Players total: " players-total
                       ", Player goals total: " player-goals-total
                       ", Seasons count: " seasons-count))
        (when (seq championships)
          (log/info (str "Sample championship data (first): " (pr-str (first championships)))))
        (when (seq top-goals)
          (log/info (str "Sample top goals player (first): " (pr-str (first top-goals)))))
        (when (seq top-assists)
          (log/info (str "Sample top assists player (first): " (pr-str (first top-assists)))))
        (when (seq top-matches)
          (log/info (str "Sample top matches player (first): " (pr-str (first top-matches)))))
        (when (seq top-titles)
          (log/info (str "Sample top titles player (first): " (pr-str (first top-titles)))))
        (resp/success response-data)))
    (catch Exception e
      (log/error e "Error getting dashboard stats")
      (log/error "Exception message:" (.getMessage e))
      (log/error "Exception class:" (class e))
      (log/error "Stack trace:" (with-out-str (.printStackTrace e)))
      (when-let [cause (.getCause e)]
        (log/error "Caused by:" (.getMessage cause)))
      (resp/server-error (str "Failed to get dashboard statistics: " (.getMessage e))))))

(defn avg-goals-by-position
  "Get average goals by position for a championship"
  [request]
  (try
    (let [championship-id (get-in request [:params :championship-id])]
      (if championship-id
        (let [stats (agg/avg-goals-by-position championship-id)]
          (resp/success stats))
        (resp/error "Championship ID required")))
    (catch Exception e
      (log/error e "Error getting avg goals by position")
      (resp/server-error "Failed to get average goals by position"))))

(defn championship-tab-stats
  "Single round-trip for stats page: player rows + position averages for a championship."
  [request]
  (try
    (let [championship-id (get-in request [:params :championship-id])]
      (if championship-id
        (resp/success {:player-stats (agg/player-stats-by-championship championship-id)
                       :position-stats (agg/avg-goals-by-position championship-id)})
        (resp/error "Championship ID required")))
    (catch Exception e
      (log/error e "Error getting championship tab stats")
      (resp/server-error "Failed to get championship tab stats"))))

(defn player-performance-evolution
  "Get player performance evolution"
  [request]
  (try
    (let [player-id (get-in request [:params :player-id])]
      (if player-id
        (let [evolution (agg/player-performance-evolution player-id)]
          (resp/success evolution))
        (resp/error "Player ID required")))
    (catch Exception e
      (log/error e "Error getting player performance evolution")
      (resp/server-error "Failed to get player performance evolution"))))

(defn search-players
  "Search players with filters"
  [request]
  (try
    (let [filters (-> request
                      :params
                      (select-keys [:q :position :min-games :min-goals :min-age :max-age :sort-by :sort-order :page :limit])
                      (update :min-games #(when % (Integer/parseInt %)))
                      (update :min-goals #(when % (Integer/parseInt %)))
                      (update :min-age #(when % (Integer/parseInt %)))
                      (update :max-age #(when % (Integer/parseInt %)))
                      (update :page #(when % (Integer/parseInt %)))
                      (update :limit #(when % (Integer/parseInt %)))
                      (update :sort-by #(when % (keyword %)))
                      (update :sort-order #(when % (Integer/parseInt %))))]
      (resp/success (agg/search-players filters)))
    (catch Exception e
      (log/error e "Error searching players")
      (resp/server-error "Failed to search players"))))

(defn championship-comparison
  "Get championship comparison"
  [_request]
  (try
    (log/info "Fetching championship comparison...")
    (let [comparison (agg/championship-comparison)]
      (log/info (str "Championship comparison retrieved: " (count comparison) " championships"))
      (when (empty? comparison)
        (log/warn "No championships found - check if matches exist in database"))
      (resp/success comparison))
    (catch Exception e
      (log/error e "Error getting championship comparison")
      (resp/server-error "Failed to get championship comparison"))))

(defn top-players
  "Get top players by metric"
  [request]
  (try
    (let [metric (keyword (get-in request [:params :metric] "goals"))
          limit (Integer/parseInt (get-in request [:params :limit] "10"))
          championship-id (get-in request [:params :championship-id])]
      (log/info (str "Fetching top players - Metric: " metric ", Limit: " limit
                     (when championship-id (str ", Championship: " championship-id))))
      (let [players (if championship-id
                     (agg/top-players-by-metric metric limit :championship-id championship-id)
                     (agg/top-players-by-metric metric limit))]
        (log/info (str "Top players retrieved: " (count players) " players"))
        (when (empty? players)
          (log/warn (str "No players found for metric " metric " - check if players have aggregated-stats field populated")))
        (resp/success players)))
    (catch Exception e
      (log/error e "Error getting top players")
      (resp/server-error "Failed to get top players"))))

(defn reconcile-stats
  "Manual reconciliation: validate data integrity (log warnings) then recalculate all player aggregated stats."
  [_request]
  (try
    (log/info "Manual stats reconciliation started")
    (validate-data-integrity)
    (let [result (agg/update-all-player-stats)
          updated (get result :updated 0)]
      (log/info (str "Reconciliation completed: " updated " player(s) updated"))
      (resp/success {:updated updated
                     :message (str "Reconciliação concluída. " updated " jogador(es) atualizado(s).")}))
  (catch Exception e
    (log/error e "Error during stats reconciliation")
    (resp/server-error (str "Falha na reconciliação: " (.getMessage e))))))
