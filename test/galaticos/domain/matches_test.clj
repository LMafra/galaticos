(ns galaticos.domain.matches-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.domain.matches :as domain])
  (:import [org.bson.types ObjectId]))

(deftest validate-season-for-new-match-test
  (testing "no season"
    (is (= :validation
           (get-in (domain/validate-season-for-new-match nil) [:error :type]))))
  (testing "completed season"
    (is (= :forbidden
           (get-in (domain/validate-season-for-new-match {:status "completed"})
                   [:error :type]))))
  (testing "active season"
    (is (= {:status "active"} (:ok (domain/validate-season-for-new-match {:status "active"}))))))

(deftest validate-players-enrolled-test
  (let [pid (ObjectId.)
        season {:enrolled-player-ids [pid]}]
    (is (= true (:ok (domain/validate-players-enrolled [pid] {:season season}))))
    (is (= :validation
           (get-in (domain/validate-players-enrolled [(ObjectId.)] {:season season})
                   [:error :type])))))

(deftest validate-player-team-coherence-test
  (let [pid (ObjectId.)
        tid (ObjectId.)
        player {:_id pid :name "P" :team-id tid}
        team {:_id tid :name "T" :active-player-ids [pid]}
        stats [{:player-id pid :team-id tid}]]
    (is (= true (:ok (domain/validate-player-team-coherence stats {pid player} {tid team}))))))

(deftest match-recalc-intent-test
  (let [mid (ObjectId.)
        pid (ObjectId.)
        intent (domain/match-recalc-intent {:reason :after-match-create
                                            :crud-op :create
                                            :match-id mid
                                            :player-ids [pid]})]
    (is (= :recalc-stats (:op intent)))
    (is (= [pid] (:player-ids intent)))
    (is (= [pid] (:affected-player-ids intent)))))
