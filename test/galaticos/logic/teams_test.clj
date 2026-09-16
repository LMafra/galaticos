(ns galaticos.logic.teams-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [galaticos.logic.teams :as logic]
            [galaticos.support.team-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(deftest delete-conflict-when-has-players
  (let [id (str (ObjectId.))
        store (fixtures/team-store
                {:team-exists? (fn [_ x] (= x id))
                 :team-has-players? (fn [_ _] true)
                 :delete-team-by-id (fn [_ _] (throw (Exception. "should not run")))})]
    (try
      (logic/delete! store id)
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 409 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "associated players"))))))

(deftest delete-success-when-no-players
  (let [id (str (ObjectId.))
        deleted (atom false)
        store (fixtures/team-store
                {:team-exists? (fn [_ x] (= x id))
                 :team-has-players? (fn [_ _] false)
                 :delete-team-by-id (fn [_ _] (reset! deleted true))})]
    (is (= "Team deleted" (:message (logic/delete! store id))))
    (is (true? @deleted))))

(deftest get-by-id-not-found
  (let [store (fixtures/team-store {})]
    (try
      (logic/get-by-id store (str (ObjectId.)))
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 404 (-> e ex-data :status)))))))
