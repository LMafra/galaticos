(ns galaticos.export.csv-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.export.csv :as export-csv]
            [galaticos.db.players :as players-db]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.matches :as matches-db])
  (:import [org.bson.types ObjectId]))

(deftest dashboard-csv-has-reference-headers
  (testing "builds dashboard csv with top 20 sections"
    (let [csv (with-redefs [players-db/find-active (fn []
                                                               [{:_id (ObjectId.)
                                                                 :name "Alice"
                                                                 :aggregated-stats {:total {:games 10 :goals 4 :assists 2 :titles 1}}}])
                            agg/top-players-by-metric
                            (fn [metric _limit & _]
                              [{:_id (ObjectId.)
                                :name (name metric)
                                :aggregated-stats {:total {:games 1 :goals 2 :assists 3 :titles 4}}}])]
                (export-csv/dashboard-csv))]
      (is (str/includes? csv "ATLETA,JOGOS,GOLS,ASSISTENCIAS,TÍTULOS"))
      (is (str/includes? csv "Top 20 Jogos"))
      (is (str/includes? csv "Top 20 Gols")))))

(deftest championship-csv-has-player-and-match-columns
  (testing "builds championship csv with left and right blocks"
    (let [championship-id (str (ObjectId.))
          winner-oid (ObjectId.)
          csv (with-redefs [championships-db/find-by-id
                            (fn [_]
                              {:_id (ObjectId.)
                               :name "Euro"
                               :enrolled-player-ids [winner-oid]
                               :winner-player-ids [winner-oid]
                               :titles-award-count 1})
                            players-db/find-by-ids
                            (fn [_] [{:_id winner-oid :name "Winner"}])
                            agg/player-stats-by-championship
                            (fn [_] [{:_id winner-oid :player-name "Winner" :games 3 :goals 2 :assists 1}])
                            matches-db/find-by-championship
                            (fn [_]
                              [{:round "SEMIFINAL" :opponent "Rivals" :result {:our-score 3 :opponent-score 1}}])]
                (export-csv/championship-csv championship-id))]
      (is (str/includes? csv "Atletas,Jogos,Gols,Assistencias,Títulos"))
      (is (str/includes? csv "Winner,3,2,1,1"))
      (is (str/includes? csv "GALÁTICOS,3 x 1,Rivals")))))
