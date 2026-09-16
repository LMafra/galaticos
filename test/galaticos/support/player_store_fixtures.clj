(ns galaticos.support.player-store-fixtures
  "Test doubles for PlayerStore protocol."
  (:require [galaticos.db.protocol.player-store :refer [PlayerStore]])
  (:import [org.bson.types ObjectId]))

(defn- call-or [f default & args]
  (if f (apply f args) (if (fn? default) (apply default args) default)))

(defn player-store
  "Build a PlayerStore reify. Pass optional fns in `overrides` map keyed by protocol method name."
  [overrides]
  (reify PlayerStore
    (find-all-players [this filters]
      (call-or (:find-all-players overrides) (constantly []) this filters))
    (find-active-players [this]
      (call-or (:find-active-players overrides) (constantly []) this))
    (find-player-by-id [this id]
      (call-or (:find-player-by-id overrides) (constantly nil) this id))
    (player-exists? [this id]
      (call-or (:player-exists? overrides) (constantly false) this id))
    (create-player [this doc]
      (call-or (:create-player overrides)
               (fn [_ d] (merge {:_id (ObjectId.)} d))
               this doc))
    (update-player-by-id [this id updates]
      (call-or (:update-player-by-id overrides) (constantly nil) this id updates))
    (delete-player-by-id [this id]
      (call-or (:delete-player-by-id overrides) (constantly nil) this id))
    (find-team-by-id [this id]
      (call-or (:find-team-by-id overrides) (constantly nil) this id))
    (team-exists? [this id]
      (call-or (:team-exists? overrides) (constantly false) this id))
    (add-player-to-team [this team-id player-id]
      (call-or (:add-player-to-team overrides) (constantly nil) this team-id player-id))))
