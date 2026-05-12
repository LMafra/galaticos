(ns galaticos.util.fuzzy-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.util.fuzzy :as fuzzy]))

(deftest normalize-for-match-trims-and-strips-punctuation
  (is (= "joao silva" (fuzzy/normalize-for-match "  João — Silva!!  "))))

(deftest jaro-winkler-identical-one
  (is (= 1.0 (fuzzy/jaro-winkler-similarity "teste" "teste"))))

(deftest duplicate-report-for-players-finds-pair
  (let [ida (org.bson.types.ObjectId.)
        idb (org.bson.types.ObjectId.)
        players [{:_id ida :name "João Silva"}
                 {:_id idb :name "Joao Silva"}]
        report (fuzzy/duplicate-report-for-players players 0.75)]
    (is (= 2 (count report)))
    (is (every? #(contains? % :candidates) report))))

(deftest duplicate-report-whole-token-boost-lucas-mafra-and-mafra
  (let [ida (org.bson.types.ObjectId.)
        idb (org.bson.types.ObjectId.)
        players [{:_id ida :name "Lucas Mafra"}
                 {:_id idb :name "Mafra"}]
        report (fuzzy/duplicate-report-for-players players 0.80)]
    (is (= 2 (count report)))
    (is (<= 0.94 (get-in (vec (filter #(= (str idb) (:player-id %)) report)) [0 :candidates 0 :similarity])))))

(deftest effective-name-similarity-token-order
  (is (>= (fuzzy/effective-name-similarity "lucas mafra" "mafra lucas") 0.99)))
