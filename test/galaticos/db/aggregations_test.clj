(ns galaticos.db.aggregations-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.core :as db]
            [galaticos.domain.analytics :as analytics]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

(deftest coerce-player-stat-numerics-is-addfields-convert
  (let [stage @#'agg/coerce-player-stat-numerics]
    (is (map? stage))
    (is (contains? stage :$addFields))
    (is (= "long" (get-in stage [:$addFields "player-statistics.goals" :$convert :to])))
    (is (= "long" (get-in stage [:$addFields "player-statistics.yellow-cards" :$convert :to])))))

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
        merged (analytics/merge-aggregated-stats existing match-derived)]
    (is (= 2 (get-in merged [:total :games])))
    (is (= 1 (get-in merged [:total :goals])))))

(deftest merge-aggregated-stats-preserves-baseline
  (testing "historical table stats add to match rollups; championships without matches keep baseline"
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
          merged (analytics/merge-aggregated-stats existing match-derived)
          c1 (first (filter #(= "c1" (:championship-id %)) (:by-championship merged)))
          c2 (first (filter #(= "c2" (:championship-id %)) (:by-championship merged)))
          c3 (first (filter #(= "c3" (:championship-id %)) (:by-championship merged)))]
      (is (= 3 (count (:by-championship merged))))
      (is (= 3 (get-in merged [:total :titles])))
      (is (= 16 (get-in merged [:total :games])))
      (is (= 12 (get-in merged [:total :goals])))
      (is (= 13 (:games c1)))
      (is (= 10 (:games (:pre-match-stats c1))))
      (is (= 2 (:games c2)))
      (is (= 1 (:games c3))))))

(deftest merge-aggregated-stats-additive-idempotent
  (testing "second merge with larger match rollup does not double-count baseline"
    (let [existing {:total {:games 10 :goals 8 :assists 4 :titles 2}
                    :by-championship [{:championship-id "c1"
                                       :championship-name "Champ 1"
                                       :games 10
                                       :goals 8
                                       :assists 4
                                       :titles 2}]}
          first-match [{:championship-id "c1"
                        :championship-name "Champ 1"
                        :games 1
                        :goals 1
                        :assists 0}]
          after-first (analytics/merge-aggregated-stats existing first-match)
          second-match [{:championship-id "c1"
                         :championship-name "Champ 1"
                         :games 2
                         :goals 3
                         :assists 1}]
          after-second (analytics/merge-aggregated-stats after-first second-match)
          row (first (:by-championship after-second))]
      (is (= 11 (:games (first (:by-championship after-first)))))
      (is (= 10 (:games (:pre-match-stats row))))
      (is (= 12 (:games row)))
      (is (= 11 (:goals row))))))

(deftest merge-aggregated-stats-preserves-total-only-baseline
  (testing "seed total without by-championship rows is kept as pre-match-total"
    (let [existing {:total {:games 50 :goals 20 :assists 5 :titles 3}
                    :by-championship []}
          match-derived [{:championship-id "c1"
                          :championship-name "C"
                          :games 1
                          :goals 2
                          :assists 0}]
          merged (analytics/merge-aggregated-stats existing match-derived)]
      (is (= {:games 50 :goals 20 :assists 5 :titles 3} (:pre-match-total merged)))
      (is (= 51 (get-in merged [:total :games])))
      (is (= 22 (get-in merged [:total :goals]))))))

(deftest merge-aggregated-stats-avoids-double-count-when-total-already-includes-matches
  (testing "total-only row whose display already embeds match rollups is not added again"
    (let [existing {:total {:games 119 :goals 145 :assists 0 :titles 0}
                    :by-championship []}
          match-derived [{:championship-id "c1"
                          :championship-name "C"
                          :games 58
                          :goals 35
                          :assists 0}]
          merged (analytics/merge-aggregated-stats existing match-derived)]
      (is (= 61 (get-in merged [:pre-match-total :games])))
      (is (= 110 (get-in merged [:pre-match-total :goals])))
      (is (= 119 (get-in merged [:total :games])))
      (is (= 145 (get-in merged [:total :goals])))
      (let [after-one-more (analytics/merge-aggregated-stats merged
                                                         [{:championship-id "c1"
                                                           :championship-name "C"
                                                           :games 59
                                                           :goals 36
                                                           :assists 0}])]
        (is (= 120 (get-in after-one-more [:total :games])))
        (is (= 146 (get-in after-one-more [:total :goals])))))))

(deftest merge-aggregated-stats-skips-imported-match-overlap-on-seeded-rows
  (testing "seeded table baseline already includes imported matches; only new match growth adds"
    (let [seeded {:total {:games 8 :goals 6 :assists 0 :titles 0}
                  :by-championship [{:championship-id "c1"
                                     :championship-name "SARRADA"
                                     :season "2025"
                                     :pre-match-stats {:games 8 :goals 6 :assists 0}
                                     :games 8
                                     :goals 6
                                     :assists 0
                                     :titles 0}]}
          imported [{:championship-id "c1"
                     :championship-name "SARRADA"
                     :season "2025"
                     :games 4
                     :goals 4
                     :assists 0}]
          after-import (analytics/merge-aggregated-stats seeded imported)
          row (first (:by-championship after-import))]
      (is (= 8 (:games row)))
      (is (= 6 (:goals row)))
      (is (= 4 (get-in row [:baseline-match-rollup :games])))
      (let [after-new-match (analytics/merge-aggregated-stats after-import
                                                          [{:championship-id "c1"
                                                            :championship-name "SARRADA"
                                                            :season "2025"
                                                            :games 5
                                                            :goals 5
                                                            :assists 0}])
            row2 (first (:by-championship after-new-match))]
        (is (= 9 (:games row2)))
        (is (= 7 (:goals row2)))))))

(deftest merge-aggregated-stats-repairs-goals-only-inflation-without-pre-match-stats
  (testing "seeded display without :pre-match-stats must not add full rollup on top (Jow 87 -> 123)"
    (let [existing {:total {:games 8 :goals 87 :assists 0 :titles 0}
                    :by-championship [{:championship-id "c1"
                                       :championship-name "SARRADA"
                                       :season "2025"
                                       :games 8
                                       :goals 87
                                       :assists 0
                                       :titles 0}]}
          match-derived [{:championship-id "c1"
                          :championship-name "SARRADA"
                          :season "2025"
                          :games 4
                          :goals 36
                          :assists 0}]
          merged (analytics/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 87 (:goals row)))
      (is (= 87 (get-in merged [:total :goals])))
      (is (= 87 (:goals (:pre-match-stats row))))
      (is (= 36 (:goals (:baseline-match-rollup row))))
      (let [after-new-match (analytics/merge-aggregated-stats merged
                                                          [{:championship-id "c1"
                                                            :championship-name "SARRADA"
                                                            :season "2025"
                                                            :games 5
                                                            :goals 37
                                                            :assists 0}])
            row2 (first (:by-championship after-new-match))]
        (is (= 88 (:goals row2)))
        (is (= 88 (get-in after-new-match [:total :goals])))))))

(deftest merge-aggregated-stats-repairs-goals-only-inflation-with-pre-match-stats
  (testing "corrupted display with same games but inflated goals is repaired on merge"
    (let [corrupted {:total {:games 8 :goals 123 :assists 0 :titles 0}
                     :by-championship [{:championship-id "c1"
                                        :championship-name "SARRADA"
                                        :season "2025"
                                        :pre-match-stats {:games 8 :goals 87 :assists 0}
                                        :games 8
                                        :goals 123
                                        :assists 0
                                        :titles 0}]}
          match-derived [{:championship-id "c1"
                          :championship-name "SARRADA"
                          :season "2025"
                          :games 4
                          :goals 36
                          :assists 0}]
          merged (analytics/merge-aggregated-stats corrupted match-derived)
          row (first (:by-championship merged))]
      (is (= 87 (:goals row)))
      (is (= 36 (:goals (:baseline-match-rollup row)))))))

(deftest merge-aggregated-stats-full-career-three-sources
  (testing "planilha sem partidas + planilha/import + só partidas antigas + partida UI nova"
    (let [existing {:total {:games 18 :goals 11 :assists 0 :titles 1}
                    :by-championship [{:championship-id "c-plan"
                                       :championship-name "Planilha"
                                       :season "2025"
                                       :pre-match-stats {:games 10 :goals 5 :assists 0}
                                       :games 10 :goals 5 :assists 0 :titles 1}
                                      {:championship-id "c-hybrid"
                                       :championship-name "Hibrido"
                                       :season "2025"
                                       :pre-match-stats {:games 8 :goals 6 :assists 0}
                                       :baseline-match-rollup {:games 4 :goals 4 :assists 0}
                                       :games 8 :goals 6 :assists 0 :titles 0}]}
          match-derived [{:championship-id "c-hybrid"
                          :championship-name "Hibrido"
                          :season "2025"
                          :games 4 :goals 4 :assists 0}
                         {:championship-id "c-old"
                          :championship-name "Historico"
                          :season "2022"
                          :games 7 :goals 11 :assists 0}
                         {:championship-id "c-plan"
                          :championship-name "Planilha"
                          :season "2026"
                          :games 1 :goals 1 :assists 0}]
          merged (analytics/merge-aggregated-stats existing match-derived)
          by (:by-championship merged)
          plan-25 (first (filter #(= "2025" (:season %)) (filter #(= "c-plan" (:championship-id %)) by)))
          hybrid (first (filter #(= "c-hybrid" (:championship-id %)) by))
          old (first (filter #(= "c-old" (:championship-id %)) by))
          ui (first (filter #(= "2026" (:season %)) (filter #(= "c-plan" (:championship-id %)) by)))]
      (is (= 5 (:goals plan-25)) "só planilha 2025")
      (is (= 6 (:goals hybrid)) "planilha + import sem delta")
      (is (= 11 (:goals old)) "só partidas 2022")
      (is (= 1 (:goals ui)) "partida UI 2026")
      (is (= 23 (get-in merged [:total :goals])) "5+6+11+1")
      (let [after-ui (analytics/merge-aggregated-stats merged
                                                   [{:championship-id "c-plan"
                                                     :championship-name "Planilha"
                                                     :season "2026"
                                                     :games 2 :goals 2 :assists 0}])]
        (is (= 24 (get-in after-ui [:total :goals])) "+1 gol na UI")))))

(deftest merge-aggregated-stats-repairs-inflated-display-from-prior-additive-merge
  (testing "corrupted display (table + full match rollup) is repaired on reconcile"
    (let [corrupted {:total {:games 12 :goals 10 :assists 0 :titles 0}
                     :by-championship [{:championship-id "c1"
                                        :championship-name "SARRADA"
                                        :season "2025"
                                        :pre-match-stats {:games 8 :goals 6 :assists 0}
                                        :games 12
                                        :goals 10
                                        :assists 0
                                        :titles 0}]}
          match-derived [{:championship-id "c1"
                          :championship-name "SARRADA"
                          :season "2025"
                          :games 4
                          :goals 4
                          :assists 0}]
          merged (analytics/merge-aggregated-stats corrupted match-derived)
          row (first (:by-championship merged))]
      (is (= 8 (:games row)))
      (is (= 6 (:goals row)))
      (is (= 4 (get-in row [:baseline-match-rollup :games]))))))

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
          merged (analytics/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 11 (:games row)))
      (is (= 7 (:goals row)))
      (is (= 2 (:assists row)))
      (is (= "2024" (:season row)))
      (is (= 11 (get-in merged [:total :games])))
      (is (= 7 (get-in merged [:total :goals]))))))

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
          merged (analytics/merge-aggregated-stats existing match-derived)
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
          merged (analytics/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 1 (count (:by-championship merged))))
      (is (= cid-str (str (:championship-id row))))
      (is (= 200 (:goals row)))
      (is (= 7 (:games row)))
      (is (= "2025" (:season row)))
      (is (= 200 (get-in merged [:total :goals]))))))

(deftest merge-aggregated-stats-fanout-adds-unscoped-into-existing-scoped-rollup
  (testing "when scoped and unscoped rollups coexist, unscoped games merge into the season row"
    (let [existing {:total {:games 8 :goals 6 :assists 0 :titles 2}
                    :by-championship [{:championship-id "c1"
                                       :season "2025"
                                       :championship-name "SARRADA"
                                       :pre-match-stats {:games 8 :goals 6 :assists 0}
                                       :baseline-match-rollup {:games 4 :goals 4 :assists 0}
                                       :games 8
                                       :goals 6
                                       :assists 0
                                       :titles 2}]}
          match-derived [{:championship-id "c1"
                          :season "2025"
                          :championship-name "SARRADA"
                          :games 4
                          :goals 4
                          :assists 0}
                         {:championship-id "c1"
                          :season nil
                          :championship-name "SARRADA"
                          :games 1
                          :goals 1
                          :assists 0}]
          merged (analytics/merge-aggregated-stats existing match-derived)
          row (first (:by-championship merged))]
      (is (= 9 (:games row)))
      (is (= 7 (:goals row)))
      (is (= 9 (get-in merged [:total :games])))
      (is (= 7 (get-in merged [:total :goals]))))))

(deftest merge-aggregated-stats-ambiguous-unscoped-adds-orphan-row
  (testing "unscoped match rollup adds orphan row; season-scoped rows keep baseline"
    (let [existing {:total {:games 2 :goals 0 :assists 0 :titles 0}
                    :by-championship [{:championship-id "c1" :season "2024"
                                       :championship-name "C" :games 1 :goals 0 :assists 0 :titles 0}
                                      {:championship-id "c1" :season "2025"
                                       :championship-name "C" :games 1 :goals 0 :assists 0 :titles 0}]}
          match-derived [{:championship-id "c1" :season nil
                          :championship-name "C" :games 3 :goals 2 :assists 0}]
          merged (analytics/merge-aggregated-stats existing match-derived)
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
          merged (analytics/merge-aggregated-stats existing match-derived)
          by (:by-championship merged)
          r24 (first (filter #(= "2024" (:season %)) by))
          r25 (first (filter #(= "2025" (:season %)) by))]
      (is (= 2 (count by)))
      (is (= 5 (:games r24)))
      (is (= 3 (:goals r24)))
      (is (= 3 (:games r25)))
      (is (= 3 (:goals r25)))
      (is (= 8 (get-in merged [:total :games])))
      (is (= 6 (get-in merged [:total :goals]))))))

(deftest merge-aggregated-stats-keeps-table-cache-when-drop-stale-disabled
  (testing "default merge preserves championships absent from match rollup (same as drop-stale false)"
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
          merged (analytics/merge-aggregated-stats existing match-derived)
          c2 (first (filter #(= "c2" (:championship-id %)) (:by-championship merged)))]
      (is (= 2 (:games c2)))
      (is (= 1 (:goals c2))))))

(deftest combine-players-aggregated-stats-sums-disjoint-championships
  (let [p1 {:aggregated-stats {:total {:games 7 :goals 1 :assists 0 :titles 0}
                              :by-championship [{:championship-id "a"
                                                :championship-name "ASTCU"
                                                :season "2025"
                                                :games 7 :goals 1 :assists 0 :titles 0}]}}
        p2 {:aggregated-stats {:total {:games 11 :goals 0 :assists 0 :titles 2}
                              :by-championship [{:championship-id "b"
                                                :championship-name "SARRADA"
                                                :season "2025"
                                                :games 6 :goals 0 :assists 0 :titles 1}
                                                {:championship-id "c"
                                                 :championship-name "Fut7"
                                                 :season "2025"
                                                 :games 5 :goals 0 :assists 0 :titles 0}
                                                {:championship-id "d"
                                                 :championship-name "Boleiro"
                                                 :season "2025"
                                                 :games 0 :goals 0 :assists 0 :titles 1}]}}
        out (agg/combine-players-aggregated-stats [p1 p2])]
    (is (= 18 (get-in out [:total :games])))
    (is (= 1 (get-in out [:total :goals])))
    (is (= 2 (get-in out [:total :titles])))
    (is (= 4 (count (:by-championship out))))))

(deftest combine-players-aggregated-stats-sums-same-championship-row
  (let [p1 {:aggregated-stats {:by-championship [{:championship-id "x"
                                                  :season "2025"
                                                  :championship-name "C"
                                                  :games 3 :goals 2 :assists 1 :titles 0}]}}
        p2 {:aggregated-stats {:by-championship [{:championship-id "x"
                                                  :season "2025"
                                                  :championship-name "C"
                                                  :games 4 :goals 0 :assists 0 :titles 1}]}}
        out (agg/combine-players-aggregated-stats [p1 p2])
        row (first (:by-championship out))]
    (is (= 1 (count (:by-championship out))))
    (is (= 7 (:games row)))
    (is (= 2 (:goals row)))
    (is (= 1 (:assists row)))
    (is (= 1 (:titles row)))
    (is (= 7 (get-in row [:pre-match-stats :games])))
    (is (= 2 (get-in row [:pre-match-stats :goals])))))

(deftest combine-players-aggregated-stats-preserves-pre-match-stats
  (testing "Lucas+Mafra style merge keeps hybrid metadata per championship row"
    (let [astcu (ObjectId.)
          sarrada (ObjectId.)
          fut7 (ObjectId.)
          boleiro (ObjectId.)
          p1 {:aggregated-stats {:total {:games 7 :goals 1 :assists 0 :titles 0}
                                :by-championship [{:championship-id astcu
                                                   :championship-name "ASTCU"
                                                   :season "2025"
                                                   :pre-match-stats {:games 7 :goals 1 :assists 0}
                                                   :games 7 :goals 1 :assists 0 :titles 0}]}}
          p2 {:aggregated-stats {:total {:games 11 :goals 0 :assists 0 :titles 2}
                                :by-championship [{:championship-id sarrada
                                                   :championship-name "SARRADA"
                                                   :season "2025"
                                                   :pre-match-stats {:games 6 :goals 0 :assists 0}
                                                   :games 6 :goals 0 :assists 0 :titles 1}
                                                  {:championship-id fut7
                                                   :championship-name "Boleiro fut7"
                                                   :season "2025"
                                                   :pre-match-stats {:games 5 :goals 0 :assists 0}
                                                   :games 5 :goals 0 :assists 0 :titles 0}
                                                  {:championship-id boleiro
                                                   :championship-name "Boleiro"
                                                   :season "2025"
                                                   :pre-match-stats {:games 0 :goals 0 :assists 0}
                                                   :games 0 :goals 0 :assists 0 :titles 1}]}}
          out (agg/combine-players-aggregated-stats [p1 p2])
          astcu-row (first (filter #(= (str astcu) (str (:championship-id %))) (:by-championship out)))]
      (is (= 18 (get-in out [:total :games])))
      (is (= 7 (get-in astcu-row [:pre-match-stats :games])))
      (is (= 1 (get-in astcu-row [:pre-match-stats :goals]))))))

(deftest combine-then-player-reconcile-opts-preserves-cache
  (testing "post-merge combined cache (no matches) is not cleared with reconcile-safe opts"
    (let [astcu (ObjectId.)
          sarrada (ObjectId.)
          p1 {:aggregated-stats {:by-championship [{:championship-id astcu :season "2025"
                                                   :championship-name "ASTCU"
                                                   :pre-match-stats {:games 7 :goals 1 :assists 0}
                                                   :games 7 :goals 1 :assists 0 :titles 0}]}}
          p2 {:aggregated-stats {:by-championship [{:championship-id sarrada :season "2025"
                                                   :championship-name "SARRADA"
                                                   :pre-match-stats {:games 6 :goals 0 :assists 0}
                                                   :games 6 :goals 0 :assists 0 :titles 1}]}}
          combined (agg/combine-players-aggregated-stats [p1 p2])
          pid (ObjectId.)
          player {:_id pid :name "Lucas Mafra" :aggregated-stats combined}
          stored (atom player)
          reconcile-safe {:zero-if-no-matches? false
                          :drop-stale-without-match-rollups? false}
          run-reconcile! (fn [opts]
                           (with-redefs [db/db (constantly :mock-db)
                                         mc/aggregate (fn [_ _ _] [])
                                         mc/find-one-as-map (fn [_ _ _ q]
                                                              (when (= pid (:_id q))
                                                                @stored))
                                         mc/update (fn [_ _ q patch]
                                                     (when (= pid (:_id q))
                                                       (swap! stored merge (:$set patch))))]
                             (agg/update-incremental-player-stats! [(str pid)] opts)))]
      (is (= 13 (get-in combined [:total :games])))
      (run-reconcile! reconcile-safe)
      (is (= 13 (get-in @stored [:aggregated-stats :total :games])))
      (is (= 2 (count (:by-championship (:aggregated-stats @stored))))))))

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

(deftest update-aggregated-stats-pipeline-vec-prefilter
  (testing "full recompute: no match prefilter on collection"
    (let [v (#'agg/update-aggregated-stats-pipeline-vec [])]
      (is (contains? (first v) :$unwind))))
  (testing "incremental: $match on matches first"
    (let [a (ObjectId.) b (ObjectId.)
          v (#'agg/update-aggregated-stats-pipeline-vec [a b])]
      (is (contains? (first v) :$match)))))
