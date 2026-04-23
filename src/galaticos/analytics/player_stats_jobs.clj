(ns galaticos.analytics.player-stats-jobs
  "In-process single-thread executor for player aggregated-stats recompute after match writes
  (incremental by default) and full recompute. See docs/parcial/analytics/technical-evolution."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [galaticos.analytics.player-stats-job-store :as job-store]
            [galaticos.db.aggregations :as agg])
  (:import [java.lang Runnable]
           [java.util UUID]
           [java.util.concurrent ThreadFactory ThreadPoolExecutor TimeUnit
            LinkedBlockingQueue]))

(defn- long-ms-threshold
  "Warn when recalc duration exceeds this (default 30s). Override: GALATICOS_PLAYER_STATS_LONG_MS or
  :galaticos-player-stats-long-ms in env."
  []
  (let [raw (or (System/getenv "GALATICOS_PLAYER_STATS_LONG_MS")
                (str (or (env :galaticos-player-stats-long-ms) "")))]
    (or (try (Long/parseLong (str/trim raw)) (catch Exception _ nil))
        30000)))

(defn- max-attempts []
  (let [raw (or (System/getenv "GALATICOS_PLAYER_STATS_MAX_ATTEMPTS")
                (str (or (env :galaticos-player-stats-max-attempts) "")))]
    (or (try (max 1 (Long/parseLong (str/trim raw))) (catch Exception _ nil))
        3)))

(defn- retry-backoff-ms [attempt]
  (let [raw (or (System/getenv "GALATICOS_PLAYER_STATS_RETRY_BACKOFF_MS")
                (str (or (env :galaticos-player-stats-retry-backoff-ms) "")))
        base (or (try (Long/parseLong (str/trim raw)) (catch Exception _ nil))
                 100)]
    (* base (long (Math/pow 2 (max 0 (dec attempt)))))))

(defn- thread-factory []
  (reify ThreadFactory
    (newThread [_ r]
      (doto (Thread. ^Runnable r "galaticos-player-stats-recalc")
        (.setDaemon false)))))

(defonce ^:private recalc-executor
  (ThreadPoolExecutor. 1 1 0 TimeUnit/MILLISECONDS
                       (LinkedBlockingQueue.)
                       (thread-factory)))

(defn executor-runtime-info
  "Queue depth and active workers for the in-process recalc pool (for GET status)."
  []
  (try
    (let [^ThreadPoolExecutor ex recalc-executor]
      {:pool-size (.getPoolSize ex)
       :active-count (.getActiveCount ex)
       :queue-size (.size (.getQueue ex))})
    (catch Exception e
      (log/warn e "player-stats: could not read executor runtime")
      {:pool-size 1 :active-count 0 :queue-size 0})))

