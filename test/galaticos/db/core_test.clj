(ns galaticos.db.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.db.core :as db]
            [monger.core :as mg]))

(deftest connect!
  (testing "returns error when connection fails"
    (let [result (with-redefs [mg/connect-via-uri (fn [_] (throw (RuntimeException. "connection refused")))]
                  (db/connect! "mongodb://invalid:27017/test" "test"))]
      (is (= :error (:status result)))
      (is (string? (:message result))))))

(deftest disconnect!
  (testing "returns disconnected when was connected"
    (with-redefs [mg/connect-via-uri (fn [_] {:conn :mock-conn :db :mock-db})
                  mg/disconnect (fn [_] nil)]
      (db/connect! "mongodb://localhost:27017/test" "test")
      (let [result (db/disconnect!)]
        (is (= :disconnected (:status result))))))

(deftest db-and-connection
  (testing "db returns instance after connect"
    (with-redefs [mg/connect-via-uri (fn [_] {:conn :mock-conn :db :mock-db})
                  mg/disconnect (fn [_] nil)]
      (db/connect! "mongodb://localhost:27017/test" "test")
      (is (= :mock-db (db/db)))
      (is (= :mock-conn (db/connection)))
      (db/disconnect!)))))
