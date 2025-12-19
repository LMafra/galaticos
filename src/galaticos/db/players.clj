(ns galaticos.db.players
  "Database operations for players collection"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]])
  (:import [org.bson.types ObjectId]))

(def collection-name "players")

(defn- normalize-player-data [player-data]
  (let [team-id (:team-id player-data)]
    (cond-> player-data
      team-id (update :team-id ->object-id))))

(defn create
  "Create a new player and return the inserted document"
  [player-data]
  (let [now (java.util.Date.)
        id (ObjectId.)
        doc (-> player-data
                normalize-player-data
                (merge {:_id id
                        :active true
                        :aggregated-stats {:total {:games 0 :goals 0 :assists 0 :titles 0}
                                          :by-championship []}
                        :created-at now
                        :updated-at now}))]
    (mc/insert (db) collection-name doc)
    doc))

(defn find-by-id
  "Find player by ID"
  [id]
  (mc/find-one-as-map (db) collection-name {:_id (->object-id id)}))

(defn find-all
  "Find all players, optionally filtered"
  ([]
   (find-all {}))
  ([filters]
   (let [normalized (cond-> filters
                      (:team-id filters) (update :team-id ->object-id))]
     (mc/find-maps (db) collection-name normalized))))

(defn find-active
  "Find all active players"
  []
  (find-all {:active true}))

(defn find-by-team
  "Find players by team ID"
  [team-id]
  (find-all {:team-id (->object-id team-id) :active true}))

(defn find-by-position
  "Find players by position"
  [position]
  (find-all {:position position :active true}))

(defn find-by-name
  "Find players by name (partial match)"
  [name]
  (mc/find-maps (db) collection-name
                {:name {:$regex name :$options "i"}}))

(defn update-by-id
  "Update player by ID"
  [id updates]
  (let [normalized (normalize-player-data updates)]
    (mc/update (db) collection-name
               {:_id (->object-id id)}
               {:$set (merge normalized {:updated-at (java.util.Date.)})})))

(defn update-stats
  "Update player aggregated statistics"
  [player-id stats]
  (update-by-id player-id {:aggregated-stats stats}))

(defn delete-by-id
  "Delete player by ID (soft delete by setting active to false)"
  [id]
  (update-by-id id {:active false}))

(defn exists?
  "Check if player exists"
  [id]
  (some? (find-by-id id)))

