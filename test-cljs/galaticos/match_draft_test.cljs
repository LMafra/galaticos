(ns galaticos.match-draft-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [galaticos.match-draft :as draft]))

(deftest save-and-load-roundtrip
  (testing "opponent survives localStorage roundtrip"
    (let [route-id "new-test-champ"
          form {:championship-id "abc"
                :home-team-id "team-1"
                :date "2026-06-05"
                :opponent "UX Draft roundtrip"
                :venue ""
                :away-score 0
                :player-statistics {}}]
      (draft/clear-draft! route-id)
      (draft/save-draft! route-id form)
      (let [loaded (draft/load-draft route-id)]
        (is (some? loaded) "draft written to localStorage")
        (is (= "UX Draft roundtrip" (get-in loaded [:form-data :opponent]))))
      (draft/clear-draft! route-id))))
