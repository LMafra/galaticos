(ns galaticos.logic.players-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [galaticos.logic.players :as logic]
            [galaticos.support.player-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(deftest create-rejects-missing-team
  (let [tid (ObjectId.)
        store (fixtures/player-store
                {:team-exists? (fn [_ _] false)
                 :create-player (fn [_ _] (throw (Exception. "should not run")))})]
    (try
      (logic/create! store {:name "P" :position "FW" :team-id tid})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 400 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "Team not found"))))))

(deftest create-success-adds-player-to-team
  (let [tid (ObjectId.)
        pid (ObjectId.)
        added (atom nil)
        store (fixtures/player-store
                {:team-exists? (fn [_ id] (= id tid))
                 :create-player (fn [_ d] (merge {:_id pid} d))
                 :add-player-to-team (fn [_ team-id player-id]
                                       (reset! added [team-id player-id]))})]
    (is (= pid (:_id (logic/create! store {:name "P" :position "FW" :team-id tid}))))
    (is (= [tid pid] @added))))

(deftest get-by-id-not-found
  (let [store (fixtures/player-store {})]
    (try
      (logic/get-by-id store (str (ObjectId.)))
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 404 (-> e ex-data :status)))))))

(deftest delete-not-found
  (let [store (fixtures/player-store {})]
    (try
      (logic/delete! store (str (ObjectId.)))
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 404 (-> e ex-data :status)))))))
