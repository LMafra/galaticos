(ns galaticos.handlers.auth-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.auth :as handlers]
            [galaticos.middleware.auth :as auth]))

(deftest login
  (testing "successful login returns token"
    (let [request {:json-body {:username "admin" :password "secret"}}
          result (with-redefs [auth/login! (fn [_ _] {:success true :username "admin" :token "jwt-token-123"})]
                  (handlers/login request))
          body (json/read-str (:body result) :key-fn keyword)
          data (:data body)]
      (is (= 200 (:status result)))
      (is (= true (:success body)))
      (is (= "admin" (:username data)))
      (is (= "jwt-token-123" (:token data)))))
  (testing "missing username and password returns 400"
    (let [request {:json-body {}}
          result (handlers/login request)
          body (json/read-str (:body result) :key-fn keyword)]
      (is (= 400 (:status result)))
      (is (= false (:success body)))
      (is (= "Username and password required" (:error body)))))
  (testing "invalid credentials returns 401"
    (let [request {:json-body {:username "admin" :password "wrong"}}
          result (with-redefs [auth/login! (fn [_ _] {:success false :error "Invalid credentials"})]
                  (handlers/login request))
          body (json/read-str (:body result) :key-fn keyword)]
      (is (= 401 (:status result)))
      (is (= false (:success body)))
      (is (= "Invalid credentials" (:error body)))))
  (testing "exception during login returns 500"
    (let [request {:json-body {:username "admin" :password "x"}}
          result (with-redefs [auth/login! (fn [_ _] (throw (ex-info "db error" {})))]
                  (handlers/login request))
          body (json/read-str (:body result) :key-fn keyword)]
      (is (= 500 (:status result)))
      (is (= false (:success body)))
      (is (string? (:error body))))))

(deftest logout
  (testing "successful logout"
    (let [request {}
          result (with-redefs [auth/logout! (fn [_] nil)]
                  (handlers/logout request))
          body (json/read-str (:body result) :key-fn keyword)
          data (:data body)]
      (is (= 200 (:status result)))
      (is (= "Logged out" (:message data)))))
  (testing "exception during logout returns 500"
    (let [request {}
          result (with-redefs [auth/logout! (fn [_] (throw (RuntimeException. "session error")))]
                  (handlers/logout request))
          body (json/read-str (:body result) :key-fn keyword)]
      (is (= 500 (:status result)))
      (is (string? (:error body))))))

(deftest check-auth
  (testing "authenticated user returns success"
    (let [request {:identity {:sub "admin"}}
          result (handlers/check-auth request)
          body (json/read-str (:body result) :key-fn keyword)
          data (:data body)]
      (is (= 200 (:status result)))
      (is (= true (:authenticated data)))
      (is (= "admin" (:user data)))))
  (testing "unauthenticated returns 200 with authenticated false"
    (let [request {}
          result (handlers/check-auth request)
          body (json/read-str (:body result) :key-fn keyword)
          data (:data body)]
      (is (= 200 (:status result)))
      (is (= true (:success body)))
      (is (= false (:authenticated data))))))
