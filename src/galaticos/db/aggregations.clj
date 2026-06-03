(ns galaticos.db.aggregations
  "MongoDB aggregation pipeline functions for analytics and reporting.
  
  ## Modelo Híbrido de Estatísticas
  
  Este módulo implementa o recálculo incremental de aggregated-stats usando
  merge-aggregated-stats do domain/analytics que preserva baseline histórico.
  
  Função principal: update-incremental-player-stats!
  - Defaults preservam baseline (:pre-match-stats)
  - :drop-stale-without-match-rollups? só em reconcile explícito
  
  Ver: docs/informacao/dominio/guia-partidas-temporadas-estatisticas-hibridas.md
  Testes: test/galaticos/db/aggregations_test.clj"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.domain.analytics :as analytics]
            [galaticos.util.response :refer [->object-id]]
            [galaticos.util.string :as str-util]
            [clojure.tools.logging :as log])
  (:import [java.util.regex Pattern]))

(def ^:private coerce-player-stat-numerics
  "After $unwind on player-statistics, $sum ignores non-numeric BSON (e.g. string goals)."
  {:$addFields
   {"player-statistics.goals"
    {:$convert {:input "$player-statistics.goals" :to "long" :onError 0 :onNull 0}}
    "player-statistics.assists"
    {:$convert {:input "$player-statistics.assists" :to "long" :onError 0 :onNull 0}}
    "player-statistics.yellow-cards"
    {:$convert {:input "$player-statistics.yellow-cards" :to "long" :onError 0 :onNull 0}}
    "player-statistics.red-cards"
    {:$convert {:input "$player-statistics.red-cards" :to "long" :onError 0 :onNull 0}}
    "player-statistics.minutes-played"
    {:$convert {:input "$player-statistics.minutes-played" :to "long" :onError 0 :onNull 0}}}})

(def ^:private coerce-player-stat-goals-assists coerce-player-stat-numerics)

(defn- safe-int
  [value]
  (cond
    (number? value) (int value)
    (nil? value) 0
    :else (try
            (int value)
            (catch Exception _ 0))))

(defn- ^{:clj-kondo/ignore [:unused-private-var]}
  match-player-id-str-set
  "Ids from match player-statistics for comparing to aggregation :player-id (ObjectId vs string)."
  [player-ids]
  (into #{} (comp (remove nil?) (map str)) player-ids))

(defn- ^{:clj-kondo/ignore [:unused-private-var]}
  stats-row-for-match-players?
  [player-stats match-pid-str-set]
  (let [pid (:player-id player-stats)]
    (boolean (and pid (contains? match-pid-str-set (str pid))))))

(defn- distinct-player-object-ids
  "Coerce a seq/set of player id values to distinct ObjectIds; skip nil/invalid."
  [ids]
  (vec
   (distinct
    (keep (fn [x]
            (when (some? x)
              (try (->object-id x) (catch Exception _ nil))))
          (if (or (sequential? ids) (set? ids))
            ids
            (when (some? ids) [ids]))))))

(defn combine-players-aggregated-stats
  "Fold multiple players' `:aggregated-stats` caches into one by summing per championship+season."
  [players]
  (analytics/combine-players-aggregated-stats players))

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

(defn- update-aggregated-stats-pipeline-vec
  "Mongo stages from optional match pre-filter through projected player rows.
  When `player-object-ids` is non-empty, only matches and stat rows for those player ids
  are scanned (incremental recompute for listed players)."
  [player-object-ids]
  (let [oids (seq (distinct (or player-object-ids [])))]
    (into
     (if oids
       [{:$match {:player-statistics {:$elemMatch {:player-id {:$in oids}}}}}]
       [])
     (into
      (if oids
        [{:$unwind "$player-statistics"}
         coerce-player-stat-goals-assists
         {:$match {"player-statistics.player-id" {:$in oids}}}]
        [{:$unwind "$player-statistics"}
         coerce-player-stat-goals-assists])
      [{:$group {:_id {:player-id "$player-statistics.player-id"
                        :championship-id "$championship-id"
                        :season-id "$season-id"}
                 :games {:$sum 1}
                 :goals {:$sum "$player-statistics.goals"}
                 :assists {:$sum "$player-statistics.assists"}
                 :yellow-cards {:$sum "$player-statistics.yellow-cards"}
                 :red-cards {:$sum "$player-statistics.red-cards"}
                 :minutes-played {:$sum "$player-statistics.minutes-played"}}}
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
                          :assists "$assists"
                          :yellow-cards "$yellow-cards"
                          :red-cards "$red-cards"
                          :minutes-played "$minutes-played"}}
                 :total {:$push {:games "$games"
                                 :goals "$goals"
                                 :assists "$assists"
                                 :yellow-cards "$yellow-cards"
                                 :red-cards "$red-cards"
                                 :minutes-played "$minutes-played"}}}}
       {:$project {:player-id "$_id"
                  :aggregated-stats
                  {:total {:games {:$sum "$total.games"}
                           :goals {:$sum "$total.goals"}
                           :assists {:$sum "$total.assists"}
                           :yellow-cards {:$sum "$total.yellow-cards"}
                           :red-cards {:$sum "$total.red-cards"}
                           :minutes-played {:$sum "$total.minutes-played"}}
                   :by-championship "$by-championship"}}}]))))

