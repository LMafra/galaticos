(ns galaticos.domain.analytics-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.domain.analytics :as analytics])
  (:import [org.bson.types ObjectId]))

(deftest goal-contribution-test
  (is (= 5 (analytics/goal-contribution {:goals 3 :assists 2})))
  (is (= 0 (analytics/goal-contribution {}))))

(deftest goal-contribution-per-game-test
  (is (= 2.0 (analytics/goal-contribution-per-game {:goals 2 :assists 4 :games 3})))
  (is (= 0 (analytics/goal-contribution-per-game {:goals 1 :games 0}))))

(deftest discipline-index-test
  (is (= 1.0 (analytics/discipline-index {:yellow-cards 2 :red-cards 1 :games 5})))
  (is (= 0 (analytics/discipline-index {:yellow-cards 1 :games 0}))))

(deftest minutes-per-goal-test
  (testing "returns nil when minutes quality insufficient"
    (is (nil? (analytics/minutes-per-goal {:goals 2 :minutes-played 0 :games 2}))))
  (testing "returns ratio when quality ok"
    (is (= 45.0 (analytics/minutes-per-goal {:goals 2 :minutes-played 90 :games 2})))))

(deftest summarize-player-stats-test
  (let [stats [{:goals 2 :assists 1 :yellow-cards 1 :red-cards 0 :minutes-played 90}
               {:goals 0 :assists 1 :yellow-cards 0 :red-cards 1 :minutes-played 45}]
        out (analytics/summarize-player-stats stats)]
    (is (= 2 (:games out)))
    (is (= 2 (:goals out)))
    (is (= 2 (:assists out)))
    (is (= 1 (:yellow-cards out)))
    (is (= 1 (:red-cards out)))
    (is (= 135 (:minutes-played out)))
    (is (= 4 (:goal-contribution out)))
    (is (= 2.0 (:goal-contribution-per-game out)))
    (is (= 2.0 (:discipline-index out)))))

(deftest recompute-invariant-from-fixtures
  (testing "(rollup matches + merge existing) preserves match totals for cards/minutes"
    (let [pid (ObjectId.)
          cid "c1"
          existing {:total {:games 5 :goals 3 :assists 1 :titles 0}
                    :by-championship [{:championship-id cid
                                       :championship-name "C"
                                       :games 5 :goals 3 :assists 1 :titles 0}]}
          matches [{:championship-id cid
                    :championship-name "C"
                    :player-statistics [{:player-id pid :goals 2 :assists 0
                                         :yellow-cards 1 :minutes-played 90}]}
                   {:championship-id cid
                    :championship-name "C"
                    :player-statistics [{:player-id pid :goals 1 :assists 1
                                         :yellow-cards 0 :red-cards 1 :minutes-played 45}]}]
          match-derived (analytics/rollup-match-derived-for-player matches pid)
          merged (analytics/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 1 (count match-derived)))
      (is (= 7 (:games row)))
      (is (= 6 (:goals row)))
      (is (= 2 (:assists row)))
      (is (= 1 (:yellow-cards row)))
      (is (= 1 (:red-cards row)))
      (is (= 135 (:minutes-played row)))
      (is (= (/ 4.0 7) (analytics/discipline-index row)))
      (is (= 8 (analytics/goal-contribution row))))))
