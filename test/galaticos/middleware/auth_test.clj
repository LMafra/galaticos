(ns galaticos.middleware.auth-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [galaticos.middleware.auth :as auth]
            [galaticos.db.admins :as admins]))

(deftest authenticated-and-current-user
  (is (true? (auth/authenticated? {:identity {:sub "admin"}})))
  (is (false? (auth/authenticated? {})))
  (is (= "admin" (auth/current-user {:identity {:sub "admin"}})))
  (is (= "user" (auth/current-user {:identity {:user "user"}})))
  (is (= "name" (auth/current-user {:identity {:username "name"}}))))

(deftest wrap-auth-valid-token
  (let [token (#'auth/issue-token "admin")
        handler (auth/wrap-auth (fn [request] {:status 200 :body (:identity request)}))
        response (handler {:headers {"authorization" (str "Bearer " token)}})]
    (is (= 200 (:status response)))
    (is (= "admin" (get-in response [:body :sub])))))

(deftest wrap-auth-invalid-token
  (let [handler (auth/wrap-auth (fn [_] {:status 200 :body "ok"}))
        response (handler {:headers {"authorization" "Bearer invalid.token"}})
        body (json/read-str (:body response) :key-fn keyword)]
    (is (= 401 (:status response)))
    (is (= {:success false :error "Authentication required"} body))))

(deftest wrap-optional-auth
  (let [token (#'auth/issue-token "admin")
        handler (auth/wrap-optional-auth (fn [request] {:status 200 :body (:identity request)}))
        response (handler {:headers {"authorization" (str "Bearer " token)}})]
    (is (= 200 (:status response)))
    (is (= "admin" (get-in response [:body :sub])))))

(deftest login-success-and-failure
  (testing "successful login updates last login and returns token"
    (let [updated (atom nil)
          result (with-redefs [admins/verify-password (fn [u p] (and (= u "admin") (= p "pw")))
                               admins/update-last-login (fn [u] (reset! updated u))]
                   (auth/login! "admin" "pw"))]
      (is (= "admin" @updated))
      (is (= true (:success result)))
      (is (= "admin" (:username result)))
      (is (string? (:token result)))))
  (testing "failed login returns error"
    (let [result (with-redefs [admins/verify-password (fn [_ _] false)
                               admins/update-last-login (fn [_] (throw (ex-info "should not call" {})))]
                   (auth/login! "admin" "bad"))]
      (is (= false (:success result)))
      (is (= "Invalid credentials" (:error result))))))

