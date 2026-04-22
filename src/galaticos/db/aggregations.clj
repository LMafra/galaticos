(ns galaticos.db.aggregations
  "MongoDB aggregation pipeline functions for analytics and reporting"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]]
            [galaticos.util.string :as str-util]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.util.regex Pattern]))

;; After $unwind on player-statistics, $sum ignores non-numeric BSON (e.g. string goals).
(def ^:private coerce-player-stat-goals-assists
  {:$addFields
   {"player-statistics.goals"
    {:$convert {:input "$player-statistics.goals" :to "long" :onError 0 :onNull 0}}
    "player-statistics.assists"
    {:$convert {:input "$player-statistics.assists" :to "long" :onError 0 :onNull 0}}}})

(defn- safe-int
  [value]
  (cond
    (number? value) (int value)
    (nil? value) 0
    :else (try
            (int value)
            (catch Exception _ 0))))

(defn- sum-stat
  [entries stat-key]
  (reduce + 0 (map #(safe-int (get % stat-key)) entries)))

(defn- agg-entity-id-str
  "Normalize ObjectId or string ids for map keys / set membership."
  [x]
  (when x (str x)))

(defn- championship-id= [a b]
  (= (agg-entity-id-str a) (agg-entity-id-str b)))

(defn- match-player-id-str-set
  "Ids from match player-statistics for comparing to aggregation :player-id (ObjectId vs string)."
  [player-ids]
  (into #{} (comp (remove nil?) (map str)) player-ids))

(defn- stats-row-for-match-players?
  [player-stats match-pid-str-set]
  (let [pid (:player-id player-stats)]
    (boolean (and pid (contains? match-pid-str-set (str pid))))))

(defn- merge-championship-entry
  [existing-entry match-entry]
  (let [match-games (safe-int (:games match-entry))
        match-goals (safe-int (:goals match-entry))
        match-assists (safe-int (:assists match-entry))
        season (or (:season existing-entry) (:season match-entry))
        base {:championship-id (:championship-id existing-entry)
              :championship-name (or (:championship-name match-entry)
                                     (:championship-name existing-entry)
                                     "")
              :games (if (pos? match-games) match-games (safe-int (:games existing-entry)))
              :goals (if (pos? match-goals) match-goals (safe-int (:goals existing-entry)))
              ;; Matches may not have full assist data in legacy imports; keep existing in that case.
              :assists (if (pos? match-assists) match-assists (safe-int (:assists existing-entry)))
              :titles (safe-int (:titles existing-entry))}]
    (cond-> base
      (some? season) (assoc :season season))))

(defn- season-key-suffix
  "Normalized non-blank season label for merge keys, or nil when absent/unscoped."
  [season]
  (when (some? season)
    (let [s (str/trim (str season))]
      (when-not (str/blank? s) s))))

(defn- match-aggregate-key
  "Composite key for merging table/hybrid rows (championship + optional season label) with match rollups."
  [championship-id season]
  (str (agg-entity-id-str championship-id) "|" (or (season-key-suffix season) "")))

(defn- fanout-unscoped-rollups-into-match-map
  "Matches without season-id roll up with nil :season (key cid|). If the player has exactly
   one distinct non-blank :season row for that championship, attach that rollup to cid|season
   so it merges into the table row (common when season-id was missing on the match doc)."
  [match-map match-derived existing-by]
  (reduce
   (fn [mm entry]
     (if-not (and (:championship-id entry)
                  (str/blank? (season-key-suffix (:season entry))))
       mm
       (let [cid (:championship-id entry)
             seasons (->> existing-by
                          (filter #(championship-id= cid (:championship-id %)))
                          (map :season)
                          (keep season-key-suffix)
                          distinct
                          sort
                          vec)]
         (cond
           (not= 1 (count seasons)) mm
           :else
           (let [sole (first seasons)
                 scoped-k (match-aggregate-key cid sole)
                 unscoped-k (match-aggregate-key cid nil)]
             (if (contains? mm scoped-k)
               mm
               (-> mm
                   (assoc scoped-k entry)
                   (dissoc unscoped-k))))))))
   match-map
   match-derived))

(defn- drop-unscoped-when-scoped-present
  "Remove cid| bucket when cid|season exists so match-only does not add a duplicate row."
  [match-map]
  (let [entries (vec match-map)]
    (reduce
     (fn [mm [k _v]]
       (if-not (str/ends-with? k "|")
         mm
         (let [cid-prefix (subs k 0 (dec (count k)))
               prefix-bar (str cid-prefix "|")
               has-scoped (some (fn [[k2 _v2]]
                                  (and (str/starts-with? k2 prefix-bar)
                                       (> (count k2) (count prefix-bar))))
                                entries)]
           (if has-scoped (dissoc mm k) mm))))
     match-map
     entries)))

(defn- merge-aggregated-stats
  [existing match-derived]
  (let [existing (or existing {})
        existing-by (vec (or (:by-championship existing) []))
        match-map (-> (into {}
                            (keep (fn [entry]
                                    (when-let [cid (:championship-id entry)]
                                      [(match-aggregate-key cid (:season entry)) entry])))
                            match-derived)
                      (fanout-unscoped-rollups-into-match-map match-derived existing-by)
                      (drop-unscoped-when-scoped-present))
        existing-keys (into #{}
                            (map (fn [e]
                                   (match-aggregate-key (:championship-id e) (:season e))))
                            existing-by)
        merged-existing (mapv (fn [entry]
                                (let [k (match-aggregate-key (:championship-id entry) (:season entry))]
                                  (if (contains? match-map k)
                                    (merge-championship-entry entry (get match-map k))
                                    (update entry :titles safe-int))))
                              existing-by)
        match-only (for [entry match-derived
                         :let [k (match-aggregate-key (:championship-id entry) (:season entry))]
                         :when (and (not (contains? existing-keys k))
                                    (contains? match-map k))]
                     (cond-> {:championship-id (:championship-id entry)
                              :championship-name (or (:championship-name entry) "")
                              :games (safe-int (:games entry))
                              :goals (safe-int (:goals entry))
                              :assists (safe-int (:assists entry))
                              :titles 0}
                       (some? (season-key-suffix (:season entry)))
                       (assoc :season (:season entry))))
        merged-by (vec (concat merged-existing match-only))]
    {:total {:games (sum-stat merged-by :games)
             :goals (sum-stat merged-by :goals)
             :assists (sum-stat merged-by :assists)
             :titles (sum-stat merged-by :titles)}
     :by-championship merged-by}))

(defn player-stats-by-championship
  "Get aggregated player statistics for a specific championship"
  [championship-id]
  (try
    (mc/aggregate (db) "matches"
                  [{:$match {:championship-id (->object-id championship-id)}}
                   {:$unwind "$player-statistics"}
                   coerce-player-stat-goals-assists
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
                   coerce-player-stat-goals-assists
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
                  ;; $year/$month/$week on missing or non-date `date` throws — skip bad docs.
                  [{:$match {:date {:$type "date"}
                             :player-statistics {:$type "array"}}}
                   {:$unwind {:path "$player-statistics"
                              :preserveNullAndEmptyArrays false}}
                   coerce-player-stat-goals-assists
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
   Groups by (player, championship, season-id) so table rows keyed by :season align with match rollups.
   Returns data structure ready to update players collection."
  []
  (try
    (mc/aggregate (db) "matches"
                  [{:$unwind "$player-statistics"}
                   coerce-player-stat-goals-assists
                   {:$group {:_id {:player-id "$player-statistics.player-id"
                                   :championship-id "$championship-id"
                                   :season-id "$season-id"}
                            :games {:$sum 1}
                            :goals {:$sum "$player-statistics.goals"}
                            :assists {:$sum "$player-statistics.assists"}}}
                   {:$lookup {:from "championships"
                              :localField "_id.championship-id"
                              :foreignField "_id"
                              :as "championship"}}
                   {:$unwind {:path "$championship"
                              :preserveNullAndEmptyArrays true}}
                   {:$lookup {:from "seasons"
                              :localField "_id.season-id"
                              :foreignField "_id"
                              :as "season-doc"}}
                   {:$addFields {:season-label {:$ifNull [{:$arrayElemAt ["$season-doc.season" 0]} nil]}}}
                   {:$group {:_id "$_id.player-id"
                            :by-championship
                            {:$push {:championship-id "$_id.championship-id"
                                     :championship-name {:$ifNull ["$championship.name" ""]}
                                     :season "$season-label"
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
        (let [pid (->object-id (:player-id player-stats))]
          (when-let [player (mc/find-one-as-map (db) "players" {:_id pid})]
            (let [existing-stats (:aggregated-stats player)
                  match-by-championship (get-in player-stats [:aggregated-stats :by-championship] [])
                  merged-stats (merge-aggregated-stats existing-stats match-by-championship)]
              (mc/update (db) "players"
                         {:_id pid}
                         {:$set {:aggregated-stats merged-stats
                                :updated-at (java.util.Date.)}})))))
      {:status :success :updated (count stats-data)})
    (catch Exception e
      (log/error e "Error updating all player stats")
      (throw e))))

(defn update-player-stats-for-match
  "Update aggregated stats for players involved in a specific match"
  [match-id]
  (try
    (if-let [match (mc/find-one-as-map (db) "matches" {:_id (->object-id match-id)})]
      (let [match-pid-set (match-player-id-str-set (map :player-id (:player-statistics match)))
            stats-data (update-aggregated-stats-pipeline)]
        (doseq [player-stats stats-data
                :when (stats-row-for-match-players? player-stats match-pid-set)]
          (let [pid (->object-id (:player-id player-stats))]
            (when-let [player (mc/find-one-as-map (db) "players" {:_id pid})]
              (let [existing-stats (:aggregated-stats player)
                    match-by-championship (get-in player-stats [:aggregated-stats :by-championship] [])
                    merged-stats (merge-aggregated-stats existing-stats match-by-championship)]
                (mc/update (db) "players"
                           {:_id pid}
                           {:$set {:aggregated-stats merged-stats
                                   :updated-at (java.util.Date.)}})))))
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
                          ;; search-name é o ideal (normalizado); name/nickname cobrem docs legados sem :search-name
                          (assoc base-match
                                 :$or [{:search-name {:$regex pattern}}
                                       {:name {:$regex pattern :$options "i"}}
                                       {:nickname {:$regex pattern :$options "i"}}])))
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

(defn- championship-match-metrics
  []
  (let [result (mc/aggregate (db) "matches"
                             [{:$match {:championship-id {:$exists true :$ne nil}
                                        :player-statistics {:$exists true}}}
                              {:$unwind {:path "$player-statistics"
                                         :preserveNullAndEmptyArrays false}}
                              coerce-player-stat-goals-assists
                              {:$group {:_id "$championship-id"
                                        :match-ids {:$addToSet "$_id"}
                                        :total-goals {:$sum "$player-statistics.goals"}
                                        :total-assists {:$sum "$player-statistics.assists"}
                                        :unique-players {:$addToSet "$player-statistics.player-id"}}}
                              {:$project {:_id 0
                                          :championship-id "$_id"
                                          :matches-count {:$size "$match-ids"}
                                          :players-count {:$size "$unique-players"}
                                          :total-goals 1
                                          :total-assists 1}}])]
    (into {}
          (map (fn [entry] [(:championship-id entry) entry]))
          result)))

(defn- championship-player-metrics
  []
  (let [result (mc/aggregate (db) "players"
                             [{:$match {:active true
                                        "aggregated-stats.by-championship" {:$exists true :$ne []}}}
                              {:$unwind "$aggregated-stats.by-championship"}
                              {:$match {"aggregated-stats.by-championship.championship-id" {:$exists true :$ne nil}}}
                              {:$group {:_id "$aggregated-stats.by-championship.championship-id"
                                        :total-goals {:$sum "$aggregated-stats.by-championship.goals"}
                                        :total-assists {:$sum "$aggregated-stats.by-championship.assists"}
                                        ;; "games" in aggregated-stats.by-championship is per-player.
                                        ;; Summing it inflates championship matches-count by player multiplicity.
                                        :max-games {:$max "$aggregated-stats.by-championship.games"}
                                        :unique-players {:$addToSet "$_id"}}}
                              {:$project {:_id 0
                                          :championship-id "$_id"
                                          :matches-count "$max-games"
                                          :players-count {:$size "$unique-players"}
                                          :total-goals 1
                                          :total-assists 1}}])]
    (into {}
          (map (fn [entry] [(:championship-id entry) entry]))
          result)))

(defn- championship-season-metrics
  []
  (let [result (mc/aggregate (db) "seasons"
                             [{:$match {:championship-id {:$exists true :$ne nil}}}
                              {:$group {:_id "$championship-id"
                                        :matches-count {:$sum {:$ifNull ["$matches-count" 0]}}
                                        :player-ids-buckets {:$addToSet {:$ifNull ["$enrolled-player-ids" []]}}}}
                              {:$unwind {:path "$player-ids-buckets"
                                         :preserveNullAndEmptyArrays true}}
                              {:$unwind {:path "$player-ids-buckets"
                                         :preserveNullAndEmptyArrays true}}
                              {:$group {:_id "$_id"
                                        :matches-count {:$first "$matches-count"}
                                        :unique-player-ids {:$addToSet "$player-ids-buckets"}}}
                              {:$project {:_id 0
                                          :championship-id "$_id"
                                          :matches-count 1
                                          :players-count {:$size "$unique-player-ids"}}}])]
    (into {}
          (map (fn [entry] [(:championship-id entry) entry]))
          result)))

(defn championship-comparison
  "Compare statistics across championships including championships without matches."
  []
  (try
    (let [championships (mc/find-maps (db) "championships" {})
          match-metrics (championship-match-metrics)
          season-metrics (championship-season-metrics)
          player-metrics (championship-player-metrics)
          result (->> championships
                      (map (fn [championship]
                             (let [championship-id (:_id championship)
                                   match-stats (get match-metrics championship-id)
                                   season-stats (get season-metrics championship-id)
                                   player-stats (get player-metrics championship-id)
                                   baseline-stats (or match-stats player-stats)
                                   matches-count (safe-int (or (:matches-count match-stats)
                                                               (:matches-count season-stats)
                                                               (:matches-count player-stats)))
                                   total-goals (safe-int (:total-goals baseline-stats))
                                   total-assists (safe-int (:total-assists baseline-stats))
                                   players-count (safe-int (or (:players-count match-stats)
                                                               (:players-count season-stats)
                                                               (:players-count player-stats)))]
                               {:championship-id championship-id
                                :championship-name (:name championship)
                                :championship-format (:format championship)
                                :matches-count matches-count
                                :players-count players-count
                                :total-goals total-goals
                                :total-assists total-assists
                                :avg-goals-per-match (if (pos? matches-count)
                                                       (/ total-goals matches-count)
                                                       0)})))
                      (sort-by :matches-count >)
                      vec)]
      (log/info (str "championship-comparison - Results: " (count result)))
      result)
    (catch Exception e
      (log/error e "Error in championship comparison")
      (log/error "Exception details: " (with-out-str (.printStackTrace e)))
      [])))

(defn total-registered-players
  "Active player documents (elenco cadastrado — alinha com o seed e exclui inativos)."
  []
  (mc/count (db) "players" {:active true}))

(defn total-seasons
  "All season documents across championships."
  []
  (mc/count (db) "seasons" {}))

(defn total-player-goals-tallied
  "Sum of aggregated-stats.total.goals over active players (totais nas fichas, não por campeonato)."
  []
  (try
    (let [rows (mc/aggregate (db) "players"
                             [{:$match {:active true}}
                              {:$group {:_id nil
                                        :total {:$sum {:$ifNull ["$aggregated-stats.total.goals" 0]}}}}])
          row (first rows)]
      (long (or (:total row) 0)))
    (catch Exception e
      (log/error e "Error summing aggregated player goals")
      0)))

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

(defn championship-table-leaderboards
  "Top 5 players per metric from aggregated-stats.by-championship for a championship root
  (sums games, goals, assists, titles across all season rows for that championship-id)."
  [championship-id]
  (try
    (let [cid (->object-id championship-id)
          pipeline [{:$match {:active true
                             "aggregated-stats.by-championship" {:$exists true :$ne []}}}
                    {:$unwind "$aggregated-stats.by-championship"}
                    {:$match {"aggregated-stats.by-championship.championship-id" cid}}
                    {:$group
                     {:_id "$_id"
                      :name {:$first "$name"}
                      :goals {:$sum {:$ifNull ["$aggregated-stats.by-championship.goals" 0]}}
                      :assists {:$sum {:$ifNull ["$aggregated-stats.by-championship.assists" 0]}}
                      :games {:$sum {:$ifNull ["$aggregated-stats.by-championship.games" 0]}}
                      :titles {:$sum {:$ifNull ["$aggregated-stats.by-championship.titles" 0]}}}}
                    {:$facet
                     {:top-goals [{:$sort {:goals -1 :assists -1}}
                            {:$limit 5}
                            {:$project {:player-id "$_id"
                                       :name 1
                                       :goals 1
                                       :assists 1
                                       :games 1
                                       :titles 1
                                       :_id 0}}]
                      :top-assists [{:$sort {:assists -1 :goals -1}}
                              {:$limit 5}
                              {:$project {:player-id "$_id"
                                         :name 1
                                         :goals 1
                                         :assists 1
                                         :games 1
                                         :titles 1
                                         :_id 0}}]
                      :top-games [{:$sort {:games -1 :goals -1}}
                          {:$limit 5}
                          {:$project {:player-id "$_id"
                                     :name 1
                                     :goals 1
                                     :assists 1
                                     :games 1
                                     :titles 1
                                     :_id 0}}]
                      :top-titles [{:$sort {:titles -1 :goals -1}}
                           {:$limit 5}
                           {:$project {:player-id "$_id"
                                      :name 1
                                      :goals 1
                                      :assists 1
                                      :games 1
                                      :titles 1
                                      :_id 0}}]}}]
          out (first (mc/aggregate (db) "players" pipeline))
          out (merge {:top-goals [] :top-assists [] :top-games [] :top-titles []} out)]
      (select-keys out [:top-goals :top-assists :top-games :top-titles]))
    (catch Exception e
      (log/error e "Error building championship table leaderboards")
      (throw e))))
