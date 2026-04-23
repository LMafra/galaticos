(ns galaticos.analytics.player-stats-job-store
  "Last-success metadata for in-process player-stats jobs (single logical document in MongoDB).
  See docs/parcial/analytics/technical-evolution.md."
  (:require [clojure.tools.logging :as log]
            [galaticos.db.core :refer [db]]
            [monger.collection :as mc]))

(def ^:private collection-name "player_stats_job_meta")

(def ^:private doc-id "player-stats-jobs")

(defn record-incremental-success!
  "Persist last successful match-triggered (incremental or forced full) job summary.
  `recalc` is :incremental or :full (from env-force-full? in the job runner)."
  [job-id reason payload recalc result duration-ms]
  (try
    (let [match-id (when-let [m (:match-id payload)] (str m))
          row {:job-id job-id
               :reason (keyword (name (or reason :unknown)))
               :op (keyword (name (or (:op payload) :unknown)))
               :recalc (keyword (name (or recalc :incremental)))
               :match-id match-id
               :updated (or (:updated result) 0)
               :duration-ms (long (or duration-ms 0))
               :finished-at (java.util.Date.)}]
      (mc/update (db) collection-name {:_id doc-id}
                 {:$set {:last-incremental row
                         :updated-at (java.util.Date.)}}
                 {:upsert true}))
    (catch Exception e
      (log/warn e "player-stats-job-store: could not record incremental success"))))

(defn record-full-success!
  "Persist last successful full recompute (async or sync reconcile path calls this for sync only;
   async full path in player_stats_jobs also calls with execution style)."
  [job-id reason recalc-execution result duration-ms]
  (try
    (let [row {:job-id job-id
               :reason (keyword (name (or reason :unknown)))
               :recalc :full
               :recalc-execution (keyword (name recalc-execution))
               :updated (or (:updated result) 0)
               :duration-ms (long (or duration-ms 0))
               :finished-at (java.util.Date.)}]
      (mc/update (db) collection-name {:_id doc-id}
                 {:$set {:last-full row
                         :updated-at (java.util.Date.)}}
                 {:upsert true}))
    (catch Exception e
      (log/warn e "player-stats-job-store: could not record full success"))))

(defn fetch-doc
  "Return the metadata document (maps keys to keywords) or nil if missing/ error."
  []
  (try
    (when-let [m (mc/find-one-as-map (db) collection-name {:_id doc-id})]
      m)
    (catch Exception e
      (log/warn e "player-stats-job-store: could not read metadata")
      nil)))
