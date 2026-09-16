(ns galaticos.finalization-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [galaticos.finalization :as fin]))

(deftest parse-titles-count-test
  (is (= 0 (fin/parse-titles-count nil)))
  (is (= 0 (fin/parse-titles-count "")))
  (is (= 2 (fin/parse-titles-count "2")))
  (is (= 3 (fin/parse-titles-count 3))))

(deftest reason-valid?-test
  (is (false? (fin/reason-valid? nil)))
  (is (false? (fin/reason-valid? "ab")))
  (is (false? (fin/reason-valid? "  ab  ")))
  (is (true? (fin/reason-valid? "ok!")))
  (is (true? (fin/reason-valid? "  Encerrar época  "))))

(deftest finalization-checklist-test
  (testing "incomplete blocks finalize"
    (let [items (fin/finalization-checklist
                 {:status "active"
                  :finished-at nil
                  :enrolled-count 0
                  :max-players 22
                  :titles-award-count 1
                  :winner-ids #{}
                  :match-count 0
                  :reason ""})]
      (is (false? (fin/checklist-complete? items)))
      (is (false? (:ok? (first (filter #(= :has-roster (:id %)) items)))))
      (is (false? (:ok? (first (filter #(= :winners (:id %)) items)))))
      (is (false? (:ok? (first (filter #(= :reason (:id %)) items)))))))
  (testing "complete when all green"
    (let [items (fin/finalization-checklist
                 {:status "active"
                  :finished-at nil
                  :enrolled-count 18
                  :max-players 22
                  :titles-award-count 1
                  :winner-ids #{"p1"}
                  :match-count 3
                  :reason "Fim da época 2026"})]
      (is (true? (fin/checklist-complete? items)))
      (is (= "7/7 requisitos cumpridos" (fin/checklist-summary-label items)))))
  (testing "zero titles skips winners requirement"
    (let [items (fin/finalization-checklist
                 {:status "active"
                  :enrolled-count 5
                  :max-players nil
                  :titles-award-count 0
                  :winner-ids #{}
                  :match-count 1
                  :reason "Encerrar sem títulos"})]
      (is (true? (:ok? (first (filter #(= :winners (:id %)) items)))))
      (is (true? (fin/checklist-complete? items)))))
  (testing "inactive or finished fail"
    (is (false? (:ok? (first (filter #(= :active (:id %))
                                     (fin/finalization-checklist
                                      {:status "completed"
                                       :enrolled-count 1
                                       :match-count 1
                                       :reason "abc"
                                       :titles-award-count 0}))))))
    (is (false? (:ok? (first (filter #(= :not-finished (:id %))
                                     (fin/finalization-checklist
                                      {:status "active"
                                       :finished-at "2026-01-01"
                                       :enrolled-count 1
                                       :match-count 1
                                       :reason "abc"
                                       :titles-award-count 0}))))))))
