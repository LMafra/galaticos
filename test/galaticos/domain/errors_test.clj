(ns galaticos.domain.errors-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.domain.errors :as errors]))

(deftest domain-errors-ex-data
  (testing "not-found!"
    (try
      (errors/not-found! "missing")
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:status 404 :message "missing" :code :not-found} (ex-data e))))))
  (testing "conflict!"
    (try
      (errors/conflict! "duplicate")
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:status 409 :message "duplicate" :code :conflict} (ex-data e))))))
  (testing "validation!"
    (try
      (errors/validation! "bad input")
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:status 400 :message "bad input" :code :validation} (ex-data e))))))
  (testing "forbidden!"
    (try
      (errors/forbidden! "denied")
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:status 403 :message "denied" :code :forbidden} (ex-data e)))))))
