(ns galaticos.domain.championships-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.domain.championships :as domain])
  (:import [org.bson.types ObjectId]))

(deftest can-delete-test
  (testing "not found"
    (is (= :not-found (get-in (domain/can-delete? false false) [:error :type]))))
  (testing "conflict when matches"
    (is (= :conflict (get-in (domain/can-delete? true true) [:error :type]))))
  (testing "ok when no matches"
    (is (= true (:ok (domain/can-delete? true false))))))

(deftest enrollment-decision-test
  (is (= :conflict
         (get-in (domain/enrollment-decision
                  {:enrolled-count 1 :max-players 1 :already-enrolled? false :scope :season})
                 [:error :type]))))

(deftest enrich-total-titles-test
  (let [cid (ObjectId.)
        champ {:_id cid :name "C1"}
        seasons [{:titles-count 2 :season "2024" :updated-at (java.util.Date. 1000)}
                 {:titles-count 3 :season "2025" :updated-at (java.util.Date. 2000)}]
        enriched (domain/enrich champ {:all-seasons seasons :active-season nil})]
    (is (= 5 (:total-titles-across-seasons enriched)))
    (is (= "2025" (:season enriched)))))

(deftest enrich-tolerates-string-titles-count-test
  "Production Mongo may store titles-count as string; list must not 500."
  (let [champ {:_id (ObjectId.) :name "Legacy"}
        seasons [{:titles-count "2" :season "2024"}
                 {:titles-count "3" :season "2025" :updated-at (java.util.Date.)}]
        enriched (domain/enrich champ {:all-seasons seasons :active-season nil})]
    (is (= 5 (:total-titles-across-seasons enriched)))
    (is (= "2025" (:season enriched)))))

(deftest pick-latest-season-unparseable-label-no-overflow-test
  "VPS prod: (- Long/MIN_VALUE) overflow when season label is not a plain year."
  (let [champ {:_id (ObjectId.) :name "Overflow"}
        seasons [{:season "2024/2025" :titles-count 1 :updated-at (java.util.Date. 1000)}
                 {:season "abc" :titles-count 2 :updated-at (java.util.Date. 2000)}
                 {:season "2025" :titles-count 3 :updated-at (java.util.Date. 500)}]
        enriched (domain/enrich champ {:all-seasons seasons :active-season nil})]
    (is (= "2025" (:season enriched)))
    (is (= 6 (:total-titles-across-seasons enriched)))))

(deftest sum-season-titles-coerces-test
  (is (= 0 (domain/sum-season-titles-across nil)))
  (is (= 0 (domain/sum-season-titles-across [])))
  (is (= 3 (domain/sum-season-titles-across [{:titles-count "1"} {:titles-count 2}])))
  (is (= 0 (domain/sum-season-titles-across [{:titles-count "nope"}]))))

(deftest finalization-decision-only-active-championship
  (let [champ {:status "completed" :enrolled-player-ids []}]
    (is (= "Only active championships can be finalized"
           (get-in (domain/finalization-decision nil champ [] 0) [:error :message])))))

(deftest parse-titles-award-count-test
  (is (= 2 (domain/parse-titles-award-count 2)))
  (is (= :galaticos.domain.championships/invalid (domain/parse-titles-award-count "nope"))))
