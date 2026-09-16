(ns galaticos.theme-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [galaticos.theme :as theme]))

(deftest normalize-preference-test
  (is (= "system" (theme/normalize-preference nil)))
  (is (= "system" (theme/normalize-preference "")))
  (is (= "system" (theme/normalize-preference "auto")))
  (is (= "light" (theme/normalize-preference "light")))
  (is (= "dark" (theme/normalize-preference "dark")))
  (is (= "system" (theme/normalize-preference "system"))))

(deftest resolve-theme-test
  (testing "explicit preference wins"
    (is (= "light" (theme/resolve-theme "light" true)))
    (is (= "dark" (theme/resolve-theme "dark" false))))
  (testing "system follows OS"
    (is (= "dark" (theme/resolve-theme "system" true)))
    (is (= "light" (theme/resolve-theme "system" false)))
    (is (= "dark" (theme/resolve-theme nil true)))
    (is (= "light" (theme/resolve-theme nil false)))))

(deftest opposite-preference-test
  (is (= "light" (theme/opposite-preference "dark" false)))
  (is (= "dark" (theme/opposite-preference "light" true)))
  (is (= "light" (theme/opposite-preference "system" true)))
  (is (= "dark" (theme/opposite-preference "system" false))))

(deftest dark-effective?-test
  (is (true? (theme/dark-effective? "dark" false)))
  (is (false? (theme/dark-effective? "light" true)))
  (is (true? (theme/dark-effective? "system" true)))
  (is (false? (theme/dark-effective? "system" false))))
