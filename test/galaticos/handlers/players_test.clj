(ns galaticos.handlers.players-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.players :as handlers]
            [galaticos.db.players :as players-db]
            [galaticos.db.teams :as teams-db]
            [galaticos.db.aggregations :as agg]
            [galaticos.util.response :as resp])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(deftest list-players
  (testing "without filters"
    (let [request {:params {}}
          result (with-redefs [players-db/find-all (fn [_] [{:name "P1"}])]
                  (handlers/list-players request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (true? (:success body)))
      (is (vector? (:data body)))))
  (testing "with team-id filter"
    (let [request {:params {:team-id (str (ObjectId.))}}
          result (with-redefs [players-db/find-all (fn [filters] (is (contains? filters :team-id)) [])]
                  (handlers/list-players request))]
      (is (= 200 (:status result)))))
  (testing "with active=true"
    (let [request {:params {:active "true"}}
          result (with-redefs [players-db/find-active (fn [] [])]
                  (handlers/list-players request))]
      (is (= 200 (:status result))))))

(deftest get-player
  (testing "found"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          player {:_id (ObjectId. id) :name "Test"}
          result (with-redefs [players-db/find-by-id (fn [x] (when (= x id) player))]
                  (handlers/get-player request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Test" (get-in body [:data :name])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [players-db/find-by-id (fn [_] nil)]
                  (handlers/get-player request))]
      (is (= 404 (:status result)))
      (is (= "Player not found" (:error (parse-body result)))))))

(deftest get-player-detail-bundle
  (testing "found includes evolution"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          player {:_id (ObjectId. id) :name "Bundled"}
          evo [{:year 2024}]
          result (with-redefs [players-db/find-by-id (fn [x] (when (= x id) player))
                              agg/player-performance-evolution (fn [x] (when (= x id) evo))]
                  (handlers/get-player-detail-bundle request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Bundled" (get-in body [:data :player :name])))
      (is (= 2024 (get-in body [:data :evolution 0 :year]))))))

(deftest create-player
  (testing "success"
    (let [request {:json-body {:name "New" :position "FW"}}
          created {:_id (ObjectId.) :name "New" :position "FW"}
          result (with-redefs [players-db/create (fn [_] created)]
                  (handlers/create-player request))
          body (parse-body result)]
      (is (= 201 (:status result)))
      (is (= "New" (get-in body [:data :name])))))
  (testing "success with team-id calls add-player"
    (let [tid (ObjectId.)
          pid (ObjectId.)
          request {:json-body {:name "New" :position "FW" :team-id (str tid)}}
          created {:_id pid :name "New" :position "FW" :team-id tid}
          added (atom nil)
          result (with-redefs [teams-db/exists? (fn [id] (= id tid))
                               teams-db/add-player (fn [team-id player-id]
                                                     (reset! added [team-id player-id]))
                               players-db/create (fn [_] created)]
                  (handlers/create-player request))]
      (is (= 201 (:status result)))
      (is (= [tid pid] @added))))
  (testing "team-id not found"
    (let [tid (str (ObjectId.))
          request {:json-body {:name "New" :position "FW" :team-id tid}}
          result (with-redefs [teams-db/exists? (fn [_] false)]
                  (handlers/create-player request))
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "Team not found"))))
  (testing "missing required fields"
    (let [request {:json-body {:name "Only"}}
          result (handlers/create-player request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (string? (:error body)))
      (is (str/includes? (:error body) "position"))))
  (testing "unknown fields"
    (let [request {:json-body {:name "X" :position "FW" :invalid 1}}
          result (handlers/create-player request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "invalid")))))

(deftest update-player
  (testing "success"
    (let [id (str (ObjectId.))
          request {:params {:id id} :json-body {:name "Updated" :position "FW"}}
          updated {:_id (ObjectId. id) :name "Updated"}
          result (with-redefs [players-db/exists? (fn [x] (= x id))
                               players-db/update-by-id (fn [_ _] nil)
                               players-db/find-by-id (fn [_] updated)]
                  (handlers/update-player request))]
      (is (= 200 (:status result)))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))} :json-body {:name "X"}}
          result (with-redefs [players-db/exists? (fn [_] false)]
                  (handlers/update-player request))]
      (is (= 404 (:status result))))))

(deftest delete-player
  (testing "success"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          result (with-redefs [players-db/exists? (fn [x] (= x id))
                               players-db/delete-by-id (fn [_] nil)]
                  (handlers/delete-player request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Player deleted" (get-in body [:data :message])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [players-db/exists? (fn [_] false)]
                  (handlers/delete-player request))]
      (is (= 404 (:status result))))))
