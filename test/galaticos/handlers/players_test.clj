(ns galaticos.handlers.players-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.player-store :as player-store]
            [galaticos.handlers.players :as handlers]
            [galaticos.middleware.errors :as errors]
            [galaticos.support.player-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(defn- invoke [handler request]
  ((errors/wrap-errors handler) request))

(defn- with-store [store f]
  (binding [player-store/*store* store]
    (f)))

(deftest list-players
  (testing "without filters"
    (let [request {:params {}}
          store (fixtures/player-store
                  {:find-all-players (fn [_ _] [{:name "P1"}])})
          result (with-store store #(invoke handlers/list-players request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (true? (:success body)))
      (is (vector? (:data body)))))
  (testing "with team-id filter"
    (let [request {:params {:team-id (str (ObjectId.))}}
          store (fixtures/player-store
                  {:find-all-players (fn [_ filters]
                                       (is (contains? filters :team-id))
                                       [])})
          result (with-store store #(invoke handlers/list-players request))]
      (is (= 200 (:status result)))))
  (testing "with active=true"
    (let [request {:params {:active "true"}}
          store (fixtures/player-store
                  {:find-active-players (fn [_] [])})
          result (with-store store #(invoke handlers/list-players request))]
      (is (= 200 (:status result))))))

(deftest get-player
  (testing "found"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          player {:_id (ObjectId. id) :name "Test"}
          store (fixtures/player-store
                  {:find-player-by-id (fn [_ x] (when (= x id) player))})
          result (with-store store #(invoke handlers/get-player request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Test" (get-in body [:data :name])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          store (fixtures/player-store {})
          result (with-store store #(invoke handlers/get-player request))]
      (is (= 404 (:status result)))
      (is (= "Player not found" (:error (parse-body result)))))))

(deftest get-player-detail-bundle
  (testing "found includes evolution"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          player {:_id (ObjectId. id) :name "Bundled"}
          evo [{:year 2024}]
          store (fixtures/player-store
                  {:find-player-by-id (fn [_ x] (when (= x id) player))})
          result (with-redefs [agg/player-performance-evolution (fn [x] (when (= x id) evo))]
                   (with-store store #(invoke handlers/get-player-detail-bundle request)))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Bundled" (get-in body [:data :player :name])))
      (is (= 2024 (get-in body [:data :evolution 0 :year])))))
  (testing "stale team-name on document is replaced by teams collection"
    (let [tid (ObjectId.)
          id (str (ObjectId.))
          request {:params {:id id}}
          player {:_id (ObjectId. id)
                  :name "P"
                  :team-id tid
                  :team-name (str tid)}
          store (fixtures/player-store
                  {:find-player-by-id (fn [_ x] (when (= x id) player))
                   :find-team-by-id (fn [_ x] (when (= x tid) {:_id tid :name "Galáticos"}))})
          result (with-redefs [agg/player-performance-evolution (fn [_] [])]
                   (with-store store #(invoke handlers/get-player-detail-bundle request)))
          body (parse-body result)]
      (is (= "Galáticos" (get-in body [:data :player :team-name]))))))

(deftest create-player
  (testing "success"
    (let [request {:json-body {:name "New" :position "FW"}}
          created {:_id (ObjectId.) :name "New" :position "FW"}
          store (fixtures/player-store
                  {:create-player (fn [_ _] created)})
          result (with-store store #(invoke handlers/create-player request))
          body (parse-body result)]
      (is (= 201 (:status result)))
      (is (= "New" (get-in body [:data :name])))))
  (testing "success with team-id calls add-player"
    (let [tid (ObjectId.)
          pid (ObjectId.)
          request {:json-body {:name "New" :position "FW" :team-id (str tid)}}
          created {:_id pid :name "New" :position "FW" :team-id tid}
          added (atom nil)
          store (fixtures/player-store
                  {:team-exists? (fn [_ id] (= id tid))
                   :add-player-to-team (fn [_ team-id player-id]
                                         (reset! added [team-id player-id]))
                   :create-player (fn [_ _] created)})
          result (with-store store #(invoke handlers/create-player request))]
      (is (= 201 (:status result)))
      (is (= [tid pid] @added))))
  (testing "team-id not found"
    (let [tid (str (ObjectId.))
          request {:json-body {:name "New" :position "FW" :team-id tid}}
          store (fixtures/player-store
                  {:team-exists? (fn [_ _] false)})
          result (with-store store #(invoke handlers/create-player request))
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "Team not found"))))
  (testing "missing required fields"
    (let [request {:json-body {:name "Only"}}
          result (invoke handlers/create-player request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (string? (:error body)))
      (is (str/includes? (:error body) "position"))))
  (testing "unknown fields"
    (let [request {:json-body {:name "X" :position "FW" :invalid 1}}
          result (invoke handlers/create-player request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "invalid")))))

(deftest update-player
  (testing "success"
    (let [id (str (ObjectId.))
          request {:params {:id id} :json-body {:name "Updated" :position "FW"}}
          updated {:_id (ObjectId. id) :name "Updated"}
          store (fixtures/player-store
                  {:player-exists? (fn [_ x] (= x id))
                   :update-player-by-id (fn [_ _ _] nil)
                   :find-player-by-id (fn [_ _] updated)})
          result (with-store store #(invoke handlers/update-player request))]
      (is (= 200 (:status result)))))
  (testing "nickname allowed"
    (let [id (str (ObjectId.))
          request {:params {:id id} :json-body {:nickname "Zico"}}
          updated {:_id (ObjectId. id) :nickname "Zico"}
          store (fixtures/player-store
                  {:player-exists? (fn [_ x] (= x id))
                   :update-player-by-id (fn [_ _ _] nil)
                   :find-player-by-id (fn [_ _] updated)})
          result (with-store store #(invoke handlers/update-player request))]
      (is (= 200 (:status result)))
      (is (= "Zico" (get-in (parse-body result) [:data :nickname])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))} :json-body {:name "X"}}
          store (fixtures/player-store {})
          result (with-store store #(invoke handlers/update-player request))]
      (is (= 404 (:status result))))))

(deftest delete-player
  (testing "success"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          store (fixtures/player-store
                  {:player-exists? (fn [_ x] (= x id))
                   :delete-player-by-id (fn [_ _] nil)})
          result (with-store store #(invoke handlers/delete-player request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Player deleted" (get-in body [:data :message])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          store (fixtures/player-store {})
          result (with-store store #(invoke handlers/delete-player request))]
      (is (= 404 (:status result))))))
