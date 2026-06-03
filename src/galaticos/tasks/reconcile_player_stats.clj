(ns galaticos.tasks.reconcile-player-stats
  "Recompute all `players.aggregated-stats` from `matches` using the hybrid merge model.
  Use after fixing merge logic or when players show inflated goals (e.g. table + full rollup).

  From repo root, with MongoDB up:

  clojure -M:dev -m galaticos.tasks.reconcile-player-stats

  Same effect as `POST /api/aggregations/reconcile` (see reconciliation-runbook.md)."
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.core :as db]
            [monger.collection :as mc]))

(defn- row-missing-hybrid-metadata?
  [row]
  (and (some pos? (map #(get row %) [:games :goals :assists]))
       (nil? (:pre-match-stats row))
       (nil? (:baseline-match-rollup row))))

(defn identify-players-missing-hybrid-metadata
  "Players with by-championship rows that have display stats but no :pre-match-stats /
  :baseline-match-rollup (candidates for double-count on new matches)."
  []
  (->> (mc/find-maps (db/db) "players" {:active true})
       (filter (fn [p]
                 (some row-missing-hybrid-metadata?
                       (or (get-in p [:aggregated-stats :by-championship]) []))))
       (mapv (fn [p] {:_id (:_id p) :name (:name p)}))))

(defn -main [& _args]
  (let [conn (db/connect!)]
    (if (= :error (:status conn))
      (do
        (binding [*out* *err*] (println "Failed to connect to MongoDB:" (:message conn)))
        (System/exit 1))
      (try
        (let [candidates (identify-players-missing-hybrid-metadata)]
          (when (seq candidates)
            (log/warn "Players with by-championship stats but no hybrid metadata"
                      {:count (count candidates)
                       :sample (take 10 candidates)}))
          (let [r (agg/update-all-player-stats)]
            (log/info "reconcile-player-stats finished" (assoc r :candidates (count candidates)))
            (println (pr-str {:candidates-missing-metadata (count candidates)
                              :sample candidates
                              :player-aggregation r}))))
        (finally
          (db/disconnect!))))))
