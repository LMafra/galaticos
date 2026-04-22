(ns galaticos.db.aggregations-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.db.aggregations :as agg]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

(deftest coerce-player-stat-goals-assists-is-addfields-convert
  (let [stage @#'agg/coerce-player-stat-goals-assists]
    (is (map? stage))
    (is (contains? stage :$addFields))
    (is (= "long" (get-in stage [:$addFields "player-statistics.goals" :$convert :to])))))

(deftest match-player-stats-filter-treats-objectid-and-string-as-same
  (let [oid (ObjectId.)
        match-set (#'agg/match-player-id-str-set [oid])]
    (is (#'agg/stats-row-for-match-players? {:player-id oid} match-set))
    (is (#'agg/stats-row-for-match-players? {:player-id (str oid)} match-set))
    (is (not (#'agg/stats-row-for-match-players? {:player-id (ObjectId.)} match-set)))))

(deftest merge-aggregated-stats-unifies-championship-id-types
  (let [cid (ObjectId.)
        existing {:total {:games 0 :goals 0 :assists 0 :titles 0}
                  :by-championship [{:championship-id cid
                                     :championship-name "C"
                                     :games 0 :goals 0 :assists 0 :titles 0}]}
        match-derived [{:championship-id (str cid)
                        :championship-name "C"
                        :games 2 :goals 1 :assists 0}]
        merged (#'agg/merge-aggregated-stats existing match-derived)]
    (is (= 2 (get-in merged [:total :games])))
    (is (= 1 (get-in merged [:total :goals])))))

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

(deftest merge-aggregated-stats-merges-match-into-season-scoped-rows
  (testing "by-championship rows with :season absorb match rollups keyed by same championship+season"
    (let [existing {:total {:games 2 :goals 2 :assists 0 :titles 1}
                    :by-championship [{:championship-id "c1"
                                       :season "2024"
                                       :championship-name "C"
                                       :games 2
                                       :goals 2
                                       :assists 0
                                       :titles 1}]}
          match-derived [{:championship-id "c1"
                          :season "2024"
                          :championship-name "C"
                          :games 9
                          :goals 5
                          :assists 2}]
          merged (#'agg/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 9 (:games row)))
      (is (= 5 (:goals row)))
      (is (= 2 (:assists row)))
      (is (= "2024" (:season row)))
      (is (= 9 (get-in merged [:total :games])))
      (is (= 5 (get-in merged [:total :goals]))))))

(deftest merge-aggregated-stats-fans-out-unscoped-to-sole-season-row
  (testing "nil-season match rollup merges into the only by-championship row for that championship"
    (let [existing {:total {:games 0 :goals 0 :assists 0 :titles 1}
                    :by-championship [{:championship-id "c-bol"
                                       :season "2025"
                                       :championship-name "Boleiro"
                                       :games 0
                                       :goals 0
                                       :assists 0
                                       :titles 1}]}
          match-derived [{:championship-id "c-bol"
                          :season nil
                          :championship-name "Boleiro"
                          :games 1
                          :goals 0
                          :assists 0}]
          merged (#'agg/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 1 (count (:by-championship merged))))
      (is (= 1 (:games row)))
      (is (= "2025" (:season row)))
      (is (= 1 (get-in merged [:total :games]))))))

(deftest merge-aggregated-stats-fanout-unifies-objectid-and-string-championship-id
  (testing "Mongo rollup uses ObjectId championship-id; player row uses string — fan-out still merges"
    (let [cid (ObjectId.)
          cid-str (str cid)
          existing {:total {:games 6 :goals 0 :assists 0 :titles 1}
                    :by-championship [{:championship-id cid-str
                                       :season "2025"
                                       :championship-name "SARRADA"
                                       :games 6
                                       :goals 0
                                       :assists 0
                                       :titles 1}]}
          match-derived [{:championship-id cid
                          :season nil
                          :championship-name "SARRADA"
                          :games 1
                          :goals 200
                          :assists 0}]
          merged (#'agg/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 1 (count (:by-championship merged))))
      (is (= cid-str (str (:championship-id row))))
      (is (= 200 (:goals row)))
      (is (= "2025" (:season row)))
      (is (= 200 (get-in merged [:total :goals]))))))

(deftest merge-aggregated-stats-ambiguous-unscoped-adds-orphan-row
  (testing "multiple season rows for same championship: unscoped rollup does not pick a season"
    (let [existing {:total {:games 2 :goals 0 :assists 0 :titles 0}
                    :by-championship [{:championship-id "c1" :season "2024"
                                       :championship-name "C" :games 1 :goals 0 :assists 0 :titles 0}
                                      {:championship-id "c1" :season "2025"
                                       :championship-name "C" :games 1 :goals 0 :assists 0 :titles 0}]}
          match-derived [{:championship-id "c1" :season nil
                          :championship-name "C" :games 3 :goals 2 :assists 0}]
          merged (#'agg/merge-aggregated-stats existing match-derived)
          by (:by-championship merged)]
      (is (= 3 (count by)))
      (is (= 5 (get-in merged [:total :games])))
      (is (= 2 (get-in merged [:total :goals]))))))

(deftest merge-aggregated-stats-distinguishes-seasons-same-championship
  (testing "same championship-id different :season merge to separate rows (no double-count)"
    (let [existing {:total {:games 4 :goals 3 :assists 1 :titles 0}
                    :by-championship [{:championship-id "c1" :season "2024"
                                       :championship-name "C" :games 2 :goals 1 :assists 1 :titles 0}
                                      {:championship-id "c1" :season "2025"
                                       :championship-name "C" :games 2 :goals 2 :assists 0 :titles 0}]}
          match-derived [{:championship-id "c1" :season "2024"
                          :championship-name "C" :games 3 :goals 2 :assists 0}
                         {:championship-id "c1" :season "2025"
                          :championship-name "C" :games 1 :goals 1 :assists 0}]
          merged (#'agg/merge-aggregated-stats existing match-derived)
          by (:by-championship merged)
          r24 (first (filter #(= "2024" (:season %)) by))
          r25 (first (filter #(= "2025" (:season %)) by))]
      (is (= 2 (count by)))
      (is (= 3 (:games r24)))
      (is (= 2 (:goals r24)))
      (is (= 1 (:games r25)))
      (is (= 1 (:goals r25)))
      (is (= 4 (get-in merged [:total :games])))
      (is (= 3 (get-in merged [:total :goals]))))))

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
