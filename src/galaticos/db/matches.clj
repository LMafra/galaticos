(ns galaticos.db.matches
  "Database operations for matches collection"
  (:require [clojure.string :as str]
            [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]])
  (:import [org.bson.types ObjectId]))

(def collection-name "matches")

(defn- safe-stat-int
  "Coerce match stat field to int for BSON ($sum) and home-score. Mirrors aggregation semantics: nil/bad -> 0."
  [value]
  (cond
    (number? value) (int (long value))
    (nil? value) 0
    (string? value)
    (let [s (str/trim value)]
      (if (str/blank? s)
        0
        (try (int (long (Double/parseDouble s)))
             (catch Exception _ 0))))
    :else
    (try (int (long value))
         (catch Exception _ 0))))

(defn normalize-player-statistics
  "Force goals/assists/cards/minutes to numeric ints on each player line (API may send strings).
  Use from maintenance tasks; create/update call this before persisting."
  [player-statistics]
  (if-not (sequential? player-statistics)
    player-statistics
    (mapv
     (fn [row]
       (if (map? row)
         (merge row
                {:goals (safe-stat-int (:goals row))
                 :assists (safe-stat-int (:assists row))
                 :yellow-cards (safe-stat-int (:yellow-cards row))
                 :red-cards (safe-stat-int (:red-cards row))
                 :minutes-played (safe-stat-int (:minutes-played row))})
         row))
     player-statistics)))

(defn- sum-goals [team-id player-statistics]
  (if team-id
    (reduce + 0 (map #(or (:goals %) 0)
                     (filter #(= (:team-id %) team-id) player-statistics)))
    0))

(defn- calculate-scores
  "home-score is always calculated from our team's goals; away-score is manual (no opponent stats)."
  [match-data player-statistics]
  (let [home-team-id (:home-team-id match-data)
        home-score (if (seq player-statistics)
                     (sum-goals home-team-id player-statistics)
                     0)
        ;; away-score: use value from request when provided (single-team platform), else 0
        away-score (if (some? (:away-score match-data))
                     (safe-stat-int (or (:away-score match-data) 0))
                     0)]
    {:home-score home-score
     :away-score away-score}))

(defn create
  "Create a new match with player statistics"
  [match-data player-statistics]
  (let [player-statistics (normalize-player-statistics player-statistics)
        now (java.util.Date.)
        id (ObjectId.)
        scores (calculate-scores match-data player-statistics)
        doc (merge match-data
                   scores
                   {:_id id
                    :player-statistics player-statistics
                    :created-at now
                    :updated-at now})]
    (mc/insert (db) collection-name doc)
    doc))

(defn find-by-id
  "Find match by ID"
  [id]
  (mc/find-one-as-map (db) collection-name {:_id (->object-id id)}))

(defn find-all
  "Find all matches, optionally filtered"
  ([]
   (find-all {}))
  ([filters]
   (mc/find-maps (db) collection-name filters)))

(defn find-by-championship
  "Find matches by championship ID, ordered by date descending"
  [championship-id]
  (sort-by :date #(compare %2 %1)
           (mc/find-maps (db) collection-name
                         {:championship-id (->object-id championship-id)})))

(defn find-by-season
  "Find matches by season ID, ordered by date descending"
  [season-id]
  (sort-by :date #(compare %2 %1)
           (mc/find-maps (db) collection-name
                         {:season-id (->object-id season-id)})))

(defn find-by-date-range
  "Find matches within date range"
  [start-date end-date]
  (find-all {:date {:$gte start-date :$lte end-date}}))

(defn find-by-player
  "Find matches where player participated"
  [player-id]
  (find-all {"player-statistics.player-id" (->object-id player-id)}))

(defn update-by-id
  "Update match by ID"
  [id updates]
  (let [match (find-by-id id)
        merged (merge (select-keys match [:home-team-id :away-team-id :player-statistics :away-score]) updates)
        merged (update merged :player-statistics
                       (fn [ps]
                         (if (sequential? ps) (normalize-player-statistics ps) ps)))
        scores (calculate-scores merged (:player-statistics merged))]
    (mc/update (db) collection-name
               {:_id (->object-id id)}
               {:$set (merge merged scores {:updated-at (java.util.Date.)})})))

(defn delete-by-id
  "Delete match by ID"
  [id]
  (mc/remove (db) collection-name {:_id (->object-id id)}))

(defn exists?
  "Check if match exists"
  [id]
  (some? (find-by-id id)))

