(ns galaticos.db.aggregations-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.db.aggregations :as agg]
            [monger.collection :as mc]))

(deftest merge-aggregated-stats-preserves-baseline
  (testing "keeps untouched championships and preserves titles while updating match-derived stats"
    (let [existing {:total {:games 12 :goals 9 :assists 5 :titles 3}
                    :by-championship [{:championship-id "c1"
                                       :championship-name "Champ 1"
                                       :games 10
                                       :goals 8
                                       :assists 4
                                       :titles 2}
                                      {:championship-id "c2"
                                       :championship-name "Champ 2"
                                       :games 2
                                       :goals 1
                                       :assists 1
                                       :titles 1}]}
          match-derived [{:championship-id "c1"
                          :championship-name "Champ 1"
                          :games 3
                          :goals 2
                          :assists 1}
                         {:championship-id "c3"
                          :championship-name "Champ 3"
                          :games 1
                          :goals 1
                          :assists 0}]
          merged (#'agg/merge-aggregated-stats existing match-derived)]
      (is (= 3 (count (:by-championship merged))))
      (is (= 3 (get-in merged [:total :titles])))
      (is (= {:championship-id "c1"
              :championship-name "Champ 1"
              :games 3
              :goals 2
              :assists 1
              :titles 2}
             (first (filter #(= "c1" (:championship-id %)) (:by-championship merged)))))
      (is (= {:championship-id "c2"
              :championship-name "Champ 2"
              :games 2
              :goals 1
              :assists 1
              :titles 1}
             (first (filter #(= "c2" (:championship-id %)) (:by-championship merged)))))
      (is (= {:championship-id "c3"
              :championship-name "Champ 3"
              :games 1
              :goals 1
              :assists 0
              :titles 0}
             (first (filter #(= "c3" (:championship-id %)) (:by-championship merged))))))))

(deftest merge-aggregated-stats-skips-match-merge-for-season-scoped-rows
  (testing "by-championship rows with :season keep table stats when match-derived arrives"
    (let [existing {:total {:games 2 :goals 2 :assists 0 :titles 1}
                    :by-championship [{:championship-id "c1"
                                      :season "2024"
                                      :championship-name "C"
                                      :games 2
                                      :goals 2
                                      :assists 0
                                      :titles 1}]}
          match-derived [{:championship-id "c1"
                          :championship-name "C"
                          :games 9
                          :goals 5
                          :assists 2}]
          merged (#'agg/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 2 (:games row)))
      (is (= 2 (:goals row)))
      (is (= "2024" (:season row))))))

(deftest championship-comparison-includes-all-championships
  (testing "returns all championships with zeroed metrics when there is no data"
    (let [championships [{:_id "c1" :name "A" :format "Pontos"}
                         {:_id "c2" :name "B" :format "Mata-mata"}]
          comparison (with-redefs-fn {#'mc/find-maps (fn [_ _ _] championships)
                                      #'agg/championship-match-metrics (fn [] {"c1" {:matches-count 2
                                                                                    :players-count 5
                                                                                    :total-goals 6
                                                                                    :total-assists 4}})
                                      #'agg/championship-season-metrics (fn [] {})
                                      #'agg/championship-player-metrics (fn [] {})}
                       #(agg/championship-comparison))]
      (is (= 2 (count comparison)))
      (is (= "c1" (:championship-id (first comparison))))
      (is (= 2 (:matches-count (first comparison))))
      (is (= "c2" (:championship-id (second comparison))))
      (is (= 0 (:matches-count (second comparison))))
      (is (= 0 (:total-goals (second comparison)))))))

(deftest championship-comparison-falls-back-to-season-metrics-without-real-matches
  (testing "uses seasons metrics for matches-count/players-count when there are no real matches"
    (let [championships [{:_id "c1" :name "A" :format "Pontos"}
                         {:_id "c2" :name "B" :format "Mata-mata"}]
          comparison (with-redefs-fn {#'mc/find-maps (fn [_ _ _] championships)
                                      #'agg/championship-match-metrics (fn [] {})
                                      #'agg/championship-season-metrics (fn [] {"c1" {:matches-count 12
                                                                                       :players-count 8}})
                                      #'agg/championship-player-metrics (fn [] {"c1" {:matches-count 99
                                                                                       :players-count 77
                                                                                       :total-goals 20
                                                                                       :total-assists 10}})}
                       #(agg/championship-comparison))
          c1 (first comparison)
          c2 (second comparison)]
      (is (= "c1" (:championship-id c1)))
      (is (= 12 (:matches-count c1)))
      (is (= 8 (:players-count c1)))
      (is (= 20 (:total-goals c1)))
      (is (= 10 (:total-assists c1)))
      (is (= "c2" (:championship-id c2)))
      (is (= 0 (:matches-count c2)))
      (is (= 0 (:players-count c2))))))
