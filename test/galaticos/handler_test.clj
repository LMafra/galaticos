(ns galaticos.handler-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handler :as handler]
            [galaticos.db.core :as db])
  (:import [java.io ByteArrayInputStream]))

(deftest health-check
  (testing "when db connected"
    (let [response (with-redefs [db/connection (fn [] :conn)]
                    (handler/health-check {}))
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "ok" (:status body)))
      (is (= "galaticos" (:service body)))
      (is (= "connected" (get-in body [:dependencies :mongodb])))))
  (testing "when db disconnected"
    (let [response (with-redefs [db/connection (fn [] nil)]
                    (handler/health-check {}))
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "disconnected" (get-in body [:dependencies :mongodb])))))
  (testing "when db throws"
    (let [response (with-redefs [db/connection (fn [] (throw (RuntimeException. "db error")))]
                    (handler/health-check {}))
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "error" (get-in body [:dependencies :mongodb]))))))

(deftest serve-index
  (testing "returns HTML with content-type and cache headers"
    (let [response (handler/serve-index {})]
      (is (= 200 (:status response)))
      (is (str/includes? (get-in response [:headers "Content-Type"]) "text/html"))
      (is (get-in response [:headers "Cache-Control"]))
      (is (string? (:body response))))))

(deftest wrap-json-body
  (testing "valid JSON with Content-Type passes through"
    (let [h (handler/wrap-json-body (fn [req] (is (= {:a 1} (:json-body req))) {:status 200}))
          request {:request-method :post
                   :headers {"content-type" "application/json"}
                   :body "{\"a\":1}"}
          response (h request)]
      (is (= 200 (:status response)))))
  (testing "POST with body but no JSON Content-Type returns 415"
    (let [h (handler/wrap-json-body (fn [_] {:status 200}))
          request {:request-method :post
                   :headers {}
                   :body "plain"}
          response (h request)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 415 (:status response)))
      (is (str/includes? (:error body) "application/json"))))
  (testing "invalid JSON returns 400"
    (let [h (handler/wrap-json-body (fn [_] {:status 200}))
          request {:request-method :post
                   :headers {"content-type" "application/json"}
                   :body "{invalid}"}
          response (h request)]
      (is (= 400 (:status response)))))
  (testing "empty body does not fail"
    (let [h (handler/wrap-json-body (fn [req] (is (nil? (:json-body req))) {:status 200}))
          request {:request-method :get :body nil}
          response (h request)]
      (is (= 200 (:status response))))))

(deftest wrap-static-cache
  (testing "/js and /css: revalidate (bundles not fingerprinted in URL)"
    (let [h (handler/wrap-static-cache (fn [_] {:status 200 :headers {}}))
          response (h {:uri "/js/compiled/app.js"})]
      (is (str/includes? (or (get-in response [:headers "Cache-Control"]) "") "max-age=0"))))
  (testing "adds long cache for /images and /fonts"
    (let [h (handler/wrap-static-cache (fn [_] {:status 200 :headers {}}))
          response (h {:uri "/images/logo.png"})]
      (is (get-in response [:headers "Cache-Control"]))
      (is (get-in response [:headers "Expires"]))))
  (testing "does not add cache headers for non-static URI"
    (let [h (handler/wrap-static-cache (fn [_] {:status 200 :headers {}}))
          request {:uri "/api/players"}
          response (h request)]
      (is (nil? (get-in response [:headers "Cache-Control"]))))))

(deftest app
  (testing "GET /health"
    (let [request {:request-method :get :uri "/health" :headers {}}
          response (with-redefs [db/connection (fn [] nil)]
                    (handler/app request))
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 200 (:status response)))
      (is (= "ok" (:status body)))))
  (testing "GET / returns HTML"
    (let [request {:request-method :get :uri "/" :headers {}}
          response (handler/app request)]
      (is (= 200 (:status response)))
      (is (str/includes? (get-in response [:headers "Content-Type"] "") "text/html"))))
  (testing "GET /api/nonexistent returns 404"
    (let [request {:request-method :get :uri "/api/nonexistent" :headers {}}
          response (handler/app request)
          body (json/read-str (:body response) :key-fn keyword)]
      (is (= 404 (:status response)))
      (is (= "Not found" (:error body))))))
