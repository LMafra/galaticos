(ns galaticos.routes.seasons-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.routes.seasons :refer [season-routes]]
            [galaticos.handlers.seasons :as handlers]
            [galaticos.middleware.auth :as auth]))

(defn- req [method uri]
  {:request-method method :uri uri :headers {}})

(deftest season-routes-dispatch-test
  (testing "public GET routes hit handlers"
    (with-redefs [handlers/list-seasons (fn [r] {:status 200 :body (str "list-" (get-in r [:params :id]))})
                  handlers/get-season (fn [r] {:status 200 :body (str "get-" (get-in r [:params :id]))})
                  handlers/get-season-players (fn [r] {:status 200 :body (str "players-" (get-in r [:params :id]))})]
      (is (= 200 (:status (season-routes (req :get "/api/championships/c1/seasons")))))
      (is (= "list-c1" (:body (season-routes (req :get "/api/championships/c1/seasons")))))
      (is (= 200 (:status (season-routes (req :get "/api/seasons/s1")))))
      (is (= "get-s1" (:body (season-routes (req :get "/api/seasons/s1")))))
      (is (= 200 (:status (season-routes (req :get "/api/seasons/s1/players")))))
      (is (= "players-s1" (:body (season-routes (req :get "/api/seasons/s1/players")))))))
  (testing "authenticated routes bypass wrap-auth in tests and hit handlers"
    (with-redefs [auth/wrap-auth (fn [handler] handler)
                  handlers/create-season (fn [r] {:status 201 :body (str "create-" (get-in r [:params :id]))})
                  handlers/update-season (fn [r] {:status 200 :body (str "put-" (get-in r [:params :id]))})
                  handlers/delete-season (fn [r] {:status 200 :body (str "del-" (get-in r [:params :id]))})
                  handlers/activate-season (fn [r] {:status 200 :body (str "act-" (get-in r [:params :id]))})
                  handlers/enroll-player (fn [r] {:status 200 :body (str "enr-" (get-in r [:params :id]))})
                  handlers/unenroll-player (fn [r] {:status 200 :body (str "unr-" (get-in r [:params :id]))})
                  handlers/finalize-season (fn [r] {:status 200 :body (str "fin-" (get-in r [:params :id]))})]
      (is (= 201 (:status (season-routes (req :post "/api/championships/c2/seasons")))))
      (is (= "create-c2" (:body (season-routes (req :post "/api/championships/c2/seasons")))))
      (is (= 200 (:status (season-routes (req :put "/api/seasons/s2")))))
      (is (= "put-s2" (:body (season-routes (req :put "/api/seasons/s2")))))
      (is (= 200 (:status (season-routes (req :delete "/api/seasons/s2")))))
      (is (= "del-s2" (:body (season-routes (req :delete "/api/seasons/s2")))))
      (is (= 200 (:status (season-routes (req :post "/api/seasons/s2/activate")))))
      (is (= "act-s2" (:body (season-routes (req :post "/api/seasons/s2/activate")))))
      (is (= 200 (:status (season-routes (req :post "/api/seasons/s2/enroll/p9")))))
      (is (= "enr-s2" (:body (season-routes (req :post "/api/seasons/s2/enroll/p9")))))
      (is (= 200 (:status (season-routes (req :delete "/api/seasons/s2/unenroll/p9")))))
      (is (= "unr-s2" (:body (season-routes (req :delete "/api/seasons/s2/unenroll/p9")))))
      (is (= 200 (:status (season-routes (req :post "/api/seasons/s2/finalize")))))
      (is (= "fin-s2" (:body (season-routes (req :post "/api/seasons/s2/finalize"))))))))
