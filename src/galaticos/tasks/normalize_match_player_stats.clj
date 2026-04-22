(ns galaticos.tasks.normalize-match-player-stats
  "One-off maintenance: ensure every `player-statistics` line in `matches` has numeric
  goals/assists/cards/minutes, refresh derived home/away score, then recompute all
  `players.aggregated-stats` from matches.

  From repo root, with `DATABASE_URL` (optional; defaults to localhost) and MongoDB up:

  clojure -M:dev -m galaticos.tasks.normalize-match-player-stats
  "
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [galaticos.db.core :as db]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.matches :as matches]
            [monger.collection :as mc]))

(defn -main [& _args]
  (let [conn (db/connect!)]
    (if (= :error (:status conn))
      (do
        (binding [*out* *err*] (println "Failed to connect to MongoDB:" (:message conn)))
        (System/exit 1))
      (try
        (let [all (mc/find-maps (db/db) matches/collection-name {})
              ndocs (count all)
              changed (atom 0)]
          (log/info "Scanning" ndocs "match documents for legacy string stats")
          (doseq [m all]
            (when-let [ps (:player-statistics m)]
              (let [n (matches/normalize-player-statistics ps)]
                (when (not= n ps)
                  (swap! changed inc)
                  (matches/update-by-id (:_id m) {:player-statistics n})))))
          (let [r (agg/update-all-player-stats)]
            (log/info "normalize-match-player-stats finished"
                      {:matches-scanned ndocs
                       :matches-updated @changed
                       :player-aggregation r})
            (println (pr-str {:matches-scanned ndocs
                              :matches-updated @changed
                              :player-aggregation r}))))
        (finally
          (db/disconnect!))))))
