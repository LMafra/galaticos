(ns galaticos.logic.matches-test
  (:require [clojure.test :refer [deftest is]]
            [galaticos.db.protocol.match-store :refer [MatchStore]]
            [galaticos.logic.matches :as logic])
  (:import [org.bson.types ObjectId]))

(defn- noop-store
  []
  (reify MatchStore
    (find-all-matches [_] [])
    (find-match-by-id [_ _] nil)
    (find-matches-by-championship [_ _] [])
    (find-matches-by-season [_ _] [])
    (create-match [_ _ _ _] nil)
    (update-match-by-id [_ _ _ _] nil)
    (delete-match-by-id [_ _] nil)
    (match-exists? [_ _] false)
    (find-season-by-id [_ _] nil)
    (find-default-season-for-championship [_ _] nil)
    (find-season-for-new-match [_ _ _] nil)
    (add-match-to-season! [_ _ _] nil)
    (remove-match-from-season! [_ _ _] nil)
    (find-championship-by-id [_ _] nil)
    (find-players-by-ids [_ _] [])
    (find-team-by-id [_ _] nil)
    (find-teams-by-ids [_ _] [])))

(deftest create-rejects-no-active-season
  (let [champ-id (ObjectId.)
        store (reify MatchStore
                (find-all-matches [_] [])
                (find-match-by-id [_ _] nil)
                (find-matches-by-championship [_ _] [])
                (find-matches-by-season [_ _] [])
                (create-match [_ _ _ _] nil)
                (update-match-by-id [_ _ _ _] nil)
                (delete-match-by-id [_ _] nil)
                (match-exists? [_ _] false)
                (find-season-by-id [_ _] nil)
                (find-default-season-for-championship [_ _] nil)
                (find-season-for-new-match [_ _ _] nil)
                (add-match-to-season! [_ _ _] nil)
                (remove-match-from-season! [_ _ _] nil)
                (find-championship-by-id [_ _] nil)
                (find-players-by-ids [_ _] [])
                (find-team-by-id [_ _] nil)
                (find-teams-by-ids [_ _] []))
        request {}
        data {:championship-id champ-id
              :player-statistics [{:player-id (ObjectId.) :team-id (ObjectId.)}]}]
    (try
      (logic/create! store request data)
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 400 (-> e ex-data :status)))
        (is (clojure.string/includes? (-> e ex-data :message) "No active season"))))))

(deftest delete-python-seed-without-force
  (let [id (str (ObjectId.))
        mid (ObjectId. id)
        store (reify MatchStore
                (find-all-matches [_] [])
                (find-match-by-id [_ x] (when (= x id) {:_id mid :data-source "python-seed"}))
                (find-matches-by-championship [_ _] [])
                (find-matches-by-season [_ _] [])
                (create-match [_ _ _ _] nil)
                (update-match-by-id [_ _ _ _] nil)
                (delete-match-by-id [_ _] (throw (Exception. "should not run")))
                (match-exists? [_ x] (= x id))
                (find-season-by-id [_ _] nil)
                (find-default-season-for-championship [_ _] nil)
                (find-season-for-new-match [_ _ _] nil)
                (add-match-to-season! [_ _ _] nil)
                (remove-match-from-season! [_ _ _] nil)
                (find-championship-by-id [_ _] nil)
                (find-players-by-ids [_ _] [])
                (find-team-by-id [_ _] nil)
                (find-teams-by-ids [_ _] []))]
    (try
      (logic/delete! store id {:params {}})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 403 (-> e ex-data :status)))
        (is (clojure.string/includes? (-> e ex-data :message) "python-seed"))))))
