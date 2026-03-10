(ns galaticos.db.aggregations
  "MongoDB aggregation pipeline functions for analytics and reporting"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]]
            [galaticos.util.string :as str-util]
            [clojure.tools.logging :as log])
  (:import [java.util.regex Pattern]))

(defn player-stats-by-championship
  "Get aggregated player statistics for a specific championship"
  [championship-id]
  (try
    (mc/aggregate (db) "matches"
                  [{:$match {:championship-id (->object-id championship-id)}}
                   {:$unwind "$player-statistics"}
                   {:$group {:_id "$player-statistics.player-id"
                            :games {:$sum 1}
                            :goals {:$sum "$player-statistics.goals"}
                            :assists {:$sum "$player-statistics.assists"}
                            :yellow-cards {:$sum "$player-statistics.yellow-cards"}
                            :red-cards {:$sum "$player-statistics.red-cards"}
                            :player-name {:$first "$player-statistics.player-name"}
                            :position {:$first "$player-statistics.position"}}}
                   {:$addFields {:goals-per-game
                                {:$cond
                                 [{:$gt ["$games" 0]}
                                  {:$divide ["$goals" "$games"]}
                                  0]}
                                :assists-per-game
                                {:$cond
                                 [{:$gt ["$games" 0]}
                                  {:$divide ["$assists" "$games"]}
                                  0]}}}
                   {:$sort {:goals -1 :assists -1}}])
    (catch Exception e
      (log/error e "Error getting player stats by championship")
      (throw e))))

(defn avg-goals-by-position
  "Get average goals per position for a championship"
  [championship-id]
  (try
    (mc/aggregate (db) "matches"
                  [{:$match {:championship-id (->object-id championship-id)}}
                   {:$unwind "$player-statistics"}
                   {:$group {:_id "$player-statistics.position"
                            :avg-goals {:$avg "$player-statistics.goals"}
                            :total-goals {:$sum "$player-statistics.goals"}
                            :total-assists {:$sum "$player-statistics.assists"}
                            :player-count {:$sum 1}
                            :games-count {:$addToSet "$_id"}}}
                   {:$addFields {:unique-games {:$size "$games-count"}}}
                   {:$project {:position "$_id"
                              :avg-goals 1
                              :total-goals 1
                              :total-assists 1
                              :player-count 1
                              :unique-games 1
                              :_id 0}}
                   {:$sort {:avg-goals -1}}])
    (catch Exception e
      (log/error e "Error getting avg goals by position")
      (throw e))))

(defn player-performance-evolution
  "Get temporal evolution of player performance"
  [player-id]
  (try
    (mc/aggregate (db) "matches"
                  [{:$unwind "$player-statistics"}
                        {:$match {"player-statistics.player-id" (->object-id player-id)}}
                   {:$group {:_id {:year {:$year "$date"}
                                   :month {:$month "$date"}
                                   :week {:$week "$date"}}
                            :games {:$sum 1}
                            :goals {:$sum "$player-statistics.goals"}
                            :assists {:$sum "$player-statistics.assists"}
                            :yellow-cards {:$sum "$player-statistics.yellow-cards"}
                            :red-cards {:$sum "$player-statistics.red-cards"}}}
                   {:$addFields {:goals-per-game
                                {:$cond
                                 [{:$gt ["$games" 0]}
                                  {:$divide ["$goals" "$games"]}
                                  0]}}}
                   {:$sort {:_id.year 1 :_id.month 1 :_id.week 1}}])
    (catch Exception e
      (log/error e "Error getting player performance evolution")
      (throw e))))

(defn update-aggregated-stats-pipeline
  "Aggregation pipeline to calculate aggregated stats for all players.
   Returns data structure ready to update players collection."
  []
  (try
    (mc/aggregate (db) "matches"
                  [{:$unwind "$player-statistics"}
                   {:$group {:_id {:player-id "$player-statistics.player-id"
                                   :championship-id "$championship-id"}
                            :games {:$sum 1}
                            :goals {:$sum "$player-statistics.goals"}
                            :assists {:$sum "$player-statistics.assists"}}}
                   {:$lookup {:from "championships"
                            :localField "_id.championship-id"
                            :foreignField "_id"
                            :as "championship"}}
                   {:$unwind "$championship"}
                   {:$group {:_id "$_id.player-id"
                            :by-championship
                            {:$push {:championship-id "$_id.championship-id"
                                     :championship-name "$championship.name"
                                     :games "$games"
                                     :goals "$goals"
                                     :assists "$assists"}}
                            :total {:$push {:games "$games"
                                            :goals "$goals"
                                            :assists "$assists"}}}}
                   {:$project {:player-id "$_id"
                              :aggregated-stats
                              {:total {:games {:$sum "$total.games"}
                                       :goals {:$sum "$total.goals"}
                                       :assists {:$sum "$total.assists"}}
                               :by-championship "$by-championship"}}}])
    (catch Exception e
      (log/error e "Error in update aggregated stats pipeline")
      (throw e))))

