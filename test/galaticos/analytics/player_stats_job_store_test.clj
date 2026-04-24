(ns galaticos.analytics.player-stats-job-store-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.analytics.player-stats-job-store :as store]
            [galaticos.db.core :as db]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

;;; ── record-incremental-success! ────────────────────────────────────────────

(deftest record-incremental-success-calls-upsert
  (testing "happy path: mc/update called with upsert true"
    (let [calls (atom [])]
      (with-redefs [db/db (fn [] :mock-db)
                    mc/update (fn [_ coll _ data opts]
                                (swap! calls conj {:coll coll :data data :opts opts}))]
        (store/record-incremental-success!
         "job-1" :test {:match-id nil :op :create} :incremental {:updated 3} 42)
        (is (= 1 (count @calls)))
        (is (= {:upsert true} (:opts (first @calls))))
        (is (contains? (get-in (first @calls) [:data :$set]) :last-incremental))
        (is (contains? (get-in (first @calls) [:data :$set]) :updated-at))))))

(deftest record-incremental-success-stores-correct-fields
  (testing "last-incremental row contains expected keys"
    (let [stored (atom nil)]
      (with-redefs [db/db (fn [] :mock-db)
                    mc/update (fn [_ _ _ data _]
                                (reset! stored (get-in data [:$set :last-incremental])))]
        (store/record-incremental-success!
         "job-42" :after-match {:match-id nil :op :update} :incremental {:updated 5} 100)
        (is (= "job-42" (:job-id @stored)))
        (is (= :after-match (:reason @stored)))
        (is (= :update (:op @stored)))
        (is (= :incremental (:recalc @stored)))
        (is (= 5 (:updated @stored)))
        (is (= 100 (:duration-ms @stored)))
        (is (instance? java.util.Date (:finished-at @stored)))))))

(deftest record-incremental-success-coerces-match-id-to-string
  (testing "ObjectId match-id coerced to string"
    (let [stored (atom nil)
          mid (ObjectId.)]
      (with-redefs [db/db (fn [] :mock-db)
                    mc/update (fn [_ _ _ data _]
                                (reset! stored (get-in data [:$set :last-incremental])))]
        (store/record-incremental-success!
         "j" :r {:match-id mid :op :delete} :full {:updated 1} 50)
        (is (string? (:match-id @stored)))
        (is (= (str mid) (:match-id @stored)))))))

(deftest record-incremental-success-nil-match-id
  (testing "nil match-id stored as nil"
    (let [stored (atom :not-set)]
      (with-redefs [db/db (fn [] :mock-db)
                    mc/update (fn [_ _ _ data _]
                                (reset! stored (get-in data [:$set :last-incremental])))]
        (store/record-incremental-success!
         "j" :r {:match-id nil :op :create} :incremental {:updated 0} 0)
        (is (nil? (:match-id @stored)))))))

(deftest record-incremental-success-swallows-exception
  (testing "exception from mc/update caught; function returns nil without rethrowing"
    (with-redefs [db/db (fn [] :mock-db)
                  mc/update (fn [& _] (throw (Exception. "db down")))]
      (is (nil? (store/record-incremental-success!
                 "j" :r {} :incremental {} 0))))))

;;; ── record-full-success! ────────────────────────────────────────────────────

(deftest record-full-success-calls-upsert
  (testing "happy path: mc/update called with upsert true and last-full key"
    (let [calls (atom [])]
      (with-redefs [db/db (fn [] :mock-db)
                    mc/update (fn [_ coll _ data opts]
                                (swap! calls conj {:coll coll :data data :opts opts}))]
        (store/record-full-success! "job-2" :manual :sync {:updated 5} 200)
        (is (= 1 (count @calls)))
        (is (= {:upsert true} (:opts (first @calls))))
        (is (contains? (get-in (first @calls) [:data :$set]) :last-full))))))

(deftest record-full-success-stores-correct-fields
  (testing "last-full row has expected keys and values"
    (let [stored (atom nil)]
      (with-redefs [db/db (fn [] :mock-db)
                    mc/update (fn [_ _ _ data _]
                                (reset! stored (get-in data [:$set :last-full])))]
        (store/record-full-success! "job-99" :reconcile :async {:updated 12} 350)
        (is (= "job-99" (:job-id @stored)))
        (is (= :reconcile (:reason @stored)))
        (is (= :full (:recalc @stored)))
        (is (= :async (:recalc-execution @stored)))
        (is (= 12 (:updated @stored)))
        (is (= 350 (:duration-ms @stored)))
        (is (instance? java.util.Date (:finished-at @stored)))))))

(deftest record-full-success-swallows-exception
  (testing "exception from mc/update caught; returns nil"
    (with-redefs [db/db (fn [] :mock-db)
                  mc/update (fn [& _] (throw (Exception. "fail")))]
      (is (nil? (store/record-full-success! "j" :r :sync {} 0))))))

;;; ── fetch-doc ───────────────────────────────────────────────────────────────

(deftest fetch-doc-returns-document
  (testing "returns the metadata document when found"
    (with-redefs [db/db (fn [] :mock-db)
                  mc/find-one-as-map (fn [& _] {:_id "player-stats-jobs" :last-full {}})]
      (let [r (store/fetch-doc)]
        (is (map? r))
        (is (= "player-stats-jobs" (:_id r)))))))

(deftest fetch-doc-returns-nil-when-not-found
  (testing "returns nil when document does not exist"
    (with-redefs [db/db (fn [] :mock-db)
                  mc/find-one-as-map (fn [& _] nil)]
      (is (nil? (store/fetch-doc))))))

(deftest fetch-doc-swallows-exception
  (testing "exception from mc/find-one-as-map is caught; returns nil"
    (with-redefs [db/db (fn [] :mock-db)
                  mc/find-one-as-map (fn [& _] (throw (Exception. "db err")))]
      (is (nil? (store/fetch-doc))))))
