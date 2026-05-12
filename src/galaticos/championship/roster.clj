(ns galaticos.championship.roster
  "Resolve enrolled player ObjectIds for a championship (active season roster, else union of seasons + root)."
  (:require [galaticos.db.championships :as championships-db]
            [galaticos.db.seasons :as seasons-db])
  (:import [org.bson.types ObjectId]))

(defn normalize-player-id-for-query
  [x]
  (cond
    (instance? ObjectId x) x
    (nil? x) nil
    :else (try (ObjectId. (str x))
               (catch Exception _ nil))))

(defn enrolled-player-object-ids
  "Distinct ObjectIds for players enrolled in this championship.
  Same semantics as get-championship-players: active season only if one exists,
  otherwise union of all seasons' enrolled-player-ids plus root championship list."
  [championship-id]
  (if-let [active-season (seasons-db/find-active-by-championship championship-id)]
    (vec (distinct (:enrolled-player-ids active-season [])))
    (if-let [championship (championships-db/find-by-id championship-id)]
      (let [season-rows (seasons-db/find-all-by-championship championship-id)
            season-ids (mapcat :enrolled-player-ids season-rows)
            root-ids (:enrolled-player-ids championship [])
            union-ids (->> (concat season-ids root-ids)
                           (map normalize-player-id-for-query)
                           (filter some?)
                           distinct
                           vec)]
        union-ids)
      [])))
