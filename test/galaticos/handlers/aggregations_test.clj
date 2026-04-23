(ns galaticos.handlers.aggregations-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.aggregations :as handlers]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.core :as db]
            [galaticos.analytics.player-stats-jobs :as pjobs]
            [galaticos.analytics.player-stats-job-store :as job-store]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(deftest player-stats-by-championship
  (testing "success with championship-id"
    (let [champ-id (str (ObjectId.))
          request {:params {:championship-id champ-id}}
          result (with-redefs [agg/player-stats-by-championship (fn [_] [{:player "P1" :goals 5}])]
                  (handlers/player-stats-by-championship request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (vector? (:data body)))
      (is (= 1 (count (:data body))))))
  (testing "error when championship-id missing"
    (let [request {:params {}}
          result (handlers/player-stats-by-championship request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (= "Championship ID required" (:error body))))))

(deftest avg-goals-by-position
  (testing "success with championship-id"
    (let [champ-id (str (ObjectId.))
          request {:params {:championship-id champ-id}}
          result (with-redefs [agg/avg-goals-by-position (fn [_] [{:position "FW" :avg 2.5}])]
                  (handlers/avg-goals-by-position request))]
      (is (= 200 (:status result)))))
  (testing "error when championship-id missing"
    (let [request {:params {}}
          result (handlers/avg-goals-by-position request)]
      (is (= 400 (:status result))))))

(deftest championship-tab-stats-handler
  (testing "success bundles player + position stats"
    (let [champ-id (str (ObjectId.))
          request {:params {:championship-id champ-id}}
          result (with-redefs [agg/player-stats-by-championship (fn [_] [{:player-name "A"}])
                              agg/avg-goals-by-position (fn [_] [{:position "GK"}])]
                  (handlers/championship-tab-stats request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "A" (get-in body [:data :player-stats 0 :player-name])))
      (is (= "GK" (get-in body [:data :position-stats 0 :position])))))
  (testing "missing championship-id"
    (let [result (handlers/championship-tab-stats {:params {}})
          body (parse-body result)]
      (is (= 400 (:status result))))))

(deftest player-performance-evolution
  (testing "success with player-id"
    (let [player-id (str (ObjectId.))
          request {:params {:player-id player-id}}
          result (with-redefs [agg/player-performance-evolution (fn [_] [])]
                  (handlers/player-performance-evolution request))]
      (is (= 200 (:status result)))))
  (testing "error when player-id missing"
    (let [request {:params {}}
          result (handlers/player-performance-evolution request)]
      (is (= 400 (:status result))))))

(deftest search-players
  (testing "success"
    (let [request {:params {}}
          result (with-redefs [agg/search-players (fn [_] [])]
                  (handlers/search-players request))]
      (is (= 200 (:status result))))))

(deftest championship-comparison
  (let [request {}
        result (with-redefs [agg/championship-comparison (fn [] [{:name "C1"}])]
                (handlers/championship-comparison request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (vector? (:data body)))))

(deftest top-players
  (testing "success with default params"
    (let [request {:params {}}
          result (with-redefs [agg/top-players-by-metric (fn [_ _] [])]
                  (handlers/top-players request))]
      (is (= 200 (:status result)))))
  (testing "success with championship-id"
    (let [champ-id (str (ObjectId.))
          request {:params {:metric "goals" :limit "5" :championship-id champ-id}}
          result (with-redefs [agg/top-players-by-metric (fn [_ _ & _] [])]
                  (handlers/top-players request))]
      (is (= 200 (:status result))))))

(deftest reconcile-stats
  (testing "success synchronous"
    (let [request {}
          result (with-redefs [db/db (fn [] :mock-db)
                              mc/find-maps (fn [_ _ _] [])
                              agg/update-all-player-stats (fn [] {:updated 3})]
                  (handlers/reconcile-stats request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= 3 (get-in body [:data :updated])))))
  (testing "async 202 with job-id"
    (let [request {:query-params {"async" "true"}}
          result (with-redefs [db/db (fn [] :mock-db)
                              mc/find-maps (fn [_ _ _] [])
                              pjobs/submit-full-recompute! (fn [_] {:status :ok :job-id "jid-1"})]
                  (handlers/reconcile-stats request))
          body (parse-body result)]
      (is (= 202 (:status result)))
      (is (= "jid-1" (get-in body [:data :job-id]))))))

(deftest player-stats-jobs-status-handler
  (let [result (with-redefs [job-store/fetch-doc (fn [] {:_id "player-stats-jobs"
                                                        :last-incremental {:job-id "j1"}
                                                        :updated-at (java.util.Date.)})
                            pjobs/executor-runtime-info (fn [] {:queue-size 0 :active-count 0 :pool-size 1})]
                 (handlers/player-stats-jobs-status {}))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (= 0 (get-in body [:data :executor :queue-size])))
    (is (= "j1" (get-in body [:data :last-success :last-incremental :job-id])))))

(deftest championship-table-leaderboards-handler
  (testing "success"
    (let [cid (str (ObjectId.))
          request {:params {:id cid}}
          payload {:top-goals [{:name "A" :goals 3}]
                   :top-assists []
                   :top-games []
                   :top-titles []}
          result (with-redefs [agg/championship-table-leaderboards (fn [_] payload)]
                  (handlers/championship-table-leaderboards request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= 3 (get-in body [:data :top-goals 0 :goals])))))
  (testing "missing id"
    (let [request {:params {:id ""}}
          result (handlers/championship-table-leaderboards request)
          body (parse-body result)]
      (is (= 400 (:status result))))))

;; dashboard-stats omitted: requires real DB or complex stubs (mc/count/mc/find-maps
;; interact with Mongo Java driver and stub fns cause ClassCastException)