(defn- parse-boolean-like [v]
  (when v
    (let [s (str/trim (str v))]
      (cond
        (#{"1" "true" "yes" "on"} (str/lower-case s)) true
        (#{"0" "false" "no" "off"} (str/lower-case s)) false
        (str/blank? s) false
        :else (boolean v)))))

(defn- env-sync? []
  (or (parse-boolean-like (or (env :galaticos-player-stats-sync)
                              (System/getenv "GALATICOS_PLAYER_STATS_SYNC")))
      false))

(defn- env-force-full? []
  (or (parse-boolean-like (or (env :galaticos-player-stats-force-full)
                              (System/getenv "GALATICOS_PLAYER_STATS_FORCE_FULL")))
      false))

(def ^:dynamic *synchronous-refresh*
  "When true, match-triggered recalc runs in caller thread (e.g. tests). Never set in production."
  false)

(def ^:dynamic *recompute-fn*
  "If bound, used instead of `agg/update-all-player-stats` for full recompute (tests, hooks)."
  nil)

(defn- run-full-recompute! []
  (if *recompute-fn*
    (*recompute-fn*)
    (agg/update-all-player-stats)))

(defn- run-match-recalc! [payload]
  (if (env-force-full?)
    (run-full-recompute!)
    (let [ids (:affected-player-ids payload)]
      (if-not (seq (filter some? ids))
        {:status :success :updated 0}
        (agg/update-incremental-player-stats! ids)))))

(defn- log-ctx [job-id reason kvs]
  (merge {:job-id job-id
          :galaticos.event/player-stats-refresh true
          :reason reason}
         kvs))

(defn- base-match-log [payload]
  (cond-> {:recalc (if (env-force-full?) :full :incremental)
           :recalc-execution (if (or *synchronous-refresh* (env-sync?)) :sync :async)
           :affected-count (count (filter some? (:affected-player-ids payload [])))
           :op (:op payload)}
    (:match-id payload) (assoc :match-id (str (:match-id payload)))))

(defn- match-job-has-work? [payload]
  (or (env-force-full?)
      (seq (filter some? (:affected-player-ids payload [])))))

(defn- execute-match-payload! [job-id reason payload]
  (let [th (long-ms-threshold)
        base (base-match-log payload)
        recalc (if (env-force-full?) :full :incremental)
        nmax (max-attempts)
        t0 (System/currentTimeMillis)]
    (log/info "player stats recalc start" (log-ctx job-id reason base))
    (loop [attempt 1]
      (let [a0 (System/currentTimeMillis)
            step (try
                   (let [r (run-match-recalc! payload)
                         a1 (System/currentTimeMillis)
                         duration-ms (- a1 a0)
                         total-ms (- a1 t0)
                         updated (get r :updated)]
                     (log/info "player stats recalc end" (log-ctx job-id reason
                                                      (merge base
                                                             {:outcome :success
                                                              :duration-ms duration-ms
                                                              :attempt attempt
                                                              :total-duration-ms total-ms
                                                              :updated updated})))
                     (when (> total-ms th)
                       (log/warn "player stats recalc exceeded threshold"
                                 (log-ctx job-id reason (merge base {:duration-ms total-ms}))))
                     (job-store/record-incremental-success! job-id reason payload recalc r duration-ms)
                     {:done r})
                   (catch Exception e
                     (if (>= attempt nmax)
                       (do
                         (log/error e "player stats recalc failed" (log-ctx job-id reason
                                                                                (merge base
                                                                                       {:outcome :error
                                                                                        :attempt attempt})))
                         {:done :failed :exception e})
                       :retry)))]
        (if (= :retry step)
          (do
            (log/warn "player stats recalc failed, will retry" (log-ctx job-id reason
                                                                            (merge base
                                                                                   {:outcome :retry
                                                                                    :attempt attempt})))
            (Thread/sleep (retry-backoff-ms attempt))
            (recur (inc attempt)))
          (if (= :failed (:done step))
            nil
            (:done step)))))))

(defn submit-incremental-recalc-after-match!
  "After match CRUD, schedule incremental recompute (or full when GALATICOS_PLAYER_STATS_FORCE_FULL is set).
  `payload` must include :affected-player-ids and should include :reason, :op, :match-id for logging.
  Never throws to caller. Async on single worker unless *synchronous-refresh* or
  GALATICOS_PLAYER_STATS_SYNC is true — then runs inline."
  [payload]
  (let [job-id (str (UUID/randomUUID))
        reason (:reason payload :after-match-unknown)
        run! #(execute-match-payload! job-id reason payload)
        submit-async! (fn [] (.execute recalc-executor
                                        (reify Runnable
                                          (run [_] (run!)))))]
    (when (match-job-has-work? payload)
      (if (or *synchronous-refresh* (env-sync?))
        (run!)
        (submit-async!)))
    nil))

(defn- perform-full-async! [job-id reason]
  (let [th (long-ms-threshold)
        nmax (max-attempts)]
    (log/info "player stats recalc start" (log-ctx job-id reason
                                                   {:recalc :full
                                                    :recalc-execution :async}))
    (let [t0 (System/currentTimeMillis)]
      (loop [attempt 1]
        (let [a0 (System/currentTimeMillis)
              step (try
                     (let [result (run-full-recompute!)
                           a1 (System/currentTimeMillis)
                           duration-ms (- a1 a0)
                           total-ms (- a1 t0)
                           updated (get result :updated)]
                       (log/info "player stats recalc end" (log-ctx job-id reason
                                                        {:outcome :success
                                                         :recalc :full
                                                         :recalc-execution :async
                                                         :duration-ms duration-ms
                                                         :attempt attempt
                                                         :total-duration-ms total-ms
                                                         :updated updated}))
                       (when (> total-ms th)
                         (log/warn "player stats recalc exceeded threshold"
                                   (log-ctx job-id reason {:recalc :full
                                                           :recalc-execution :async
                                                           :duration-ms total-ms})))
                       (job-store/record-full-success! job-id reason :async result duration-ms)
                       {:done result})
                     (catch Exception e
                       (if (>= attempt nmax)
                         (do
                           (log/error e "player stats recalc failed" (log-ctx job-id reason
                                                                               {:outcome :error
                                                                                :recalc :full
                                                                                :recalc-execution :async
                                                                                :attempt attempt}))
                           {:done :failed})
                         :retry)))]
          (if (= :retry step)
            (do
              (log/warn "player stats full recalc failed, will retry" (log-ctx job-id reason
                                                                                 {:outcome :retry
                                                                                  :recalc :full
                                                                                  :recalc-execution :async
                                                                                  :attempt attempt
                                                                                  :message "retrying"}))
              (Thread/sleep (retry-backoff-ms attempt))
              (recur (inc attempt)))
            (when (not= :failed (:done step))
              (:done step))))))))

(defn submit-full-recompute!
  "Enqueue a full recompute on the single worker thread. Returns {:status :ok :job-id s} (never throws)."
  [reason]
  (let [job-id (str (UUID/randomUUID))
        r! (fn [] (perform-full-async! job-id reason))]
    (.execute recalc-executor
              (reify Runnable
                (run [_] (r!))))
    {:status :ok :job-id job-id}))

(defn synchronous-full-recompute!
  "Run full recompute in caller thread. Returns {:status :ok :result m} or {:status :error :message s}.
  For admin reconcile. Coordinated with informacao/analytics/reconciliation-runbook.md."
  [reason]
  (let [job-id (str (UUID/randomUUID))
        th (long-ms-threshold)
        nmax (max-attempts)
        t0 (System/currentTimeMillis)]
    (log/info "player stats recalc start" (log-ctx job-id reason
                                                   {:recalc :full
                                                    :recalc-execution :sync}))
    (let [r (loop [attempt 1]
              (let [a0 (System/currentTimeMillis)
                    step (try
                           (let [result (run-full-recompute!)
                                 a1 (System/currentTimeMillis)
                                 duration-ms (- a1 a0)
                                 total-ms (- a1 t0)
                                 updated (get result :updated)]
                             (log/info "player stats recalc end" (log-ctx job-id reason
                                                            {:outcome :success
                                                             :recalc :full
                                                             :recalc-execution :sync
                                                             :duration-ms duration-ms
                                                             :attempt attempt
                                                             :total-duration-ms total-ms
                                                             :updated updated}))
                             (when (> total-ms th)
                               (log/warn "player stats recalc exceeded threshold"
                                         (log-ctx job-id reason {:recalc :full
                                                                 :recalc-execution :sync
                                                                 :duration-ms total-ms})))
                             (job-store/record-full-success! job-id reason :sync result duration-ms)
                             {:ok result})
                           (catch Exception e
                             (if (>= attempt nmax)
                               (do
                                 (log/error e "player stats recalc failed" (log-ctx job-id reason
                                                                                 {:outcome :error
                                                                                  :recalc :full
                                                                                  :recalc-execution :sync
                                                                                  :attempt attempt}))
                                 {:err (or (.getMessage e) (str e))})
                               :retry)))]
                (if (= :retry step)
                  (do
                    (log/warn "player stats full recalc failed, will retry" (log-ctx job-id reason
                                                                                   {:outcome :retry
                                                                                    :recalc :full
                                                                                    :recalc-execution :sync
                                                                                    :attempt attempt
                                                                                    :message "retrying"}))
                    (Thread/sleep (retry-backoff-ms attempt))
                    (recur (inc attempt)))
                  step)))]
      (if (:ok r)
        {:status :ok :result (:ok r)}
        {:status :error :message (:err r)}))))
