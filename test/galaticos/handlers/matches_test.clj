(ns galaticos.handlers.matches-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.analytics.player-stats-jobs :as player-stats-jobs]
            [galaticos.db.match-store :as match-store]
            [galaticos.handlers.matches :as handlers]
            [galaticos.middleware.errors :as errors]
            [galaticos.support.match-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(defn- invoke [handler request]
  ((errors/wrap-errors handler) request))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(defn- with-store [store f]
  (binding [match-store/*store* store]
    (f)))

(deftest list-matches-smoke
  (testing "all"
    (let [request {:params {}}
          store (fixtures/match-store {})
          result (with-store store #(invoke handlers/list-matches request))]
      (is (= 200 (:status result)))))
  (testing "by season-id"
    (let [sid (str (ObjectId.))
          request {:params {:season-id sid}}
          store (fixtures/match-store
                   {:find-matches-by-season (fn [_ x] (when (= x sid) [{:_id "m1"}]))})
          result (with-store store #(invoke handlers/list-matches request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= 1 (count (:data body)))))))

(deftest get-match-smoke
  (testing "found"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          match {:_id (ObjectId. id) :date "2024-01-01" :player-statistics []}
          store (fixtures/match-store
                   {:find-match-by-id (fn [_ x] (when (= x id) match))})
          result (with-store store #(invoke handlers/get-match request))]
      (is (= 200 (:status result)))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          store (fixtures/match-store {})
          result (with-store store #(invoke handlers/get-match request))]
      (is (= 404 (:status result))))))

(deftest create-match-validation
  (testing "invalid body - unknown fields"
    (let [cid (str (ObjectId.))
          pid (str (ObjectId.))
          tid (str (ObjectId.))
          request {:json-body {:championship-id cid
                               :player-statistics [{:player-id pid :team-id tid}]
                               :not-allowed 1}}
          result (invoke handlers/create-match request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "Unknown fields"))))
  (testing "invalid body - missing required"
    (let [request {:json-body {:championship-id (str (ObjectId.))}}
          result (invoke handlers/create-match request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "player-statistics"))))
  (testing "player-statistics not a vector"
    (let [request {:json-body {:championship-id (str (ObjectId.))
                               :player-statistics "bad"}}
          result (invoke handlers/create-match request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "player-statistics")))))

(deftest create-match-smoke
  (let [champ-id (ObjectId.)
        team-id (ObjectId.)
        player-id (ObjectId.)
        season-id (ObjectId.)
        created {:_id (ObjectId.)}
        request {:json-body {:championship-id (str champ-id)
                             :player-statistics [{:player-id (str player-id) :team-id (str team-id)}]}}
        season {:_id season-id :status "active" :enrolled-player-ids [player-id]}
        find-team (fn [tid]
                    (when (= tid team-id)
                      {:_id team-id :name "T" :active-player-ids [player-id]}))
        store (fixtures/match-store
                 {:find-season-for-new-match (fn [_ _ _] season)
                  :find-default-season-for-championship (fn [_ _] season)
                  :find-season-by-id (fn [_ sid] (when (= sid season-id) season))
                  :find-championship-by-id (fn [_ _] {:enrolled-player-ids [player-id]})
                  :find-players-by-ids (fn [_ ids]
                                         (vec (for [id ids]
                                                {:_id id :name "P" :team-id team-id})))
                  :find-team-by-id find-team
                  :find-teams-by-ids (fixtures/teams-by-id-fn find-team)
                  :create-match (fn [_ _ _ _] created)
                  :add-match-to-season! (fn [_ _ _] nil)})]
    (with-redefs [player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
      (let [result (with-store store #(invoke handlers/create-match request))
            body (parse-body result)]
        (is (= 201 (:status result)))
        (is (true? (:success body)))))))

(deftest update-match-smoke
  (let [id (str (ObjectId.))
        request {:params {:id id} :json-body {:notes "x"}}
        store (fixtures/match-store
                 {:match-exists? (fn [_ x] (= x id))
                  :find-match-by-id (fn [_ x]
                                      (when (= x id)
                                        {:_id (ObjectId. id) :player-statistics []}))
                  :update-match-by-id (fn [_ _ _ _] nil)})]
    (with-redefs [player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
      (let [result (with-store store #(invoke handlers/update-match request))]
        (is (= 200 (:status result)))))))

(deftest delete-match-smoke
  (testing "success"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          store (fixtures/match-store
                   {:match-exists? (fn [_ x] (= x id))
                    :find-match-by-id (fn [_ _] {:_id (ObjectId. id) :player-statistics []})
                    :delete-match-by-id (fn [_ _] nil)})]
      (with-redefs [player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
        (let [result (with-store store #(invoke handlers/delete-match request))
              body (parse-body result)]
          (is (= 200 (:status result)))
          (is (= "Match deleted" (get-in body [:data :message])))))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          store (fixtures/match-store {:match-exists? (fn [_ _] false)})
          result (with-store store #(invoke handlers/delete-match request))]
      (is (= 404 (:status result))))))
