(ns galaticos.tasks.normalize-match-dates-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.tasks.normalize-match-dates :as task]
            [galaticos.db.core :as db]
            [galaticos.db.matches :as matches]
            [monger.collection :as mc])
  (:import [java.time Instant]
           [java.util Date]
           [org.bson.types ObjectId]))

(defn- mock-success-conn [] {:status :connected :db-name "galaticos"})

(deftest main-no-matches
  (testing "success path with zero match documents"
    (let [disconnected? (atom false)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [])
                    db/disconnect! (fn [] (reset! disconnected? true) {:status :disconnected})]
        (let [out (with-out-str (task/-main))]
          (is (re-find #"matches-scanned 0" out))
          (is @disconnected? "disconnect! must be called in finally"))))))

(deftest main-updates-string-dates
  (testing "string :date values are coerced and persisted"
    (let [update-calls (atom [])
          mid (ObjectId.)
          coerced (Date/from (Instant/parse "2024-06-15T12:00:00Z"))]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id mid :date "2024-06-15"}])
                    matches/coerce-match-date (fn [d] (when (= d "2024-06-15") coerced))
                    mc/update (fn [& args]
                                (swap! update-calls conj args))
                    db/disconnect! (fn [] nil)]
        (let [out (with-out-str (task/-main))]
          (is (= 1 (count @update-calls)))
          (is (re-find #"matches-updated 1" out)))))))

(deftest main-skips-already-date
  (testing "BSON Date documents are skipped"
    (let [update-calls (atom 0)
          existing (Date.)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id (ObjectId.) :date existing}])
                    mc/update (fn [& _] (swap! update-calls inc))
                    db/disconnect! (fn [] nil)]
        (let [out (with-out-str (task/-main))]
          (is (= 0 @update-calls))
          (is (re-find #"matches-skipped 1" out)))))))

(deftest main-skips-unparseable-string
  (testing "unparseable string dates increment skipped, not updated"
    (let [update-calls (atom 0)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id (ObjectId.) :date "not-a-date"}])
                    matches/coerce-match-date (constantly nil)
                    mc/update (fn [& _] (swap! update-calls inc))
                    db/disconnect! (fn [] nil)]
        (let [out (with-out-str (task/-main))]
          (is (= 0 @update-calls))
          (is (re-find #"matches-skipped 1" out)))))))

(deftest main-skips-nil-and-other-types
  (testing "nil and non-date types are skipped"
    (let [update-calls (atom 0)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id (ObjectId.) :date nil}
                                             {:_id (ObjectId.) :date 123}])
                    mc/update (fn [& _] (swap! update-calls inc))
                    db/disconnect! (fn [] nil)]
        (let [out (with-out-str (task/-main))]
          (is (= 0 @update-calls))
          (is (re-find #"matches-skipped 2" out)))))))

(deftest main-mixed-documents
  (testing "only string dates with successful coercion are updated"
    (let [update-calls (atom 0)
          coerced (Date.)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id (ObjectId.) :date "2024-01-01"}
                                             {:_id (ObjectId.) :date (Date.)}
                                             {:_id (ObjectId.) :date "bad"}
                                             {:_id (ObjectId.)}])
                    matches/coerce-match-date (fn [d]
                                                (when (= d "2024-01-01") coerced))
                    mc/update (fn [& _] (swap! update-calls inc))
                    db/disconnect! (fn [] nil)]
        (let [out (with-out-str (task/-main))]
          (is (= 1 @update-calls))
          (is (re-find #"matches-updated 1" out))
          (is (re-find #"matches-skipped 3" out)))))))

(deftest main-disconnect-called-on-success
  (testing "disconnect! runs in finally after successful scan"
    (let [disconnected? (atom false)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id (ObjectId.) :date (Date.)}])
                    db/disconnect! (fn [] (reset! disconnected? true) {:status :disconnected})]
        (task/-main)
        (is @disconnected?)))))
