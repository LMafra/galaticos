(ns galaticos.support.championship-store-fixtures
  "Test doubles for ChampionshipStore protocol."
  (:require [galaticos.db.protocol.championship-store :refer [ChampionshipStore]])
  (:import [org.bson.types ObjectId]))

(defn- call-or [f default & args]
  (if f (apply f args) (if (fn? default) (apply default args) default)))

(defn championship-store
  "Build a ChampionshipStore reify. Pass optional fns in `overrides` map keyed by protocol method name."
  [overrides]
  (reify ChampionshipStore
    (find-all-championships [this]
      (call-or (:find-all-championships overrides) (constantly []) this))
    (find-championship-by-id [this id]
      (call-or (:find-championship-by-id overrides) (constantly nil) this id))
    (create-championship [this doc]
      (call-or (:create-championship overrides)
               (fn [_ d] (merge {:_id (ObjectId.)} d))
               this doc))
    (update-championship-by-id [this id updates]
      (call-or (:update-championship-by-id overrides) (constantly nil) this id updates))
    (delete-championship-by-id [this id]
      (call-or (:delete-championship-by-id overrides) (constantly nil) this id))
    (championship-exists? [this id]
      (call-or (:championship-exists? overrides) (constantly false) this id))
    (championship-has-matches? [this id]
      (call-or (:championship-has-matches? overrides) (constantly false) this id))
    (add-player-to-championship [this championship-id player-id]
      (call-or (:add-player-to-championship overrides) (constantly nil) this championship-id player-id))
    (remove-player-from-championship [this championship-id player-id]
      (call-or (:remove-player-from-championship overrides) (constantly nil) this championship-id player-id))
    (find-all-seasons-by-championship [this championship-id]
      (call-or (:find-all-seasons-by-championship overrides) (constantly []) this championship-id))
    (find-active-season-by-championship [this championship-id]
      (call-or (:find-active-season-by-championship overrides) (constantly nil) this championship-id))
    (create-season [this doc]
      (call-or (:create-season overrides)
               (fn [_ d] (merge {:_id (ObjectId.) :status "inactive"} d))
               this doc))
    (update-season-by-id [this id updates]
      (call-or (:update-season-by-id overrides) (constantly nil) this id updates))
    (delete-seasons-by-championship [this championship-id]
      (call-or (:delete-seasons-by-championship overrides) (constantly nil) this championship-id))
    (activate-season! [this season-id]
      (call-or (:activate-season! overrides) (constantly nil) this season-id))
    (finalize-season! [this season-id winner-player-ids titles-award-count]
      (call-or (:finalize-season! overrides) (constantly nil) this season-id winner-player-ids titles-award-count))
    (add-player-to-season [this season-id player-id]
      (call-or (:add-player-to-season overrides) (constantly nil) this season-id player-id))
    (remove-player-from-season [this season-id player-id]
      (call-or (:remove-player-from-season overrides) (constantly nil) this season-id player-id))
    (find-player-by-id [this player-id]
      (call-or (:find-player-by-id overrides) (constantly nil) this player-id))
    (find-players-by-ids [this player-ids]
      (call-or (:find-players-by-ids overrides) (constantly []) this player-ids))
    (increment-player-titles [this player-ids amount]
      (call-or (:increment-player-titles overrides) (constantly nil) this player-ids amount))
    (enrolled-player-object-ids [this championship-id]
      (call-or (:enrolled-player-object-ids overrides) (constantly []) this championship-id))))
