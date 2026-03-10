(ns galaticos.middleware.cors-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.middleware.cors :as cors]))

(deftest wrap-cors-preflight
  (let [handler (cors/wrap-cors (fn [_] {:status 200 :body "ok"}))
        response (handler {:request-method :options
                           :headers {"origin" "http://localhost:3000"}})]
    (is (= 204 (:status response)))
    (is (= "" (:body response)))
    (is (= "http://localhost:3000"
           (get-in response [:headers "Access-Control-Allow-Origin"])))))

(deftest wrap-cors-adds-headers
  (testing "merges headers for non-OPTIONS requests"
    (let [handler (cors/wrap-cors (fn [_] {:status 200 :headers {"X-Test" "1"}}))
          response (handler {:request-method :get
                             :headers {"origin" "http://localhost:3000"}})]
      (is (= 200 (:status response)))
      (is (= "1" (get-in response [:headers "X-Test"])))
      (is (= "http://localhost:3000"
             (get-in response [:headers "Access-Control-Allow-Origin"]))))))

