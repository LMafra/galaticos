(ns galaticos.tasks.normalize-match-player-stats-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.tasks.normalize-match-player-stats :as task]
            [galaticos.db.core :as db]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.matches :as matches]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

(defn- mock-success-conn [] {:status :connected :db-name "galaticos"})

(deftest main-no-matches
  (testing "success path with zero match documents"
    (let [agg-called? (atom false)
          disconnected? (atom false)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [])
                    agg/update-all-player-stats (fn [] (reset! agg-called? true) {:status :success :updated 0})
                    db/disconnect! (fn [] (reset! disconnected? true) {:status :disconnected})]
        (task/-main)
        (is @agg-called? "update-all-player-stats must be called even with no matches")
        (is @disconnected? "disconnect! must be called in finally")))))

(deftest main-matches-already-normalized
  (testing "matches whose player-statistics are unchanged are not updated"
    (let [update-calls (atom 0)
          ps [{:player-id (ObjectId.) :goals 1 :assists 0}]]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id (ObjectId.) :player-statistics ps}])
                    matches/normalize-player-statistics (fn [s] s)
                    matches/update-by-id (fn [& _] (swap! update-calls inc))
                    agg/update-all-player-stats (fn [] {:status :success :updated 0})
                    db/disconnect! (fn [] nil)]
        (task/-main)
        (is (= 0 @update-calls) "no update when stats already normalized")))))

(deftest main-matches-needing-normalization
  (testing "matches with changed player-statistics trigger update-by-id"
    (let [update-calls (atom 0)
          mid (ObjectId.)
          ps-old [{:player-id (ObjectId.) :goals "2" :assists "1"}]
          ps-new [{:player-id (ObjectId.) :goals 2 :assists 1}]]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id mid :player-statistics ps-old}
                                             {:_id (ObjectId.) :player-statistics ps-old}])
                    matches/normalize-player-statistics (fn [_] ps-new)
                    matches/update-by-id (fn [& _] (swap! update-calls inc))
                    agg/update-all-player-stats (fn [] {:status :success :updated 2})
                    db/disconnect! (fn [] nil)]
        (task/-main)
        (is (= 2 @update-calls) "update called once per changed match")))))

(deftest main-match-without-player-statistics
  (testing "match with nil :player-statistics is skipped gracefully"
    (let [update-calls (atom 0)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id (ObjectId.)}])
                    matches/update-by-id (fn [& _] (swap! update-calls inc))
                    agg/update-all-player-stats (fn [] {:status :success :updated 0})
                    db/disconnect! (fn [] nil)]
        (task/-main)
        (is (= 0 @update-calls) "no update when :player-statistics absent")))))

(deftest main-mixed-matches
  (testing "only changed matches are updated; unchanged and nil-stats skipped"
    (let [update-calls (atom 0)
          ps-same [{:player-id (ObjectId.) :goals 1}]
          ps-old  [{:player-id (ObjectId.) :goals "3"}]
          ps-new  [{:player-id (ObjectId.) :goals 3}]]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [{:_id (ObjectId.) :player-statistics ps-same}
                                             {:_id (ObjectId.)}
                                             {:_id (ObjectId.) :player-statistics ps-old}])
                    matches/normalize-player-statistics (fn [s]
                                                          (if (= s ps-same) ps-same ps-new))
                    matches/update-by-id (fn [& _] (swap! update-calls inc))
                    agg/update-all-player-stats (fn [] {:status :success :updated 1})
                    db/disconnect! (fn [] nil)]
        (task/-main)
        (is (= 1 @update-calls) "only the changed match triggers update")))))

(deftest main-disconnect-called-even-on-agg-exception
  (testing "disconnect! runs in finally even when agg throws"
    (let [disconnected? (atom false)]
      (with-redefs [db/connect! mock-success-conn
                    db/db (fn [] :mock-db)
                    mc/find-maps (fn [& _] [])
                    agg/update-all-player-stats (fn [] (throw (Exception. "agg fail")))
                    db/disconnect! (fn [] (reset! disconnected? true) {:status :disconnected})]
        (try (task/-main) (catch Exception _))
        (is @disconnected? "disconnect! must fire even when exception occurs")))))
