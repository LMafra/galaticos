(ns galaticos.db.player-merge-refs
  "Rewrite MongoDB references when merging duplicate player documents."
  (:require [monger.collection :as mc]
            [monger.operators :refer [$elemMatch $in $set]]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]])
  (:import [org.bson.types ObjectId]))

(defn- safe-int [x]
  (cond
    (number? x) (long x)
    (nil? x) 0
    :else (try (long x) (catch Exception _ 0))))

(defn- remap-enrolled [player-ids merged-set ^ObjectId master-id]
  (->> player-ids
       (map (fn [pid]
              (let [oid (if (instance? ObjectId pid) pid (->object-id pid))]
                (if (merged-set oid) master-id oid))))
       distinct
       vec))

(defn- merge-stat-rows-by-player [rows]
  (vals
   (reduce (fn [acc row]
             (let [k (:player-id row)]
               (update acc k (fn [existing]
                               (if existing
                                 (-> existing
                                     (update :goals + (safe-int (:goals row)))
                                     (update :assists + (safe-int (:assists row)))
                                     (update :yellow-cards + (safe-int (:yellow-cards row)))
                                     (update :red-cards + (safe-int (:red-cards row)))
                                     (update :minutes-played + (safe-int (:minutes-played row))))
                                 row)))))
           {}
           rows)))

(defn rewrite-match-player-lines!
  "Replace merged player ids with master in matches.player-statistics; consolidate duplicate lines."
  [master-id merged-ids master-display-name]
  (let [master-oid (->object-id master-id)
        merged-oids (mapv ->object-id merged-ids)
        mset (set merged-oids)
        dbi (db)
        coll "matches"]
    (doseq [m (mc/find-maps dbi coll
                         {:player-statistics {$elemMatch {:player-id {$in merged-oids}}}})]
      (let [stats (:player-statistics m)]
        (when (sequential? stats)
          (let [new-stats (->> stats
                               (map (fn [row]
                                      (let [pid (:player-id row)
                                            oid (if (instance? ObjectId pid) pid (->object-id pid))]
                                        (if (mset oid)
                                          (-> row
                                              (assoc :player-id master-oid)
                                              (assoc :player-name master-display-name))
                                          row))))
                               merge-stat-rows-by-player
                               vec)]
            (mc/update dbi coll {:_id (:_id m)}
                       {$set {:player-statistics new-stats
                              :updated-at (java.util.Date.)}})))))))

(defn rewrite-championship-enrollments!
  [master-id merged-ids]
  (let [master-oid (->object-id master-id)
        merged-oids (set (map ->object-id merged-ids))]
    (doseq [doc (mc/find-maps (db) "championships" {})]
      (when-let [enrolled (:enrolled-player-ids doc)]
        (when (some merged-oids enrolled)
          (let [newv (remap-enrolled enrolled merged-oids master-oid)]
            (mc/update (db) "championships" {:_id (:_id doc)}
                       {$set {:enrolled-player-ids newv
                              :updated-at (java.util.Date.)}})))))))

(defn rewrite-season-enrollments-and-winners!
  [master-id merged-ids]
  (let [master-oid (->object-id master-id)
        merged-oids (set (map ->object-id merged-ids))]
    (doseq [doc (mc/find-maps (db) "seasons" {})]
      (let [updates (cond-> {}
                      (some merged-oids (:enrolled-player-ids doc))
                      (assoc :enrolled-player-ids (remap-enrolled (:enrolled-player-ids doc) merged-oids master-oid))

                      (some merged-oids (:winner-player-ids doc))
                      (assoc :winner-player-ids (remap-enrolled (:winner-player-ids doc) merged-oids master-oid))

                      (some merged-oids (:top-scorer-ids doc))
                      (assoc :top-scorer-ids (remap-enrolled (:top-scorer-ids doc) merged-oids master-oid))

                      (some merged-oids (:top-assister-ids doc))
                      (assoc :top-assister-ids (remap-enrolled (:top-assister-ids doc) merged-oids master-oid)))]
        (when (seq updates)
          (mc/update (db) "seasons" {:_id (:_id doc)}
                     {$set (merge updates {:updated-at (java.util.Date.)})}))))))

(defn rewrite-team-active-players!
  [master-id merged-ids]
  (let [master-oid (->object-id master-id)
        merged-oids (set (map ->object-id merged-ids))]
    (doseq [doc (mc/find-maps (db) "teams" {})]
      (when-let [active (:active-player-ids doc)]
        (when (some merged-oids active)
          (let [newv (remap-enrolled active merged-oids master-oid)]
            (mc/update (db) "teams" {:_id (:_id doc)}
                       {$set {:active-player-ids newv
                              :updated-at (java.util.Date.)}})))))))

(defn rewrite-all-player-refs!
  [master-id merged-ids master-display-name]
  (rewrite-match-player-lines! master-id merged-ids master-display-name)
  (rewrite-championship-enrollments! master-id merged-ids)
  (rewrite-season-enrollments-and-winners! master-id merged-ids)
  (rewrite-team-active-players! master-id merged-ids))
