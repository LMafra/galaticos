(ns galaticos.db.merge-audit
  "Audit trail for player merge operations."
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]])
  (:import [org.bson.types ObjectId]))

(def collection-name "merge-audit")

(defn create!
  "Insert merge audit document. Returns inserted doc with generated _id."
  [{:keys [master-id merged-ids field-selections admin-id admin-username before-state after-state]}]
  (let [now (java.util.Date.)
        id (ObjectId.)
        doc {:_id id
             :type "player-merge"
             :master-id (->object-id master-id)
             :merged-ids (mapv ->object-id merged-ids)
             :field-selections field-selections
             :admin-id (some-> admin-id ->object-id)
             :admin-username admin-username
             :merged-at now
             :before-state before-state
             :after-state after-state
             :created-at now}]
    (mc/insert (db) collection-name doc)
    doc))

(defn find-by-id
  [id]
  (mc/find-one-as-map (db) collection-name {:_id (->object-id id)}))
