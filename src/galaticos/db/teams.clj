(ns galaticos.db.teams
  "Database operations for teams collection"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.db.players :as players-db]
            [galaticos.util.response :refer [->object-id]])
  (:import [org.bson.types ObjectId]))

(def collection-name "teams")

(defn create
  "Create a new team"
  [team-data]
  (let [now (java.util.Date.)
        id (ObjectId.)
        doc (merge team-data
                   {:_id id
                    :active-player-ids []
                    :created-at now
                    :updated-at now})]
    (mc/insert (db) collection-name doc)
    doc))

(defn find-by-id
  "Find team by ID"
  [id]
  (mc/find-one-as-map (db) collection-name {:_id (->object-id id)}))

(defn find-by-name
  "Find team by name"
  [name]
  (mc/find-one-as-map (db) collection-name {:name name}))

(defn find-by-ids
  "Find teams by a list of IDs (ObjectId or string)."
  [ids]
  (let [object-ids (map ->object-id (filter some? ids))]
    (if (seq object-ids)
      (mc/find-maps (db) collection-name {:_id {:$in object-ids}})
      [])))

(defn find-all
  "Find all teams"
  []
  (mc/find-maps (db) collection-name {}))

(defn update-by-id
  "Update team by ID"
  [id updates]
  (mc/update (db) collection-name
             {:_id (->object-id id)}
             {:$set (merge updates {:updated-at (java.util.Date.)})}))

(defn add-player
  "Add player ID to team's active players list"
  [team-id player-id]
  (mc/update (db) collection-name
             {:_id (->object-id team-id)}
             {:$addToSet {:active-player-ids (->object-id player-id)}
              :$set {:updated-at (java.util.Date.)}}))

(defn remove-player
  "Remove player ID from team's active players list"
  [team-id player-id]
  (mc/update (db) collection-name
             {:_id (->object-id team-id)}
             {:$pull {:active-player-ids (->object-id player-id)}
              :$set {:updated-at (java.util.Date.)}}))

(defn delete-by-id
  "Delete team by ID"
  [id]
  (mc/remove (db) collection-name {:_id (->object-id id)}))

(defn exists?
  "Check if team exists"
  [id]
  (some? (find-by-id id)))

(defn has-players?
  "Check if team has any active players"
  [team-id]
  (let [players (players-db/find-by-team team-id)]
    (seq players)))

