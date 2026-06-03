(ns galaticos.analytics.predictive-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.analytics.predictive :as predictive]
            [galaticos.analytics.readiness :as readiness]))

(def sample-evolution
  [{:_id {:year 2023 :month 1 :week 1} :games 2 :goals 0 :assists 1}
   {:_id {:year 2023 :month 2 :week 5} :games 2 :goals 1 :assists 0}
   {:_id {:year 2024 :month 1 :week 2} :games 2 :goals 2 :assists 2}
   {:_id {:year 2024 :month 2 :week 6} :games 2 :goals 3 :assists 3}])

(deftest readiness-ok-test
  (let [result (readiness/evaluate {:total {:games 10}
                                    :evolution sample-evolution}
                                   {:require-reconciliation? false})]
    (is (:ok result))
    (is (pos? (count (readiness/disclaimers-for result))))))

(deftest readiness-fails-reconciliation-test
  (let [result (readiness/evaluate {:total {:games 10}
                                    :evolution sample-evolution
                                    :job-meta nil}
                                   {:require-reconciliation? true})]
    (is (not (:ok result)))
    (is (= :reconciliation-pending (:reason result)))))

(deftest readiness-fails-insufficient-games-test
  (let [result (readiness/evaluate {:total {:games 2}
                                    :evolution sample-evolution})]
    (is (not (:ok result)))
    (is (= :insufficient-games (:reason result)))))

(deftest trend-up-test
  (let [trend (predictive/compute-trend sample-evolution)]
    (is (= :up (:direction trend)))
    (is (pos? (:delta trend)))))

(deftest risk-and-projection-test
  (let [trend (predictive/compute-trend sample-evolution)
        total {:games 8 :goals 6 :assists 6 :yellow-cards 2 :red-cards 0}
        risk (predictive/compute-risk trend total)
        projection (predictive/compute-projection sample-evolution)]
    (is (#{:low :medium :high} (:level risk)))
    (is (number? (:projected-goal-contribution projection)))))

(deftest predictive-omitted-when-not-ready
  (testing "readiness false → no trend numbers in consumer contract"
    (let [bad (readiness/evaluate {:total {:games 1} :evolution []})]
      (is (not (:ok bad)))
      (is (some? (readiness/readiness-disclaimer bad))))))
