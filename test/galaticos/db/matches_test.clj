(ns galaticos.db.matches-test
  (:require [clojure.test :refer [deftest is]]
            [galaticos.db.matches :as matches])
  (:import [java.time Instant LocalDate LocalTime ZoneOffset]
           [java.util Date]
           [org.bson.types ObjectId]))

(deftest coerce-match-date-parses-calendar-date-at-noon-utc
  (let [d (matches/coerce-match-date "2024-06-15")]
    (is (instance? Date d))
    (is (= (Instant/parse "2024-06-15T12:00:00Z")
           (.toInstant ^Date d)))))

(deftest coerce-match-date-is-idempotent-on-date
  (let [d (Date/from (Instant/parse "2025-01-01T12:00:00Z"))]
    (is (= d (matches/coerce-match-date d)))))

(deftest coerce-match-date-parses-iso-instant
  (let [d (matches/coerce-match-date "2024-01-01T00:00:00Z")]
    (is (= (Instant/parse "2024-01-01T00:00:00Z")
           (.toInstant ^Date d)))))

(deftest coerce-match-date-nil-and-blank
  (is (nil? (matches/coerce-match-date nil)))
  (is (nil? (matches/coerce-match-date "  "))))

(deftest sort-matches-by-date-desc-handles-mixed-types
  (let [older (Date/from (.toInstant (.atTime (LocalDate/parse "2023-06-01")
                                                (LocalTime/of 12 0))
                                      (ZoneOffset/UTC)))
        newer-str "2024-06-15"
        matches [{:date older :_id 1} {:date newer-str :_id 2}]
        sorted (matches/sort-matches-by-date-desc matches)]
    (is (= [2 1] (map :_id sorted)))))

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
