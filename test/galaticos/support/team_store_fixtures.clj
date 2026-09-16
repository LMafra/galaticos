(ns galaticos.support.team-store-fixtures
  "Test doubles for TeamStore protocol."
  (:require [galaticos.db.protocol.team-store :refer [TeamStore]])
  (:import [org.bson.types ObjectId]))

(defn- call-or [f default & args]
  (if f (apply f args) (if (fn? default) (apply default args) default)))

(defn team-store
  "Build a TeamStore reify. Pass optional fns in `overrides` map keyed by protocol method name."
  [overrides]
  (reify TeamStore
    (find-all-teams [this]
      (call-or (:find-all-teams overrides) (constantly []) this))
    (find-team-by-id [this id]
      (call-or (:find-team-by-id overrides) (constantly nil) this id))
    (create-team [this doc]
      (call-or (:create-team overrides)
               (fn [_ d] (merge {:_id (ObjectId.)} d))
               this doc))
    (update-team-by-id [this id updates]
      (call-or (:update-team-by-id overrides) (constantly nil) this id updates))
    (delete-team-by-id [this id]
      (call-or (:delete-team-by-id overrides) (constantly nil) this id))
    (team-exists? [this id]
      (call-or (:team-exists? overrides) (constantly false) this id))
    (team-has-players? [this id]
      (call-or (:team-has-players? overrides) (constantly false) this id))
    (add-player-to-team [this team-id player-id]
      (call-or (:add-player-to-team overrides) (constantly nil) this team-id player-id))
    (remove-player-from-team [this team-id player-id]
      (call-or (:remove-player-from-team overrides) (constantly nil) this team-id player-id))))
