(ns galaticos.match-draft
  "Local draft + last championship for match form (UX-PLAN-09)."
  (:require [clojure.string :as str]))

(def ^:private draft-prefix "galaticos.match-draft.")
(def ^:private last-championship-key "galaticos.match-last-championship")
(def ^:private stale-ms (* 7 24 60 60 1000))

(defn draft-storage-key [route-id]
  (str draft-prefix (str route-id)))

(defn match-draft-route-id
  [is-edit? match-id preset-championship-id]
  (if is-edit?
    (str "edit-" match-id)
    (if (str/blank? (str preset-championship-id))
      "new"
      (str "new-" preset-championship-id))))

(defn today-date-str []
  (let [d (js/Date.)
        pad #(if (< % 10) (str "0" %) (str %))]
    (str (.getFullYear d) "-" (pad (inc (.getMonth d))) "-" (pad (.getDate d)))))

(defn- storage-available? []
  (try
    (boolean (.-localStorage js/window))
    (catch :default _ false)))

(defn- read-json [k]
  (try
    (when (storage-available?)
      (when-let [raw (.getItem js/localStorage k)]
        (js/JSON.parse raw)))
    (catch :default _ nil)))

(defn draft-stale? [saved-at]
  (let [ts (cond
             (number? saved-at) saved-at
             (string? saved-at) (js/parseInt saved-at 10)
             :else nil)]
    (and (number? ts) (not (js/isNaN ts))
         (> (- (.now js/Date) ts) stale-ms))))

(defn- normalize-player-stat [stat]
  (let [m (cond
            (map? stat) stat
            :else {})]
    {:goals (or (:goals m) (get m "goals") 0)
     :assists (or (:assists m) (get m "assists") 0)
     :minutes-played (or (:minutes-played m) (get m "minutes-played") 0)
     :yellow-cards (or (:yellow-cards m) (get m "yellow-cards") 0)
     :red-cards (or (:red-cards m) (get m "red-cards") 0)
     :played? (boolean (or (:played? m) (get m "played?")))}))

(defn normalize-player-statistics
  "Pure: map player-id (string) → normalized stat map.
   Accepts keywordized ids from JSON keywordize-keys."
  [stats]
  (let [raw (cond
              (map? stats) stats
              :else {})]
    (into {}
          (map (fn [[pid stat]]
                 [(cond
                    (keyword? pid) (name pid)
                    (string? pid) pid
                    :else (str pid))
                  (normalize-player-stat
                   (cond
                     (map? stat)
                     (into {}
                           (map (fn [[k v]]
                                  [(keyword (if (keyword? k) (name k) (str k))) v])
                                stat))
                     :else {}))])
               raw))))

(defn form-snapshot
  "Pure form subset persisted in drafts (includes player-statistics)."
  [form-data]
  (let [fd (or form-data {})]
    {:championship-id (str (or (:championship-id fd) ""))
     :home-team-id (str (or (:home-team-id fd) ""))
     :date (str (or (:date fd) ""))
     :opponent (str (or (:opponent fd) ""))
     :venue (str (or (:venue fd) ""))
     :away-score (let [a (:away-score fd)]
                   (if (number? a) a 0))
     :player-statistics (normalize-player-statistics (:player-statistics fd))}))

(defn draft-payload
  "Pure envelope {:form-data :saved-at}."
  [form-data saved-at]
  {:form-data (form-snapshot form-data)
   :saved-at saved-at})

(defn encode-draft
  "Pure JSON string for a draft payload."
  [payload]
  (.stringify js/JSON (clj->js payload)))

(defn decode-draft
  "Pure parse of draft JSON → map with keywordized form-data, or nil."
  [raw]
  (try
    (when (and (string? raw) (not (str/blank? raw)))
      (let [data (js->clj (js/JSON.parse raw) :keywordize-keys true)]
        (when (map? (:form-data data))
          (-> data
              (update :form-data
                      (fn [fd]
                        (-> fd
                            (update :player-statistics normalize-player-statistics)
                            (update :away-score #(if (number? %) % 0)))))))))
    (catch :default _ nil)))

(defn load-draft [route-id]
  (when-let [parsed (read-json (draft-storage-key route-id))]
    (let [data (js->clj parsed :keywordize-keys true)]
      (when (map? (:form-data data))
        (-> data
            (update :form-data
                    (fn [fd]
                      (-> fd
                          (update :player-statistics normalize-player-statistics)
                          (update :away-score #(if (number? %) % 0))))))))))

(defn save-draft! [route-id form-data]
  (when-not (str/blank? (str route-id))
    (try
      (when (storage-available?)
        (.setItem js/localStorage
                  (draft-storage-key route-id)
                  (encode-draft (draft-payload form-data (.now js/Date)))))
      (catch :default _ nil))))

(defn clear-draft! [route-id]
  (try
    (when (storage-available?)
      (.removeItem js/localStorage (draft-storage-key route-id)))
    (catch :default _ nil)))

(defn load-last-championship-id []
  (try
    (when (storage-available?)
      (.getItem js/localStorage last-championship-key))
    (catch :default _ nil)))

(defn save-last-championship-id! [championship-id]
  (when-not (str/blank? (str championship-id))
    (try
      (when (storage-available?)
        (.setItem js/localStorage last-championship-key (str championship-id)))
      (catch :default _ nil))))
