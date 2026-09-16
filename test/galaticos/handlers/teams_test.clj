(ns galaticos.handlers.teams-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.db.team-store :as team-store]
            [galaticos.handlers.teams :as handlers]
            [galaticos.middleware.errors :as errors]
            [galaticos.support.team-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(defn- invoke [handler request]
  ((errors/wrap-errors handler) request))

(defn- with-store [store f]
  (binding [team-store/*store* store]
    (f)))

(deftest list-teams
  (let [request {}
        store (fixtures/team-store
                {:find-all-teams (fn [_] [{:name "T1"}])})
        result (with-store store #(invoke handlers/list-teams request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (vector? (:data body)))))

(deftest get-team
  (testing "found"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          team {:_id (ObjectId. id) :name "Team"}
          store (fixtures/team-store
                  {:find-team-by-id (fn [_ x] (when (= x id) team))})
          result (with-store store #(invoke handlers/get-team request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Team" (get-in body [:data :name])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          store (fixtures/team-store {})
          result (with-store store #(invoke handlers/get-team request))]
      (is (= 404 (:status result))))))

(deftest create-team
  (testing "success"
    (let [request {:json-body {:name "New Team"}}
          created {:_id (ObjectId.) :name "New Team"}
          store (fixtures/team-store
                  {:create-team (fn [_ _] created)})
          result (with-store store #(invoke handlers/create-team request))
          body (parse-body result)]
      (is (= 201 (:status result)))
      (is (= "New Team" (get-in body [:data :name])))))
  (testing "missing required name"
    (let [request {:json-body {}}
          result (invoke handlers/create-team request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "name")))))

(deftest update-team
  (testing "success"
    (let [id (str (ObjectId.))
          request {:params {:id id} :json-body {:name "Updated"}}
          updated {:_id (ObjectId. id) :name "Updated"}
          store (fixtures/team-store
                  {:team-exists? (fn [_ x] (= x id))
                   :update-team-by-id (fn [_ _ _] nil)
                   :find-team-by-id (fn [_ _] updated)})
          result (with-store store #(invoke handlers/update-team request))]
      (is (= 200 (:status result)))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))} :json-body {:name "X"}}
          store (fixtures/team-store {})
          result (with-store store #(invoke handlers/update-team request))]
      (is (= 404 (:status result))))))

(deftest delete-team
  (testing "success when no players"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          store (fixtures/team-store
                  {:team-exists? (fn [_ x] (= x id))
                   :team-has-players? (fn [_ _] false)
                   :delete-team-by-id (fn [_ _] nil)})
          result (with-store store #(invoke handlers/delete-team request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Team deleted" (get-in body [:data :message])))))
  (testing "conflict when has players"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          store (fixtures/team-store
                  {:team-exists? (fn [_ x] (= x id))
                   :team-has-players? (fn [_ _] true)})
          result (with-store store #(invoke handlers/delete-team request))]
      (is (= 409 (:status result)))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          store (fixtures/team-store {})
          result (with-store store #(invoke handlers/delete-team request))]
      (is (= 404 (:status result))))))

(deftest add-player-to-team
  (testing "success"
    (let [team-id (str (ObjectId.))
          player-id (str (ObjectId.))
          request {:params {:id team-id :player-id player-id}}
          team {:_id (ObjectId. team-id) :name "T"}
          store (fixtures/team-store
                  {:add-player-to-team (fn [_ _ _] nil)
                   :find-team-by-id (fn [_ _] team)})
          result (with-store store #(invoke handlers/add-player-to-team request))]
      (is (= 200 (:status result)))))
  (testing "missing params"
    (let [request {:params {}}
          result (invoke handlers/add-player-to-team request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (= "Team ID and player ID required" (:error body))))))

(deftest remove-player-from-team
  (testing "success"
    (let [team-id (str (ObjectId.))
          player-id (str (ObjectId.))
          request {:params {:id team-id :player-id player-id}}
          team {:_id (ObjectId. team-id)}
          store (fixtures/team-store
                  {:remove-player-from-team (fn [_ _ _] nil)
                   :find-team-by-id (fn [_ _] team)})
          result (with-store store #(invoke handlers/remove-player-from-team request))]
      (is (= 200 (:status result))))))