(defn update-all-player-stats
  "Update aggregated stats for all players based on matches"
  []
  (try
    (let [stats-data (update-aggregated-stats-pipeline)]
      (doseq [player-stats stats-data]
        (mc/update (db) "players"
                   {:_id (:player-id player-stats)}
                   {:$set {:aggregated-stats (:aggregated-stats player-stats)
                           :updated-at (java.util.Date.)}}))
      {:status :success :updated (count stats-data)})
    (catch Exception e
      (log/error e "Error updating all player stats")
      (throw e))))

(defn update-player-stats-for-match
  "Update aggregated stats for players involved in a specific match"
  [match-id]
  (try
    (if-let [match (mc/find-one-as-map (db) "matches" {:_id (->object-id match-id)})]
      (let [player-ids (map :player-id (:player-statistics match))
            stats-data (update-aggregated-stats-pipeline)]
        (doseq [player-stats stats-data
                :when (some #{(:player-id player-stats)} player-ids)]
          (mc/update (db) "players"
                     {:_id (:player-id player-stats)}
                     {:$set {:aggregated-stats (:aggregated-stats player-stats)
                             :updated-at (java.util.Date.)}}))
        {:status :success})
      {:status :error :message "Match not found"})
    (catch Exception e
      (log/error e "Error updating player stats for match")
      (throw e))))

(defn search-players
  "Search players with multiple filters"
  [filters]
  (try
    (let [q (some-> (:q filters) str-util/normalize-text)
          limit (or (:limit filters) 25)
          page (max 1 (or (:page filters) 1))
          skip (* (dec page) limit)
          base-match (merge {:active true}
                            (select-keys filters [:position])
                            (when (:min-games filters)
                              {"aggregated-stats.total.games" {:$gte (:min-games filters)}})
                            (when (:min-goals filters)
                              {"aggregated-stats.total.goals" {:$gte (:min-goals filters)}}))
          match-stage (if (str-util/blank-normalized? q)
                        base-match
                        (let [pattern (str ".*" (Pattern/quote q) ".*")]
                          (assoc base-match :search-name {:$regex pattern})))
          sort-field (or (:sort-by filters) :goals-per-game)
          sort-order (or (:sort-order filters) -1)
          sort-map {:$sort (hash-map (keyword (name sort-field)) sort-order)}
          pipeline-raw [{:$match match-stage}
                        (when (or (:min-age filters) (:max-age filters))
                          {:$addFields {:age {:$cond
                                             [{:$ne ["$birth-date" nil]}
                                              {:$subtract [{:$year (java.util.Date.)}
                                                           {:$year "$birth-date"}]}
                                              nil]}}})
                        (when (or (:min-age filters) (:max-age filters))
                          (let [age-conditions (cond-> {}
                                                   (:min-age filters) (assoc :$gte (:min-age filters))
                                                   (:max-age filters) (assoc :$lte (:max-age filters)))]
                            {:$match {:age age-conditions}}))
                        {:$addFields {:goals-per-game
                                      {:$cond
                                       [{:$gt ["$aggregated-stats.total.games" 0]}
                                        {:$divide ["$aggregated-stats.total.goals"
                                                   "$aggregated-stats.total.games"]}
                                        0]}
                                      :assists-per-game
                                      {:$cond
                                       [{:$gt ["$aggregated-stats.total.games" 0]}
                                        {:$divide ["$aggregated-stats.total.assists"
                                                   "$aggregated-stats.total.games"]}
                                        0]}}}
                        sort-map
                        {:$skip skip}
                        {:$limit limit}]
          pipeline (remove nil? pipeline-raw)]
      (mc/aggregate (db) "players" pipeline))
    (catch Exception e
      (log/error e "Error searching players")
      (throw e))))

(defn championship-comparison
  "Compare statistics across different championships.
   Aggregates from players' aggregated-stats since matches may be empty."
  []
  (try
    ;; First try to aggregate from matches if they exist
    (let [total-matches (mc/count (db) "matches")
          matches-with-data (mc/count (db) "matches" {:championship-id {:$exists true :$ne nil}
                                                       :player-statistics {:$exists true}})]
      (if (and (> total-matches 0) (> matches-with-data 0))
        ;; Use matches collection if data exists
        (let [result (mc/aggregate (db) "matches"
                                  [{:$match {:championship-id {:$exists true :$ne nil}
                                             :player-statistics {:$exists true}}}
                                   {:$lookup {:from "championships"
                                              :localField "championship-id"
                                              :foreignField "_id"
                                              :as "championship"}}
                                   {:$addFields {:championship-found {:$gt [{:$size "$championship"} 0]}}}
                                   {:$match {:championship-found true}}
                                   {:$unwind {:path "$championship"
                                             :preserveNullAndEmptyArrays false}}
                                   {:$unwind {:path "$player-statistics"
                                             :preserveNullAndEmptyArrays false}}
                                   {:$group {:_id "$championship-id"
                                             :championship-name {:$first "$championship.name"}
                                             :championship-format {:$first "$championship.format"}
                                             :total-matches {:$addToSet "$_id"}
                                             :total-goals {:$sum "$player-statistics.goals"}
                                             :total-assists {:$sum "$player-statistics.assists"}
                                             :unique-players {:$addToSet "$player-statistics.player-id"}}}
                                   {:$addFields {:matches-count {:$size "$total-matches"}
                                                 :players-count {:$size "$unique-players"}
                                                 :avg-goals-per-match
                                                 {:$cond
                                                  [{:$gt [{:$size "$total-matches"} 0]}
                                                   {:$divide ["$total-goals" {:$size "$total-matches"}]}
                                                   0]}}}
                                   {:$project {:championship-name 1
                                               :championship-format 1
                                               :matches-count 1
                                               :players-count 1
                                               :total-goals 1
                                               :total-assists 1
                                               :avg-goals-per-match 1
                                               :_id 0}}
                                   {:$sort {:matches-count -1}}])]
          (log/info (str "championship-comparison (from matches) - Results: " (count result)))
          result)
        ;; Otherwise aggregate from players' aggregated-stats
        (let [result (mc/aggregate (db) "players"
                                  [{:$match {:active true
                                             "aggregated-stats.by-championship" {:$exists true :$ne []}}}
                                   {:$unwind "$aggregated-stats.by-championship"}
                                   {:$match {"aggregated-stats.by-championship.championship-id" {:$exists true :$ne nil}}}
                                   {:$lookup {:from "championships"
                                              :localField "aggregated-stats.by-championship.championship-id"
                                              :foreignField "_id"
                                              :as "championship"}}
                                   {:$addFields {:championship-found {:$gt [{:$size "$championship"} 0]}}}
                                   {:$match {:championship-found true}}
                                   {:$unwind "$championship"}
                                   {:$group {:_id "$aggregated-stats.by-championship.championship-id"
                                             :championship-name {:$first "$championship.name"}
                                             :championship-format {:$first "$championship.format"}
                                             :total-goals {:$sum "$aggregated-stats.by-championship.goals"}
                                             :total-assists {:$sum "$aggregated-stats.by-championship.assists"}
                                             :total-games {:$sum "$aggregated-stats.by-championship.games"}
                                             :unique-players {:$addToSet "$_id"}}}
                                   {:$addFields {:matches-count "$total-games"
                                                 :players-count {:$size "$unique-players"}
                                                 :avg-goals-per-match
                                                 {:$cond
                                                  [{:$gt ["$total-games" 0]}
                                                   {:$divide ["$total-goals" "$total-games"]}
                                                   0]}}}
                                   {:$project {:championship-name 1
                                               :championship-format 1
                                               :matches-count 1
                                               :players-count 1
                                               :total-goals 1
                                               :total-assists 1
                                               :avg-goals-per-match 1
                                               :_id 0}}
                                   {:$sort {:matches-count -1}}])]
          (log/info (str "championship-comparison (from players aggregated-stats) - Results: " (count result)))
          result)))
    (catch Exception e
      (log/error e "Error in championship comparison")
      (log/error "Exception details: " (with-out-str (.printStackTrace e)))
      [])))

(defn top-players-by-metric
  "Get top players by a specific metric"
  [metric limit & {:keys [championship-id]}]
  (try
    (let [sort-field-path (if championship-id
                            (str "aggregated-stats.by-championship." (name metric))
                            (str "aggregated-stats.total." (name metric)))
          ;; Construct sort - use string as map key directly (Clojure supports this)
          sort-map {sort-field-path -1}
          sort-stage {:$sort sort-map}
          ;; Construct match stage - match active players with aggregated-stats
          base-match {:active true
                      "aggregated-stats" {:$exists true}}
          match-stage {:$match base-match}
          ;; Build pipeline
          pipeline (let [stages [match-stage]
                        stages (if championship-id
                                (concat stages
                                        [{:$unwind "$aggregated-stats.by-championship"}
                                         {:$match {"aggregated-stats.by-championship.championship-id"
                                                   (->object-id championship-id)}}])
                                stages)]
                     (concat stages
                             [sort-stage
                              {:$limit (or limit 10)}]))
          result (mc/aggregate (db) "players" pipeline)]
      ;; Log for debugging
      (log/debug (str "top-players-by-metric - Metric: " metric
                      ", Championship: " (or championship-id "all")
                      ", Sort field: " sort-field-path
                      ", Results: " (count result)))
      (when (empty? result)
        ;; Check if players exist and have aggregated-stats
        (let [total-players (mc/count (db) "players" {:active true})
              players-with-stats (mc/count (db) "players" 
                                          {:active true 
                                           "aggregated-stats" {:$exists true}})
              players-with-metric (mc/count (db) "players"
                                            {:active true
                                             "aggregated-stats" {:$exists true}
                                             sort-field-path {:$exists true}})]
          (log/warn (str "No top players found for metric " metric 
                        ". Total active players: " total-players
                        ", Players with aggregated-stats: " players-with-stats
                        ", Players with " sort-field-path ": " players-with-metric
                        ". Consider running update-all-player-stats if stats are missing."))))
      result)
    (catch Exception e
      (log/error e (str "Error getting top players by metric: " metric))
      (throw e))))

