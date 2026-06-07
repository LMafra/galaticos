(ns galaticos.form-errors-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [galaticos.components.common :as common]))

(deftest field-errors-from-api-test
  (testing "maps message to known field"
    (is (= {:date "Missing required fields: date"}
           (common/field-errors-from-api "Missing required fields: date" [:date :opponent]))))
  (testing "falls back to _form when no field match"
    (is (= {:_form "Cannot create matches in a completed season"}
           (common/field-errors-from-api "Cannot create matches in a completed season" [:date])))))
