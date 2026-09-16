(ns galaticos.match-stat-bounds-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [galaticos.match-stat-bounds :as bounds]))

(deftest bounds-for-test
  (is (= {:min-val 0 :max-val 120} (bounds/bounds-for :minutes-played)))
  (is (= {:min-val 0 :max-val 1} (bounds/bounds-for :red-cards)))
  (is (= {:min-val 0 :max-val 2} (bounds/bounds-for :yellow-cards)))
  (is (= {:min-val 0 :max-val 20} (bounds/bounds-for :goals)))
  (is (nil? (bounds/bounds-for :played?))))

(deftest clamp-stat-value-test
  (testing "minutes"
    (is (= 0 (bounds/clamp-stat-value :minutes-played -5)))
    (is (= 90 (bounds/clamp-stat-value :minutes-played 90)))
    (is (= 120 (bounds/clamp-stat-value :minutes-played 999))))
  (testing "red cards"
    (is (= 1 (bounds/clamp-stat-value :red-cards 3)))
    (is (= 0 (bounds/clamp-stat-value :red-cards -1))))
  (testing "goals"
    (is (= 20 (bounds/clamp-stat-value :goals 50)))
    (is (= 0 (bounds/clamp-stat-value :goals nil)))))
