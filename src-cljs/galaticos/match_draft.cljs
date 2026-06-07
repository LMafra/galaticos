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

(defn- read-json [k]
  (try
    (when-let [raw (.getItem js/localStorage k)]
      (js/JSON.parse raw))
    (catch :default _ nil)))

(defn draft-stale? [saved-at]
  (let [ts (cond
             (number? saved-at) saved-at
             (string? saved-at) (js/parseInt saved-at 10)
             :else nil)]
    (and (number? ts) (not (js/isNaN ts))
         (> (- (.now js/Date) ts) stale-ms))))

(defn load-draft [route-id]
  (when-let [parsed (read-json (draft-storage-key route-id))]
    (let [data (js->clj parsed :keywordize-keys true)]
      (when (map? (:form-data data))
        data))))

(defn- json-str [v]
  (.stringify js/JSON (str v)))

(defn- draft-json [form-data]
  (let [away (or (:away-score form-data) 0)]
    (str "{\"form-data\":{"
         "\"championship-id\":" (json-str (or (:championship-id form-data) ""))
         ",\"home-team-id\":" (json-str (or (:home-team-id form-data) ""))
         ",\"date\":" (json-str (or (:date form-data) ""))
         ",\"opponent\":" (json-str (or (:opponent form-data) ""))
         ",\"venue\":" (json-str (or (:venue form-data) ""))
         ",\"away-score\":" away
         ",\"player-statistics\":{}}"
         ",\"saved-at\":" (.now js/Date)
         "}")))

(defn save-draft! [route-id form-data]
  (when-not (clojure.string/blank? (str route-id))
    (try
      (.setItem js/localStorage (draft-storage-key route-id) (draft-json form-data))
      (catch :default _ nil))))

(defn clear-draft! [route-id]
  (try
    (.removeItem js/localStorage (draft-storage-key route-id))
    (catch :default _ nil)))

(defn load-last-championship-id []
  (try
    (.getItem js/localStorage last-championship-key)
    (catch :default _ nil)))

(defn save-last-championship-id! [championship-id]
  (when-not (clojure.string/blank? (str championship-id))
    (try
      (.setItem js/localStorage last-championship-key (str championship-id))
      (catch :default _ nil))))