(defn update-aggregated-stats-pipeline
  "Aggregation pipeline to calculate aggregated stats for all players.
   Groups by (player, championship, season-id) so table rows keyed by :season align with match rollups.
   Returns data structure ready to update players collection."
  []
  (try
    (mc/aggregate (db) "matches" (update-aggregated-stats-pipeline-vec []))
    (catch Exception e
      (log/error e "Error in update aggregated stats pipeline")
      (throw e))))

(defn update-all-player-stats
  "Update aggregated stats for all players based on matches"
  []
  (try
    (let [;; Realize the aggregation: Mongo cursors are single-pass; doseq + count can yield :updated 0
          ;; and incremental paths that walk the seq twice can skip the doseq body entirely.
          rows (vec (or (update-aggregated-stats-pipeline) []))]
      (doseq [player-stats rows]
        (let [pid (->object-id (:player-id player-stats))]
          (when-let [player (mc/find-one-as-map (db) "players" {:_id pid})]
            (let [existing-stats (:aggregated-stats player)
                  match-by-championship (get-in player-stats [:aggregated-stats :by-championship] [])
                  merged-stats (analytics/merge-aggregated-stats existing-stats match-by-championship)]
              (mc/update (db) "players"
                         {:_id pid}
                         {:$set {:aggregated-stats merged-stats
                                :updated-at (java.util.Date.)}})))))
      {:status :success :updated (count rows)})
    (catch Exception e
      (log/error e "Error updating all player stats")
      (throw e))))

(defn update-incremental-player-stats!
  "Recalcula aggregated-stats incrementalmente para os player-ids fornecidos.
  
  Usa merge-aggregated-stats do domain/analytics que implementa a fórmula híbrida:
    exibido = pre-match-stats + (rollup_partidas − baseline-match-rollup)
  
  Comportamento:
  - Escaneia apenas partidas que referenciam os players
  - Preserva baseline histórico (:pre-match-stats) por padrão
  - Players sem partidas: mantém baseline se existir, senão zera
  
  Opções:
  - :zero-if-no-matches? (default true) — zera se não há partidas e sem baseline
  - :drop-stale-without-match-rollups? (default false) — zera rows sem rollup (só reconcile)
  
  IMPORTANTE: O default (false) preserva baseline. Use true apenas em reconciliação
  explícita para limpar linhas órfãs.
  
  Ver: docs/informacao/dominio/guia-partidas-temporadas-estatisticas-hibridas.md
  
  Returns {:status :success :updated n}."
  ([player-ids]
   (update-incremental-player-stats! player-ids {:zero-if-no-matches? true}))
  ([player-ids opts]
   (let [oids (distinct-player-object-ids player-ids)
         zero-missing? (:zero-if-no-matches? opts true)]
     (if (empty? oids)
       {:status :success :updated 0}
       (try
         (let [rows (vec (or (mc/aggregate (db) "matches" (update-aggregated-stats-pipeline-vec oids)) []))
               merge-opts (select-keys opts [:drop-stale-without-match-rollups?])
               updated-pids (into #{} (keep #(try (->object-id (:player-id %))
                                                  (catch Exception _ nil))
                                            rows))]
           (doseq [player-stats rows]
             (let [pid (->object-id (:player-id player-stats))]
               (when-let [player (mc/find-one-as-map (db) "players" {:_id pid})]
                 (let [existing-stats (:aggregated-stats player)
                       match-by-championship (get-in player-stats [:aggregated-stats :by-championship] [])
                       merged-stats (analytics/merge-aggregated-stats existing-stats match-by-championship merge-opts)]
                   (mc/update (db) "players"
                              {:_id pid}
                              {:$set {:aggregated-stats merged-stats
                                     :updated-at (java.util.Date.)}})))))
           (when zero-missing?
             (doseq [pid oids
                     :when (not (contains? updated-pids pid))]
               (when-let [player (mc/find-one-as-map (db) "players" {:_id pid})]
                 (let [existing-stats (:aggregated-stats player)]
                   (if (analytics/player-has-baseline-stats? existing-stats)
                     (let [merged-stats (analytics/merge-aggregated-stats existing-stats [] merge-opts)]
                       (mc/update (db) "players"
                                  {:_id pid}
                                  {:$set {:aggregated-stats merged-stats
                                         :updated-at (java.util.Date.)}}))
                     (mc/update (db) "players"
                                {:_id pid}
                                {:$set {:aggregated-stats {:total {:games 0 :goals 0 :assists 0 :titles 0}
                                                           :by-championship []}
                                        :updated-at (java.util.Date.)}}))))))
           {:status :success :updated (count rows)})
         (catch Exception e
           (log/error e "Error updating incremental player stats")
           (throw e)))))))

(defn update-player-stats-for-match
  "Update aggregated stats for players involved in a specific match (incremental scan)."
  [match-id]
  (try
    (if-let [match (mc/find-one-as-map (db) "matches" {:_id (->object-id match-id)})]
      (let [pids (map :player-id (:player-statistics match))]
        (if (seq pids)
          (update-incremental-player-stats! pids)
          {:status :success :updated 0}))
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
