(ns galaticos.enrollment-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [galaticos.enrollment :as enroll]))

(deftest normalize-max-players-test
  (testing "nil and invalid → unlimited"
    (is (nil? (enroll/normalize-max-players nil)))
    (is (nil? (enroll/normalize-max-players "")))
    (is (nil? (enroll/normalize-max-players "abc")))
    (is (nil? (enroll/normalize-max-players -1))))
  (testing "numbers and numeric strings"
    (is (= 22 (enroll/normalize-max-players 22)))
    (is (= 0 (enroll/normalize-max-players 0)))
    (is (= 10.0 (enroll/normalize-max-players "10")))))

(deftest at-limit?-test
  (testing "unlimited never at limit"
    (is (false? (enroll/at-limit? 100 nil)))
    (is (false? (enroll/at-limit? 0 nil))))
  (testing "below and at max"
    (is (false? (enroll/at-limit? 18 22)))
    (is (true? (enroll/at-limit? 22 22)))
    (is (true? (enroll/at-limit? 23 22))))
  (testing "zero max blocks any enroll"
    (is (true? (enroll/at-limit? 0 0)))))

(deftest can-enroll?-test
  (is (true? (enroll/can-enroll? 18 22)))
  (is (false? (enroll/can-enroll? 22 22)))
  (is (true? (enroll/can-enroll? 5 nil))))

(deftest progress-ratio-test
  (is (nil? (enroll/progress-ratio 5 nil)))
  (is (= 1.0 (enroll/progress-ratio 0 0)))
  (is (= 0.0 (enroll/progress-ratio 0 22)))
  (is (= 1.0 (enroll/progress-ratio 22 22)))
  (is (= 0.5 (enroll/progress-ratio 11 22))))

(deftest enrollment-capacity-test
  (testing "limited roster"
    (is (= {:enrolled 18
            :max 22
            :at-limit? false
            :ratio (/ 18.0 22.0)
            :label "18/22"}
           (enroll/enrollment-capacity 18 22)))
    (is (= {:enrolled 22
            :max 22
            :at-limit? true
            :ratio 1.0
            :label "22/22"}
           (enroll/enrollment-capacity 22 22))))
  (testing "unlimited"
    (is (= {:enrolled 3
            :max nil
            :at-limit? false
            :ratio nil
            :label "3"}
           (enroll/enrollment-capacity 3 nil)))))

(deftest athlete-display-helpers-test
  (testing "label with shirt"
    (is (= "10 - Mateus"
           (enroll/athlete-label {:name "Mateus" :shirt-number 10})))
    (is (= "João" (enroll/athlete-label {:name "João"}))))
  (testing "position and team base"
    (is (= "Atacante" (enroll/athlete-position {:position "Atacante"})))
    (is (= "—" (enroll/athlete-position {:position ""})))
    (let [idx (enroll/teams-index [{:_id "t1" :name "Galáticos Principal"}])]
      (is (= "Galáticos Principal"
             (enroll/athlete-team-base {:team-id "t1"} idx)))
      (is (= "Time A"
             (enroll/athlete-team-base {:team-name "Time A" :team-id "t1"} idx)))
      (is (= "—" (enroll/athlete-team-base {} idx)))))
  (testing "enrolled-display-row"
    (let [row (enroll/enrolled-display-row
               {:_id "p1" :name "Jose" :position "Volante"
                :shirt-number "07" :team-id "t1"}
               {"t1" "Galáticos B"})]
      (is (= "p1" (:id row)))
      (is (= "07 - Jose" (:label row)))
      (is (= "Volante" (:position row)))
      (is (= "Galáticos B" (:team-base row)))))
  (testing "sort"
    (is (= ["Ana" "Bruno"]
           (mapv :name (enroll/sort-enrolled-players
                        [{:name "Bruno"} {:name "Ana"}]))))))

(deftest remaining-slots-test
  (is (nil? (enroll/remaining-slots 5 nil)))
  (is (= 4 (enroll/remaining-slots 18 22)))
  (is (= 0 (enroll/remaining-slots 22 22)))
  (is (= 0 (enroll/remaining-slots 25 22))))

(deftest batch-selection-helpers-test
  (testing "label and confirm"
    (is (= "2 selecionados de 10 disponíveis"
           (enroll/batch-selection-label 2 10)))
    (is (false? (enroll/can-confirm-batch? #{})))
    (is (true? (enroll/can-confirm-batch? #{"p1"}))))
  (testing "toggle respects remaining slots"
    (is (= #{"a"} (enroll/toggle-batch-id #{} "a" 2)))
    (is (= #{"a" "b"} (enroll/toggle-batch-id #{"a"} "b" 2)))
    (is (= #{"a" "b"} (enroll/toggle-batch-id #{"a" "b"} "c" 2)))
    (is (= #{"a"} (enroll/toggle-batch-id #{"a" "b"} "b" 2)))
    (is (= #{"a" "b" "c"} (enroll/toggle-batch-id #{"a" "b"} "c" nil))))
  (testing "select allowed?"
    (is (true? (enroll/batch-select-allowed? #{"a"} "a" 1)))
    (is (false? (enroll/batch-select-allowed? #{"a"} "b" 1)))
    (is (true? (enroll/batch-select-allowed? #{} "b" nil)))))

(deftest partition-batch-results-test
  (is (= {:succeeded ["p1" "p2"]
          :failed ["p3"]
          :failed-rows [{:id "p3" :ok? false :error "409"}]}
         (enroll/partition-batch-results
          [{:id "p1" :ok? true}
           {:id "p2" :ok? true}
           {:id "p3" :ok? false :error "409"}]))))
