(ns galaticos.db.matches
  "Database operations for matches collection"
  (:require [monger.collection :as mc]
            [monger.query :as mq]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]])
  (:import [org.bson.types ObjectId]))

(def collection-name "matches")

(defn create
  "Create a new match with player statistics"
  [match-data player-statistics]
  (let [now (java.util.Date.)
        id (ObjectId.)
        doc (merge match-data
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
  (mq/with-collection (db) collection-name
    (mq/find {:championship-id (->object-id championship-id)})
    (mq/sort {:date -1})))

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
  (mc/update (db) collection-name
             {:_id (->object-id id)}
             {:$set (merge updates {:updated-at (java.util.Date.)})}))

(defn delete-by-id
  "Delete match by ID"
  [id]
  (mc/remove (db) collection-name {:_id (->object-id id)}))

(defn exists?
  "Check if match exists"
  [id]
  (some? (find-by-id id)))

