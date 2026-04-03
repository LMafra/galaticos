(ns galaticos.db.championships
  "Database operations for championships collection"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.db.matches :as matches-db]
            [galaticos.util.response :refer [->object-id]])
  (:import [org.bson.types ObjectId]))

(def collection-name "championships")

(defn create
  "Create a new championship"
  [championship-data]
  (let [now (java.util.Date.)
        id (ObjectId.)
        doc (merge {:enrolled-player-ids []
                     :season-ids []}
                   championship-data
                   {:_id id
                    :created-at now
                    :updated-at now})]
    (mc/insert (db) collection-name doc)
    doc))

(defn add-season-id
  "Append a season reference to the championship.season-ids array."
  [championship-id season-id]
  (mc/update (db) collection-name
             {:_id (->object-id championship-id)}
             {:$addToSet {:season-ids (->object-id season-id)}
              :$set {:updated-at (java.util.Date.)}}))

(defn remove-season-id
  "Remove a season reference from the championship.season-ids array."
  [championship-id season-id]
  (mc/update (db) collection-name
             {:_id (->object-id championship-id)}
             {:$pull {:season-ids (->object-id season-id)}
              :$set {:updated-at (java.util.Date.)}}))

(defn find-by-id
  "Find championship by ID"
  [id]
  (mc/find-one-as-map (db) collection-name {:_id (->object-id id)}))

(defn find-all
  "Find all championships, optionally filtered by status"
  ([]
   (find-all {}))
  ([filters]
   (mc/find-maps (db) collection-name filters)))

(defn find-active
  "Find all active championships"
  []
  (find-all {:status "active"}))

(defn find-by-name-and-season
  "Find championship by name and season"
  [name season]
  (mc/find-one-as-map (db) collection-name {:name name :season season}))

(defn update-by-id
  "Update championship by ID"
  [id updates]
  (mc/update (db) collection-name
             {:_id (->object-id id)}
             {:$set (merge updates {:updated-at (java.util.Date.)})}))

(defn delete-by-id
  "Delete championship by ID"
  [id]
  (mc/remove (db) collection-name {:_id (->object-id id)}))

(defn exists?
  "Check if championship exists"
  [id]
  (some? (find-by-id id)))

(defn has-matches?
  "Check if championship has any matches"
  [championship-id]
  (let [matches (matches-db/find-by-championship championship-id)]
    (seq matches)))

(defn add-player
  "Enroll a player in a championship"
  [championship-id player-id]
  (mc/update (db) collection-name
             {:_id (->object-id championship-id)}
             {:$addToSet {:enrolled-player-ids (->object-id player-id)}
              :$set {:updated-at (java.util.Date.)}}))

(defn remove-player
  "Unenroll a player from a championship"
  [championship-id player-id]
  (mc/update (db) collection-name
             {:_id (->object-id championship-id)}
             {:$pull {:enrolled-player-ids (->object-id player-id)}
              :$set {:updated-at (java.util.Date.)}}))
