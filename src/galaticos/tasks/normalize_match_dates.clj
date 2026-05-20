(ns galaticos.tasks.normalize-match-dates
  "One-off maintenance: convert string `date` values in `matches` to BSON Date.

  From repo root, with `DATABASE_URL` (optional; defaults to localhost) and MongoDB up:

  clojure -M:dev -m galaticos.tasks.normalize-match-dates
  "
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [galaticos.db.core :as db]
            [galaticos.db.matches :as matches]
            [monger.collection :as mc]
            [monger.operators :refer [$set]]))

(defn -main [& _args]
  (let [conn (db/connect!)]
    (if (= :error (:status conn))
      (do
        (binding [*out* *err*] (println "Failed to connect to MongoDB:" (:message conn)))
        (System/exit 1))
      (try
        (let [all (mc/find-maps (db/db) matches/collection-name {})
              ndocs (count all)
              updated (atom 0)
              skipped (atom 0)]
          (log/info "Scanning" ndocs "match documents for string dates")
          (doseq [m all]
            (let [date (:date m)]
              (cond
                (string? date)
                (let [coerced (matches/coerce-match-date date)]
                  (if coerced
                    (do
                      (swap! updated inc)
                      (mc/update (db/db) matches/collection-name
                                 {:_id (:_id m)}
                                 {$set {:date coerced}}))
                    (do
                      (swap! skipped inc)
                      (log/warn "Skipping match with unparseable date string"
                                {:match-id (:_id m) :date date}))))

                (instance? java.util.Date date)
                (swap! skipped inc)

                :else
                (swap! skipped inc))))
          (log/info "normalize-match-dates finished"
                    {:matches-scanned ndocs
                     :matches-updated @updated
                     :matches-skipped @skipped})
          (println (pr-str {:matches-scanned ndocs
                            :matches-updated @updated
                            :matches-skipped @skipped})))
        (finally
          (db/disconnect!))))))
