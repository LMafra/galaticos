(ns galaticos.logic.seasons-test
  (:require [clojure.test :refer [deftest is]]
            [galaticos.logic.seasons :as logic]
            [galaticos.support.championship-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(deftest get-by-id-not-found
  (let [store (fixtures/championship-store {})]
    (try
      (logic/get-by-id store (str (ObjectId.)))
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 404 (-> e ex-data :status)))))))

(deftest activate-not-found
  (let [store (fixtures/championship-store {})]
    (try
      (logic/activate! store (str (ObjectId.)))
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 404 (-> e ex-data :status)))))))

(deftest activate-success
  (let [id (str (ObjectId.))
        called (atom false)
        store (fixtures/championship-store
                {:season-exists? (fn [_ x] (= x id))
                 :activate-season! (fn [_ _] (reset! called true))})]
    (is (= "Season activated" (:message (logic/activate! store id))))
    (is (true? @called))))

(deftest enroll-not-found
  (let [store (fixtures/championship-store {})]
    (try
      (logic/enroll! store (str (ObjectId.)) (str (ObjectId.)))
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 404 (-> e ex-data :status)))))))

(deftest enroll-success
  (let [sid (str (ObjectId.))
        pid (str (ObjectId.))
        enrolled (atom nil)
        store (fixtures/championship-store
                {:season-exists? (fn [_ x] (= x sid))
                 :add-player-to-season (fn [_ season-id player-id]
                                         (reset! enrolled [season-id player-id]))})]
    (is (= "Player enrolled" (:message (logic/enroll! store sid pid))))
    (is (= [sid pid] @enrolled))))
