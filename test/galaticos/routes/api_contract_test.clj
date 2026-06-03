(ns galaticos.routes.api-contract-test
  "Route-level contract tests: stable status codes and JSON envelope for critical API paths."
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handler :as handler]
            [galaticos.db.core :as db]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.matches :as matches-db]
            [galaticos.handlers.aggregations :as agg-handlers]
            [galaticos.middleware.auth :as auth-mw]
            [galaticos.util.response :as resp]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(defn- json-req [method uri & {:keys [body headers params]}]
  (let [base {:request-method method
              :uri uri
              :scheme :http
              :server-name "localhost"
              :server-port 3000}]
    (cond-> base
      true (assoc :headers (merge {"accept" "application/json"} headers))
      body (assoc :body body
                  :headers (merge {"accept" "application/json"
                                   "content-type" "application/json"}
                                  headers))
      params (assoc :params params))))

(def ^:private strict-wrap-auth
  (fn [handler]
    (fn [request]
      (if (some? (:identity request))
        (handler request)
        (resp/unauthorized "Authentication required")))))

(deftest api-contract-unauthenticated
  (testing "GET /api/championships without auth returns 200 (public list)"
    (let [response (with-redefs [db/connection (fn [] :conn)
                                 championships-db/find-all (fn [] [])]
                    (handler/app (json-req :get "/api/championships")))]
      (is (= 200 (:status response)))
      (is (true? (:success (parse-body response))))))
  (testing "POST /api/aggregations/reconcile without auth returns 401"
    (let [response (with-redefs [db/connection (fn [] :conn)
                                 auth-mw/wrap-auth strict-wrap-auth]
                    (handler/app (json-req :post "/api/aggregations/reconcile")))]
      (is (= 401 (:status response)))))
  (testing "GET /api/exports/dashboard.csv without auth returns 401"
    (let [response (with-redefs [db/connection (fn [] :conn)
                                 auth-mw/wrap-auth strict-wrap-auth]
                    (handler/app (json-req :get "/api/exports/dashboard.csv")))]
      (is (= 401 (:status response)))))
  (testing "GET /api/aggregations/players/:id/insights without auth returns 401"
    (let [response (with-redefs [db/connection (fn [] :conn)
                                 auth-mw/wrap-auth strict-wrap-auth]
                    (handler/app (json-req :get "/api/aggregations/players/507f1f77bcf86cd799439011/insights")))]
      (is (= 401 (:status response))))))

(deftest api-contract-authenticated-reconcile
  (testing "POST reconcile with auth bypass returns JSON success envelope"
    (let [response (with-redefs [db/connection (fn [] :conn)
                                 auth-mw/wrap-auth (fn [handler] handler)
                                 agg-handlers/reconcile-stats
                                 (fn [_]
                                   {:status 200
                                    :headers {"Content-Type" "application/json"}
                                    :body (json/write-str {:success true :data {:updated 0}})})]
                    (handler/app (json-req :post "/api/aggregations/reconcile")))]
      (is (= 200 (:status response)))
      (is (true? (:success (parse-body response)))))))

(deftest api-contract-matches-list
  (testing "GET /api/matches returns 200 with success envelope"
    (let [response (with-redefs [db/connection (fn [] :conn)
                                 matches-db/find-all (fn [] [])]
                    (handler/app (json-req :get "/api/matches")))]
      (is (= 200 (:status response)))
      (is (contains? (parse-body response) :success)))))
