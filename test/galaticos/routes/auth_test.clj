(ns galaticos.routes.auth-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.routes.auth :refer [auth-routes]]
            [galaticos.handlers.auth :as handlers]
            [galaticos.middleware.auth :as auth-mw]))

(defn- req [method uri]
  {:request-method method :uri uri :headers {}})

(deftest auth-routes-dispatch-test
  (with-redefs [handlers/login (fn [_] {:status 200 :body "login"})
                handlers/logout (fn [_] {:status 200 :body "logout"})
                auth-mw/wrap-optional-auth (fn [handler] handler)
                handlers/check-auth (fn [_] {:status 200 :body "check"})]
    (is (= 200 (:status (auth-routes (req :post "/api/auth/login")))))
    (is (= "login" (:body (auth-routes (req :post "/api/auth/login")))))
    (is (= 200 (:status (auth-routes (req :post "/api/auth/logout")))))
    (is (= "logout" (:body (auth-routes (req :post "/api/auth/logout")))))
    (is (= 200 (:status (auth-routes (req :get "/api/auth/check")))))
    (is (= "check" (:body (auth-routes (req :get "/api/auth/check")))))))
