(ns galaticos.handlers.teams-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.teams :as handlers]
            [galaticos.db.teams :as teams-db])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(deftest list-teams
  (let [request {}
        result (with-redefs [teams-db/find-all (fn [] [{:name "T1"}])]
                (handlers/list-teams request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (vector? (:data body)))))

(deftest get-team
  (testing "found"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          team {:_id (ObjectId. id) :name "Team"}
          result (with-redefs [teams-db/find-by-id (fn [x] (when (= x id) team))]
                  (handlers/get-team request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Team" (get-in body [:data :name])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [teams-db/find-by-id (fn [_] nil)]
                  (handlers/get-team request))]
      (is (= 404 (:status result))))))

(deftest create-team
  (testing "success"
    (let [request {:json-body {:name "New Team"}}
          created {:_id (ObjectId.) :name "New Team"}
          result (with-redefs [teams-db/create (fn [_] created)]
                  (handlers/create-team request))
          body (parse-body result)]
      (is (= 201 (:status result)))
      (is (= "New Team" (get-in body [:data :name])))))
  (testing "missing required name"
    (let [request {:json-body {}}
          result (handlers/create-team request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "name")))))

(deftest update-team
  (testing "success"
    (let [id (str (ObjectId.))
          request {:params {:id id} :json-body {:name "Updated"}}
          updated {:_id (ObjectId. id) :name "Updated"}
          result (with-redefs [teams-db/exists? (fn [x] (= x id))
                               teams-db/update-by-id (fn [_ _] nil)
                               teams-db/find-by-id (fn [_] updated)]
                  (handlers/update-team request))]
      (is (= 200 (:status result)))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))} :json-body {:name "X"}}
          result (with-redefs [teams-db/exists? (fn [_] false)]
                  (handlers/update-team request))]
      (is (= 404 (:status result))))))

(deftest delete-team
  (testing "success when no players"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          result (with-redefs [teams-db/exists? (fn [x] (= x id))
                               teams-db/has-players? (fn [_] false)
                               teams-db/delete-by-id (fn [_] nil)]
                  (handlers/delete-team request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Team deleted" (get-in body [:data :message])))))
  (testing "conflict when has players"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          result (with-redefs [teams-db/exists? (fn [x] (= x id))
                               teams-db/has-players? (fn [_] true)]
                  (handlers/delete-team request))]
      (is (= 409 (:status result)))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [teams-db/exists? (fn [_] false)]
                  (handlers/delete-team request))]
      (is (= 404 (:status result))))))

(deftest add-player-to-team
  (testing "success"
    (let [team-id (str (ObjectId.))
          player-id (str (ObjectId.))
          request {:params {:id team-id :player-id player-id}}
          team {:_id (ObjectId. team-id) :name "T"}
          result (with-redefs [teams-db/add-player (fn [_ _] nil)
                               teams-db/find-by-id (fn [_] team)]
                  (handlers/add-player-to-team request))]
      (is (= 200 (:status result)))))
  (testing "missing params"
    (let [request {:params {}}
          result (handlers/add-player-to-team request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (= "Team ID and player ID required" (:error body))))))

(deftest remove-player-from-team
  (testing "success"
    (let [team-id (str (ObjectId.))
          player-id (str (ObjectId.))
          request {:params {:id team-id :player-id player-id}}
          team {:_id (ObjectId. team-id)}
          result (with-redefs [teams-db/remove-player (fn [_ _] nil)
                               teams-db/find-by-id (fn [_] team)]
                  (handlers/remove-player-from-team request))]
      (is (= 200 (:status result))))))
