(ns galaticos.logic.teams-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [galaticos.logic.teams :as logic])
  (:import [org.bson.types ObjectId]))

(deftest delete-conflict-when-has-players
  (let [id (str (ObjectId.))]
    (try
      (with-redefs [galaticos.db.teams/exists? (fn [x] (= x id))
                    galaticos.db.teams/has-players? (fn [_] true)]
        (logic/delete! id))
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 409 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "associated players"))))))
