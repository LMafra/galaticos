(ns galaticos.util.string-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.util.string :as str-util]))

(deftest normalize-text
  (testing "returns nil for nil input"
    (is (nil? (str-util/normalize-text nil))))
  (testing "normalizes empty string"
    (is (= "" (str-util/normalize-text ""))))
  (testing "lowercases and trims"
    (is (= "hello" (str-util/normalize-text "  HELLO  "))))
  (testing "removes accents"
    (is (= "jose" (str-util/normalize-text "José")))
    (is (= "cafe" (str-util/normalize-text "Café")))
    (is (= "nao" (str-util/normalize-text "Não"))))
  (testing "non-string input"
    (is (= "123" (str-util/normalize-text 123)))))

(deftest blank-normalized?
  (testing "nil and blank strings"
    (is (true? (str-util/blank-normalized? nil)))
    (is (true? (str-util/blank-normalized? "")))
    (is (true? (str-util/blank-normalized? "   "))))
  (testing "non-blank"
    (is (false? (str-util/blank-normalized? "hello")))
    (is (false? (str-util/blank-normalized? "  x  ")))))
