(ns galaticos.db.matches-test
  (:require [clojure.test :refer [deftest is]]
            [galaticos.db.matches :as matches])
  (:import [org.bson.types ObjectId]))

(deftest normalize-player-statistics-coerces-numeric-strings
  (let [pid (ObjectId.)
        tid (ObjectId.)
        r (matches/normalize-player-statistics
           [{:player-id pid :team-id tid :goals "200" :assists "3" :minutes-played "90"}])]
    (is (= 200 (:goals (first r))))
    (is (= 3 (:assists (first r))))
    (is (= 90 (:minutes-played (first r))))))

(deftest normalize-player-statistics-idempotent-for-numbers
  (let [pid (ObjectId.)
        tid (ObjectId.)
        row {:player-id pid :team-id tid :goals 5 :assists 0}
        r (matches/normalize-player-statistics [row])]
    (is (= 5 (:goals (first r))))))

(deftest normalize-player-statistics-nilish-stats-become-zero
  (let [pid (ObjectId.)
        tid (ObjectId.)
        r (matches/normalize-player-statistics
           [{:player-id pid :team-id tid :goals nil :assists nil}])]
    (is (= 0 (:goals (first r))))
    (is (= 0 (:assists (first r))))))
