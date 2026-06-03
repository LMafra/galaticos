(ns galaticos.analytics.derived-metrics-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.analytics.derived-metrics :as derived]))

(deftest enrich-stats-test
  (let [out (derived/enrich-stats {:goals 2 :assists 1 :games 2 :yellow-cards 1})]
    (is (= 3 (:goal-contribution out)))
    (is (= 1.5 (:goal-contribution-per-game out)))
    (is (= 0.5 (:discipline-index out)))))

(deftest derived-metric-test
  (is (derived/derived-metric? :goal-contribution))
  (is (not (derived/derived-metric? :goals))))

(deftest attach-player-derived-test
  (let [player {:name "P"
                :aggregated-stats {:total {:goals 1 :assists 2 :games 1}}}
        out (derived/attach-player-derived player)]
    (is (= 3 (get-in out [:derived :goal-contribution])))
    (is (nil? (get-in player [:derived :goal-contribution])))))

(deftest metric-value-test
  (let [player (derived/attach-player-derived
                {:aggregated-stats {:total {:goals 2 :assists 2 :games 2}}})]
    (is (= 4 (derived/metric-value player :goal-contribution)))))
