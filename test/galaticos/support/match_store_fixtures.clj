(ns galaticos.support.match-store-fixtures
  "Test doubles for MatchStore protocol."
  (:require [galaticos.db.protocol.match-store :refer [MatchStore]])
  (:import [org.bson.types ObjectId]))

(defn- call-or [f default & args]
  (if f (apply f args) (if (fn? default) (apply default args) default)))

(defn match-store
  "Build a MatchStore reify. Pass optional fns in `overrides` map keyed by protocol method name."
  [overrides]
  (reify MatchStore
    (find-all-matches [this]
      (call-or (:find-all-matches overrides) (constantly []) this))
    (find-match-by-id [this id]
      (call-or (:find-match-by-id overrides) (constantly nil) this id))
    (find-matches-by-championship [this championship-id]
      (call-or (:find-matches-by-championship overrides) (constantly []) this championship-id))
    (find-matches-by-season [this season-id]
      (call-or (:find-matches-by-season overrides) (constantly []) this season-id))
    (create-match [this match-data player-statistics opts]
      (call-or (:create-match overrides)
               (fn [_ md _ _] (merge {:_id (ObjectId.)} md))
               this match-data player-statistics opts))
    (update-match-by-id [this id updates opts]
      (call-or (:update-match-by-id overrides) (constantly nil) this id updates opts))
    (delete-match-by-id [this id]
      (call-or (:delete-match-by-id overrides) (constantly nil) this id))
    (match-exists? [this id]
      (call-or (:match-exists? overrides) (constantly false) this id))
    (find-season-by-id [this id]
      (call-or (:find-season-by-id overrides) (constantly nil) this id))
    (find-default-season-for-championship [this championship-id]
      (call-or (:find-default-season-for-championship overrides) (constantly nil) this championship-id))
    (find-season-for-new-match [this championship-id season-id]
      (call-or (:find-season-for-new-match overrides) (constantly nil) this championship-id season-id))
    (add-match-to-season! [this season-id match-id]
      (call-or (:add-match-to-season! overrides) (constantly nil) this season-id match-id))
    (remove-match-from-season! [this season-id match-id]
      (call-or (:remove-match-from-season! overrides) (constantly nil) this season-id match-id))
    (find-championship-by-id [this id]
      (call-or (:find-championship-by-id overrides) (constantly nil) this id))
    (find-players-by-ids [this ids]
      (call-or (:find-players-by-ids overrides) (constantly []) this ids))
    (find-team-by-id [this id]
      (call-or (:find-team-by-id overrides) (constantly nil) this id))
    (find-teams-by-ids [this ids]
      (call-or (:find-teams-by-ids overrides) (constantly []) this ids))))

(defn teams-by-id-fn
  "Helper: find-teams-by-ids that looks up each id via find-by-id fn."
  [find-by-id-fn]
  (fn [_ ids] (vec (keep find-by-id-fn ids))))
