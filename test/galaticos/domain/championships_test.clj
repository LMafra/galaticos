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

(deftest finalization-decision-only-active-championship
  (let [champ {:status "completed" :enrolled-player-ids []}]
    (is (= "Only active championships can be finalized"
           (get-in (domain/finalization-decision nil champ [] 0) [:error :message])))))

(deftest parse-titles-award-count-test
  (is (= 2 (domain/parse-titles-award-count 2)))
  (is (= :galaticos.domain.championships/invalid (domain/parse-titles-award-count "nope"))))
