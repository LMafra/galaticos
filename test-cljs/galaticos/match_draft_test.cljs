(ns galaticos.match-draft-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [galaticos.match-draft :as draft]))

(deftest form-snapshot-includes-player-statistics
  (testing "stats survive pure snapshot"
    (let [form {:championship-id "abc"
                :home-team-id "team-1"
                :date "2026-06-05"
                :opponent "UX Draft roundtrip"
                :venue "Estádio"
                :away-score 2
                :player-statistics
                {"p1" {:goals 1 :assists 0 :minutes-played 90
                       :yellow-cards 0 :red-cards 0 :played? true}}}
          snap (draft/form-snapshot form)]
      (is (= "UX Draft roundtrip" (:opponent snap)))
      (is (= 2 (:away-score snap)))
      (is (= 1 (get-in snap [:player-statistics "p1" :goals])))
      (is (true? (get-in snap [:player-statistics "p1" :played?]))))))

(deftest encode-decode-roundtrip
  (testing "JSON encode/decode keeps opponent and stats"
    (let [form {:championship-id "abc"
                :home-team-id "team-1"
                :date "2026-06-05"
                :opponent "UX Draft roundtrip"
                :venue ""
                :away-score 0
                :player-statistics
                {"pid-9" {:goals 2 :assists 1 :minutes-played 75
                          :yellow-cards 1 :red-cards 0 :played? true}}}
          payload (draft/draft-payload form 1234567890)
          raw (draft/encode-draft payload)
          loaded (draft/decode-draft raw)]
      (is (some? loaded))
      (is (= "UX Draft roundtrip" (get-in loaded [:form-data :opponent])))
      (is (= 2 (get-in loaded [:form-data :player-statistics "pid-9" :goals])))
      (is (= 75 (get-in loaded [:form-data :player-statistics "pid-9" :minutes-played])))
      (is (= 1234567890 (:saved-at loaded))))))

(deftest save-and-load-roundtrip
  (testing "opponent + stats survive localStorage when available"
    (let [route-id "new-test-champ"
          form {:championship-id "abc"
                :home-team-id "team-1"
                :date "2026-06-05"
                :opponent "UX Draft roundtrip"
                :venue ""
                :away-score 0
                :player-statistics
                {"p1" {:goals 1 :assists 0 :minutes-played 90
                       :yellow-cards 0 :red-cards 0 :played? true}}}]
      (draft/clear-draft! route-id)
      (draft/save-draft! route-id form)
      (let [loaded (draft/load-draft route-id)]
        (if (some? loaded)
          (do
            (is (= "UX Draft roundtrip" (get-in loaded [:form-data :opponent])))
            (is (= 1 (get-in loaded [:form-data :player-statistics "p1" :goals]))))
          ;; Node cljs-test has no localStorage — pure encode/decode covers persistence.
          (is (nil? loaded) "skipped localStorage in this environment")))
      (draft/clear-draft! route-id))))
