(ns galaticos.db.seasons
  "Database operations for seasons collection"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.db.players :as players-db]
            [galaticos.db.championships :as championships-db]
            [galaticos.util.response :refer [->object-id]]
            [clojure.tools.logging :as log])
  (:import [org.bson.types ObjectId]))

(def collection-name "seasons")

(defn find-by-id
  [id]
  (mc/find-one-as-map (db) collection-name {:_id (->object-id id)}))

(defn find-all-by-championship
  [championship-id]
  (mc/find-maps (db) collection-name
               {:championship-id (->object-id championship-id)}))

(defn find-active-by-championship
  [championship-id]
  (mc/find-one-as-map (db) collection-name
                        {:championship-id (->object-id championship-id)
                         :status "active"}))

(defn exists?
  [id]
  (some? (find-by-id id)))

(defn find-by-championship-and-season
  [championship-id season]
  (mc/find-one-as-map
   (db) collection-name
   {:championship-id (->object-id championship-id)
    :season season}))

(defn- now []
  (java.util.Date.))

(defn create
  "Create a new season.
   Enforces uniqueness by (championship-id, season) at application level."
  [{:keys [championship-id season status format start-date end-date]
    :as season-data}]
  (let [existing (when (and championship-id season)
                    (find-by-championship-and-season championship-id season))]
    (when existing
      (throw (ex-info "Season already exists for championship+season"
                      {:status 409
                       :championship-id championship-id
                       :season season})))
    (let [id (ObjectId.)
          created-at (now)
          status (or status "inactive")
          ;; Do not let nil :enrolled-player-ids from callers overwrite the default []; Mongo $addToSet fails on null.
          doc (merge {:championship-id (->object-id championship-id)
                       :season season
                       :status status
                       :format format
                       :enrolled-player-ids []
                       :match-ids []
                       :winner-player-ids []
                       :titles-award-count 0
                       :titles-count 0
                       :start-date start-date
                       :end-date end-date
                       :finished-at nil
                       :created-at created-at
                       :updated-at created-at}
                      (dissoc season-data :enrolled-player-ids)
                      (when (some? (:enrolled-player-ids season-data))
                        {:enrolled-player-ids (:enrolled-player-ids season-data)})
                      {:_id id
                       :championship-id (->object-id championship-id)})]
      (mc/insert (db) collection-name doc)
      ;; Link to root championship
      (when championship-id
        (try
          (championships-db/add-season-id championship-id id)
          (catch Exception e
            (log/warn e "Failed to update championships.season-ids linkage"))))
      doc)))

(defn update-by-id
  [id updates]
  (mc/update (db) collection-name
             {:_id (->object-id id)}
             {:$set (merge updates {:updated-at (now)})}))

(defn delete-by-id
  [id]
  (mc/remove (db) collection-name {:_id (->object-id id)}))

(defn delete-by-championship
  [championship-id]
  (mc/remove (db) collection-name
             {:championship-id (->object-id championship-id)}))

(defn add-player
  [season-id player-id]
  (mc/update (db) collection-name
              {:_id (->object-id season-id)}
              {:$addToSet {:enrolled-player-ids (->object-id player-id)}
               :$set {:updated-at (now)}}))

(defn remove-player
  [season-id player-id]
  (mc/update (db) collection-name
              {:_id (->object-id season-id)}
              {:$pull {:enrolled-player-ids (->object-id player-id)}
               :$set {:updated-at (now)}}))

(defn get-players
  [season-id]
  (when-let [season (find-by-id season-id)]
    (players-db/find-by-ids (:enrolled-player-ids season []))))

(defn add-match
  [season-id match-id]
  (mc/update (db) collection-name
              {:_id (->object-id season-id)}
              {:$addToSet {:match-ids (->object-id match-id)}
               :$set {:updated-at (now)}}))

(defn remove-match
  [season-id match-id]
  (mc/update (db) collection-name
              {:_id (->object-id season-id)}
              {:$pull {:match-ids (->object-id match-id)}
               :$set {:updated-at (now)}}))

(defn activate!
  "Set this season active and deactivate other seasons of the same championship."
  [season-id]
  (when-let [season (find-by-id season-id)]
    (let [championship-id (:championship-id season)
          now-ts (now)]
      ;; Deactivate all existing active seasons
      (mc/update (db) collection-name
                  {:championship-id championship-id
                   :status "active"}
                  {:$set {:status "inactive"
                          :updated-at now-ts}})
      ;; Activate chosen one
      (mc/update (db) collection-name
                  {:_id (->object-id season-id)}
                  {:$set {:status "active"
                          :updated-at now-ts}}))))

(defn finalize!
  "Finalize a season and award titles to winners."
  [season-id winner-player-ids titles-award-count]
  (let [titles-award-count (or titles-award-count 1)
        now-ts (now)
        winners (mapv ->object-id winner-player-ids)]
    (when-let [season (find-by-id season-id)]
      (let [enrolled (set (:enrolled-player-ids season []))
            not-enrolled (remove #(contains? enrolled %) winners)]
        (when (seq not-enrolled)
          (throw (ex-info "Winners must be enrolled in the season"
                          {:status 400
                           :not-enrolled (map str not-enrolled)})))
        (update-by-id season-id
                       {:status "completed"
                        :finished-at now-ts
                        :winner-player-ids winners
                        :titles-award-count titles-award-count
                        :titles-count titles-award-count})
        (when (pos? titles-award-count)
          (players-db/increment-titles winners titles-award-count))
        (find-by-id season-id)))))

(defn get-enrolled-player-ids
  [season-id]
  (when-let [season (find-by-id season-id)]
    (:enrolled-player-ids season [])))

