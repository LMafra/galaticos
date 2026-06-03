(ns galaticos.logic.analytics
  "Orchestrate analytics reads: derived enrichment, tops, player insights."
  (:require [galaticos.analytics.derived-metrics :as derived]
            [galaticos.analytics.player-stats-job-store :as job-store]
            [galaticos.analytics.player-stats-jobs :as player-stats-jobs]
            [galaticos.analytics.predictive :as predictive]
            [galaticos.analytics.readiness :as readiness]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.players :as players-db]
            [galaticos.domain.errors :as errors]))

(defn- sort-players-by-derived
  [players metric limit]
  (->> players
       derived/attach-players-derived
       (sort-by #(derived/metric-value % metric) #(compare %2 %1))
       (take limit)
       vec))

(defn top-players
  "Top players by base or derived metric."
  [metric limit & {:keys [championship-id]}]
  (let [metric-kw (keyword metric)]
    (if (derived/derived-metric? metric-kw)
      (let [candidate-limit (max (* 3 (or limit 10)) 30)
            candidates (if championship-id
                         (agg/top-players-by-metric :games candidate-limit
                                                    :championship-id championship-id)
                         (agg/top-players-by-metric :games candidate-limit))]
        (sort-players-by-derived candidates metric-kw limit))
      (if championship-id
        (-> (agg/top-players-by-metric metric-kw limit :championship-id championship-id)
            derived/attach-players-derived)
        (-> (agg/top-players-by-metric metric-kw limit)
            derived/attach-players-derived)))))

(defn search-players
  "Search players and attach `:derived` from aggregated-stats."
  [filters]
  (-> (agg/search-players filters)
      derived/attach-players-derived))

(defn dashboard-derived-tops
  "Top lists for derived metrics (for dashboard payload)."
  [limit]
  {:top-goal-contribution (top-players :goal-contribution limit)
   :top-discipline-index (top-players :discipline-index limit)})

(defn player-insights
  "Build insights payload for one player. Predictive fields omitted when readiness fails."
  [player-id]
  (if-let [player (players-db/find-by-id player-id)]
    (let [total (get-in player [:aggregated-stats :total] {})
          evolution (agg/player-performance-evolution player-id)
          readiness-result (readiness/evaluate
                            {:total total
                             :evolution evolution
                             :job-meta (job-store/fetch-doc)
                             :executor-info (player-stats-jobs/executor-runtime-info)}
                            {:require-reconciliation? true})
          derived-map (derived/derived-from-stats total)
          base {:derived derived-map
                :readiness readiness-result
                :disclaimers (readiness/disclaimers-for readiness-result)
                :experiment-meta (predictive/experiment-meta)}]
      (if (:ok readiness-result)
        (let [trend (predictive/compute-trend evolution)]
          (assoc base
                 :trend trend
                 :risk (predictive/compute-risk trend total)
                 :projection (predictive/compute-projection evolution)))
        (assoc base :trend nil :risk nil :projection nil)))
    (errors/not-found! "Player not found")))
