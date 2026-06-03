(ns galaticos.domain.teams-test
  (:require [clojure.test :refer [deftest is]]
            [galaticos.domain.teams :as domain]))

(deftest can-delete
  (is (= :not-found (get-in (domain/can-delete? false false) [:error :type])))
  (is (= :conflict (get-in (domain/can-delete? true true) [:error :type])))
  (is (= {:ok true} (domain/can-delete? true false))))
