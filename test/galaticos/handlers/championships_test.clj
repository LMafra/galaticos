(ns galaticos.handlers.championships-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [galaticos.db.championship-store :as championship-store]
            [galaticos.handlers.championships :as handlers]
            [galaticos.middleware.errors :as errors]
            [galaticos.support.championship-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(defn- invoke [handler request]
  ((errors/wrap-errors handler) request))

(defn- with-store [store f]
  (binding [championship-store/*store* store]
    (f)))

(deftest list-championships-smoke
  (let [request {:params {}}
        store (fixtures/championship-store
                 {:find-all-championships (fn [_] [{:name "C1" :_id (ObjectId.) :status "active"}])})
        result (with-store store #(invoke handlers/list-championships request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (vector? (:data body)))))

(deftest get-championship-smoke
  (testing "found"
    (let [id (str (ObjectId.))
          oid (ObjectId. id)
          request {:params {:id id}}
          store (fixtures/championship-store
                   {:find-championship-by-id (fn [_ x] (when (= x id) {:_id oid :name "Champ"}))})
          result (with-store store #(invoke handlers/get-championship request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Champ" (get-in body [:data :name])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          store (fixtures/championship-store {})
          result (with-store store #(invoke handlers/get-championship request))]
      (is (= 404 (:status result))))))

(deftest create-championship-smoke
  (let [request {:json-body {:name "New" :season "2024" :titles-count 1}}
        created {:_id (ObjectId.) :name "New"}
        store (fixtures/championship-store
                 {:create-championship (fn [_ _] created)
                  :create-season (fn [_ d] (merge {:_id (ObjectId.) :status "inactive"} d))})
        result (with-store store #(invoke handlers/create-championship request))
        body (parse-body result)]
    (is (= 201 (:status result)))
    (is (= "New" (get-in body [:data :name])))))

(deftest update-championship-smoke
  (let [id (str (ObjectId.))
        request {:params {:id id} :json-body {:name "Updated"}}
        updated {:_id (ObjectId. id) :name "Updated"}
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ _] updated)
                  :update-championship-by-id (fn [_ _ _] nil)})
        result (with-store store #(invoke handlers/update-championship request))]
    (is (= 200 (:status result)))))

(deftest delete-championship-smoke
  (let [id (str (ObjectId.))
        request {:params {:id id}}
        store (fixtures/championship-store
                 {:championship-exists? (fn [_ x] (= x id))
                  :championship-has-matches? (fn [_ _] false)
                  :delete-championship-by-id (fn [_ _] nil)})
        result (with-store store #(invoke handlers/delete-championship request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (= "Championship deleted" (get-in body [:data :message])))))

(deftest enroll-player-smoke
  (testing "success"
    (let [champ-id (str (ObjectId.))
          player-id (str (ObjectId.))
          request {:params {:id champ-id :player-id player-id}}
          champ {:_id (ObjectId. champ-id) :enrolled-player-ids []}
          store (fixtures/championship-store
                   {:find-active-season-by-championship (fn [_ _] nil)
                    :find-championship-by-id (fn [_ _] champ)
                    :find-player-by-id (fn [_ _] {:_id (ObjectId. player-id)})
                    :add-player-to-championship (fn [_ _ _] nil)})
          result (with-store store #(invoke handlers/enroll-player request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Player enrolled" (get-in body [:data :message])))))
  (testing "missing params"
    (let [request {:params {}}
          result (invoke handlers/enroll-player request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (= "Championship ID and player ID are required" (:error body))))))

(deftest unenroll-player-smoke
  (let [champ-id (str (ObjectId.))
        player-id (str (ObjectId.))
        request {:params {:id champ-id :player-id player-id}}
        store (fixtures/championship-store
                 {:find-active-season-by-championship (fn [_ _] nil)
                  :championship-exists? (fn [_ _] true)
                  :remove-player-from-championship (fn [_ _ _] nil)})
        result (with-store store #(invoke handlers/unenroll-player request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (= "Player unenrolled" (get-in body [:data :message])))))

(deftest finalize-championship-smoke
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids []}
        request {:params {:id champ-id}
                 :json-body {:winner-player-ids [] :titles-award-count 0}}
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ _] champ)
                  :update-championship-by-id (fn [_ _ _] nil)})
        result (with-store store #(invoke handlers/finalize-championship request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (= "Championship finalized" (get-in body [:data :message])))))

(deftest get-championship-players-smoke
  (let [champ-id (str (ObjectId.))
        request {:params {:id champ-id}}
        store (fixtures/championship-store
                 {:championship-exists? (fn [_ _] true)
                  :enrolled-player-object-ids (fn [_ _] [(ObjectId.)])
                  :find-players-by-ids (fn [_ _] [{:name "P1"}])})
        result (with-store store #(invoke handlers/get-championship-players request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (vector? (:data body)))
    (is (= "P1" (get-in body [:data 0 :name])))))
