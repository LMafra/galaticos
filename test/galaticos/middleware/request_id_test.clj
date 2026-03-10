(ns galaticos.middleware.request-id-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.middleware.request-id :as request-id])
  (:import [org.slf4j MDC]))

(deftest wrap-request-id
  (testing "adds :request-id to request"
    (let [handler (request-id/wrap-request-id (fn [req]
                                                (is (string? (:request-id req)))
                                                (is (= 36 (count (:request-id req))))
                                                {:status 200 :body "ok"}))
          response (handler {:request-method :get :uri "/"})]
      (is (= 200 (:status response)))))
  (testing "adds X-Request-Id header to map response"
    (let [request-id-atom (atom nil)
          handler (request-id/wrap-request-id (fn [req]
                                                (reset! request-id-atom (:request-id req))
                                                {:status 200 :body "ok" :headers {}}))
          response (handler {:request-method :get})]
      (is (= 200 (:status response)))
      (is (string? (get-in response [:headers "X-Request-Id"])))
      (is (= (get-in response [:headers "X-Request-Id"]) @request-id-atom))))
  (testing "passes through non-map response without adding header"
    (let [handler (request-id/wrap-request-id (fn [_] "raw string"))
          response (handler {:request-method :get})]
      (is (= "raw string" response))))
  (testing "clears MDC after request"
    (let [handler (request-id/wrap-request-id (fn [req]
                                                (MDC/put "request_id" (:request-id req))
                                                {:status 200}))
          _ (handler {:request-method :get})
          mdc-after (MDC/get "request_id")]
      (is (nil? mdc-after)))))
