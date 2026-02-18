(ns galaticos.util.response-test
   (:require [clojure.data.json :as json]
             [clojure.test :refer [deftest is testing]]
             [galaticos.util.response :as resp])
   (:import [java.io ByteArrayInputStream]
            [java.util Date]
            [org.bson.types ObjectId]))
 
(deftest prepare-for-json-recursive
   (let [id (ObjectId.)
         date (Date. 0)
         input {:id id
                :created-at date
                :nested {:ids [id]
                         :dates [date]}}
         result (resp/prepare-for-json input)]
     (is (= (str id) (:id result)))
     (is (= "1970-01-01T00:00:00Z" (:created-at result)))
     (is (= [(str id)] (get-in result [:nested :ids])))
     (is (= ["1970-01-01T00:00:00Z"] (get-in result [:nested :dates])))))
 
(deftest json-response-helpers
   (testing "json-response sets status and content-type"
     (let [response (resp/json-response {:ok true} 201)
           body (json/read-str (:body response) :key-fn keyword)]
       (is (= 201 (:status response)))
       (is (= "application/json; charset=utf-8" (get-in response [:headers "Content-Type"])))
       (is (= {:ok true} body))))
   (testing "success and error helpers"
     (let [success (resp/success {:id 1})
           error (resp/error "bad" 422)]
       (is (= 200 (:status success)))
       (is (= 422 (:status error)))
       (is (= {:success true :data {:id 1}}
              (json/read-str (:body success) :key-fn keyword)))
       (is (= {:success false :error "bad"}
              (json/read-str (:body error) :key-fn keyword)))))
   (testing "status helpers"
     (is (= 404 (:status (resp/not-found "missing"))))
     (is (= 401 (:status (resp/unauthorized "nope"))))
     (is (= 403 (:status (resp/forbidden "denied"))))
     (is (= 500 (:status (resp/server-error "boom"))))))
 
(deftest parse-json-body-variants
  (testing "uses :json-body when provided"
    (is (= {:a 1} (resp/parse-json-body {:json-body {:a 1}}))))
  (testing "map body is returned as-is"
    (is (= {:b 2} (resp/parse-json-body {:body {:b 2}}))))
  (testing "nil or empty body returns {}"
    (is (= {} (resp/parse-json-body {})))
    (is (= {} (resp/parse-json-body {:body ""})))
    (is (= {} (resp/parse-json-body {:body "   "}))))
  (testing "string or stream body is parsed"
    (is (= {:c 3} (resp/parse-json-body {:body "{\"c\":3}"})))
    (let [stream (ByteArrayInputStream. (.getBytes "{\"d\":4}"))]
      (is (= {:d 4} (resp/parse-json-body {:body stream})))))
  (testing "invalid JSON throws ex-info with status 400"
    (try
      (resp/parse-json-body {:body "{not-json}"})
      (is false "Expected exception")
      (catch clojure.lang.ExceptionInfo e
        (is (= 400 (:status (ex-data e))))))))
 
(deftest object-id-coercion
   (let [id (ObjectId.)]
     (is (= id (resp/->object-id id)))
     (is (instance? ObjectId (resp/->object-id (str id)))))
   (try
     (resp/->object-id nil)
     (is false "Expected exception for nil")
     (catch clojure.lang.ExceptionInfo e
       (is (= 400 (:status (ex-data e))))))
   (try
     (resp/->object-id "invalid-id")
     (is false "Expected exception for invalid id")
     (catch clojure.lang.ExceptionInfo e
       (is (= 400 (:status (ex-data e))))
       (is (= "invalid-id" (:value (ex-data e)))))))

