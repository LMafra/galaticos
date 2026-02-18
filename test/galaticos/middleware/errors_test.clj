(ns galaticos.middleware.errors-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is]]
            [galaticos.middleware.errors :as errors]))

(deftest wrap-errors-handles-ex-info
  (let [handler (errors/wrap-errors (fn [_]
                                      (throw (ex-info "oops" {:status 422 :message "invalid"}))))
        response (handler {:request-method :get})
        body (json/read-str (:body response) :key-fn keyword)]
    (is (= 422 (:status response)))
    (is (= {:success false :error "invalid"} body))))

(deftest wrap-errors-handles-exception
  (let [handler (errors/wrap-errors (fn [_] (throw (Exception. "boom"))))
        response (handler {:request-method :get})
        body (json/read-str (:body response) :key-fn keyword)]
    (is (= 500 (:status response)))
    (is (= {:success false :error "Internal server error"} body))))

