(ns galaticos.handlers.exports-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.exports :as handlers]
            [galaticos.export.csv :as export-csv]
            [galaticos.db.championships :as championships-db]))

(deftest export-dashboard-csv-test
  (testing "success"
    (let [csv "a,b\n1,2"
          result (with-redefs [export-csv/dashboard-csv (fn [] csv)]
                  (handlers/export-dashboard-csv {}))]
      (is (= 200 (:status result)))
      (is (= csv (:body result)))
      (is (= "text/csv; charset=utf-8" (get-in result [:headers "Content-Type"])))
      (is (re-find #"galaticos-dashboard\.csv"
                   (get-in result [:headers "Content-Disposition"])))))
  (testing "error from export"
    (let [result (with-redefs [export-csv/dashboard-csv (fn [] (throw (Exception. "boom")))]
                  (handlers/export-dashboard-csv {}))]
      (is (= 500 (:status result))))))

(deftest export-championship-csv-test
  (testing "championship not found"
    (let [id "507f1f77bcf86cd799439011"
          result (with-redefs [championships-db/find-by-id (fn [_] nil)]
                  (handlers/export-championship-csv {:params {:id id}}))]
      (is (= 404 (:status result)))))
  (testing "championship exists but no csv data"
    (let [id "507f1f77bcf86cd799439011"
          champ {:_id id :name "My Cup"}
          result (with-redefs [championships-db/find-by-id (fn [x] (when (= x id) champ))
                               export-csv/championship-csv (fn [_] nil)]
                  (handlers/export-championship-csv {:params {:id id}}))]
      (is (= 404 (:status result)))))
  (testing "success with named championship"
    (let [id "507f1f77bcf86cd799439011"
          champ {:_id id :name "My Cup 2024"}
          csv "x,y"
          result (with-redefs [championships-db/find-by-id (fn [x] (when (= x id) champ))
                               export-csv/championship-csv (fn [x] (when (= x id) csv))]
                  (handlers/export-championship-csv {:params {:id id}}))]
      (is (= 200 (:status result)))
      (is (= csv (:body result)))
      (is (re-find #"my-cup-2024\.csv"
                   (get-in result [:headers "Content-Disposition"])))))
  (testing "success falls back to championship filename when name is blank"
    (let [id "507f1f77bcf86cd799439011"
          champ {:_id id :name "!!!"}
          csv "x,y"
          result (with-redefs [championships-db/find-by-id (fn [x] (when (= x id) champ))
                               export-csv/championship-csv (fn [x] (when (= x id) csv))]
                  (handlers/export-championship-csv {:params {:id id}}))]
      (is (= 200 (:status result)))
      (is (re-find #"championship\.csv"
                   (get-in result [:headers "Content-Disposition"])))))
  (testing "unexpected error"
    (let [id "507f1f77bcf86cd799439011"
          result (with-redefs [championships-db/find-by-id (fn [_] (throw (Exception. "db")))]
                  (handlers/export-championship-csv {:params {:id id}}))]
      (is (= 500 (:status result))))))
