(ns galaticos.handlers.matches-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.matches :as handlers]
            [galaticos.db.matches :as matches-db]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.db.seasons :as seasons-db]
            [galaticos.db.teams :as teams-db]
            [galaticos.db.aggregations :as agg])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(deftest list-matches
  (testing "all"
    (let [request {:params {}}
          result (with-redefs [matches-db/find-all (fn [] [])]
                  (handlers/list-matches request))]
      (is (= 200 (:status result)))))
  (testing "by championship-id"
    (let [champ-id (str (ObjectId.))
          request {:params {:championship-id champ-id}}
          result (with-redefs [matches-db/find-by-championship (fn [_] [])]
                  (handlers/list-matches request))]
      (is (= 200 (:status result))))))

(deftest get-match
  (testing "found"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          match {:_id (ObjectId. id) :date "2024-01-01"}
          result (with-redefs [matches-db/find-by-id (fn [x] (when (= x id) match))]
                  (handlers/get-match request))]
      (is (= 200 (:status result)))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [matches-db/find-by-id (fn [_] nil)]
                  (handlers/get-match request))]
      (is (= 404 (:status result))))))

(deftest create-match
  (testing "invalid body - missing required"
    (let [request {:json-body {:championship-id (str (ObjectId.))}}
          result (handlers/create-match request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "player-statistics"))))
  (testing "success with minimal player-statistics"
    (let [champ-id (ObjectId.)
          team-id (ObjectId.)
          player-id (ObjectId.)
          request {:json-body {:championship-id (str champ-id)
                               :player-statistics [{:player-id (str player-id) :team-id (str team-id)}]}}
          created {:_id (ObjectId.)}
          result (with-redefs [seasons-db/find-active-by-championship (fn [_] nil)
                               championships-db/find-by-id (fn [x]
                                                             (when (or (= x (str champ-id))
                                                                       (= x champ-id))
                                                               {:enrolled-player-ids [player-id]}))
                               players-db/find-by-ids
                               (fn [_ids]
                                 [{:_id player-id :name "P" :team-id team-id}])
                               teams-db/find-by-id
                               (fn [tid]
                                 (when (= tid team-id)
                                   {:_id team-id :name "T" :active-player-ids [player-id]}))
                               matches-db/create (fn [_ _] created)
                               agg/update-player-stats-for-match (fn [_] nil)]
                  (handlers/create-match request))
          body (parse-body result)]
      (is (= 201 (:status result)))
      (is (true? (:success body))))))

(deftest update-match
  (testing "not found"
    (let [id (str (ObjectId.))
          request {:params {:id id} :json-body {}}
          result (with-redefs [matches-db/exists? (fn [_] false)]
                  (handlers/update-match request))]
      (is (= 404 (:status result))))))

(deftest delete-match
  (testing "success"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          result (with-redefs [matches-db/exists? (fn [x] (= x id))
                               matches-db/delete-by-id (fn [_] nil)
                               agg/update-all-player-stats (fn [] nil)]
                  (handlers/delete-match request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Match deleted" (get-in body [:data :message])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [matches-db/exists? (fn [_] false)]
                  (handlers/delete-match request))]
      (is (= 404 (:status result))))))
