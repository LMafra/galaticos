(ns galaticos.domain.players-test
  (:require [clojure.test :refer [deftest is]]
            [galaticos.domain.players :as domain]))

(deftest attach-team-name-strips-stale
  (let [player {:name "P" :team-name "stale" :team-id "tid"}]
    (is (= {:name "P" :team-id "tid" :team-name "Galáticos"}
           (domain/attach-team-name player "Galáticos")))
    (is (= {:name "P" :team-id "tid"}
           (domain/attach-team-name player nil)))))

(deftest team-assignment-decision
  (is (= {:ok true} (domain/team-assignment-decision nil true)))
  (is (= :validation (get-in (domain/team-assignment-decision "tid" false) [:error :type]))))
