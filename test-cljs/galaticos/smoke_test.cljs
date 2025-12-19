(ns galaticos.smoke-test
  (:require [cljs.test :refer-macros [deftest is testing]]))

(deftest smoke-test
  (testing "basic truthiness"
    (is (= 1 1))))

