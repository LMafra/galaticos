(ns galaticos.analytics.predictive
  "Lightweight trend, risk, and linear projection (no ML libs)."
  (:require [galaticos.domain.analytics :as domain]))

(def ^:private trend-threshold 0.15)

(defn- safe-double [v]
  (if (number? v) (double v) 0.0))

(defn- bucket-contribution-per-game
  [{:keys [games goals assists]}]
  (let [g (long (or games 0))]
    (if (pos? g)
      (/ (+ (long (or goals 0)) (long (or assists 0))) (double g))
      0.0)))

(defn- window-avg
  [buckets]
  (if (empty? buckets)
    0.0
    (/ (reduce + 0.0 (map bucket-contribution-per-game buckets))
       (double (count buckets)))))

(defn- split-windows
  [evolution]
  (let [sorted (vec (sort-by (juxt #(get-in % [:_id :year])
                                   #(get-in % [:_id :month])
                                   #(get-in % [:_id :week]))
                             evolution))
        n (count sorted)]
    (if (< n 2)
      {:recent sorted :prior []}
      (let [mid (int (/ n 2))]
        {:recent (subvec sorted mid n)
         :prior (subvec sorted 0 mid)}))))

(defn compute-trend
  "Compare recent vs prior evolution buckets on goal-contribution-per-game."
  [evolution]
  (let [{:keys [recent prior]} (split-windows evolution)
        recent-avg (window-avg recent)
        prior-avg (window-avg prior)
        delta (- recent-avg prior-avg)
        direction (cond
                    (> delta trend-threshold) :up
                    (< delta (- trend-threshold)) :down
                    :else :stable)]
    {:direction direction
     :delta (safe-double delta)
     :recent-avg (safe-double recent-avg)
     :prior-avg (safe-double prior-avg)
     :recent-buckets (count recent)
     :prior-buckets (count prior)}))

(defn compute-risk
  "Simple risk label from trend + discipline on total stats."
  [trend total-stats]
  (let [discipline (domain/discipline-index (or total-stats {}))
        down? (= :down (:direction trend))
        level (cond
                (and down? (>= discipline 1.5)) :high
                (or down? (>= discipline 1.0)) :medium
                :else :low)]
    {:level level
     :discipline-index (safe-double discipline)
     :trend-direction (:direction trend)}))

(defn compute-projection
  "Linear extrapolation: avg goal-contribution in recent window × next window size."
  [evolution & {:keys [next-window-size] :or {next-window-size 4}}]
  (let [{:keys [recent]} (split-windows evolution)
        per-bucket (mapv (fn [b]
                           (+ (long (or (:goals b) 0))
                              (long (or (:assists b) 0))))
                         recent)
        avg (if (seq per-bucket)
              (/ (reduce + 0 (map long per-bucket)) (double (count per-bucket)))
              0.0)
        projected (* avg (double next-window-size))]
    {:projected-goal-contribution (safe-double projected)
     :window-size (count recent)
     :next-window-size next-window-size
     :avg-contribution-per-bucket (safe-double avg)}))

(defn experiment-meta
  []
  {:version "v0.1"
   :model "linear-window"
   :status "experimental"})
