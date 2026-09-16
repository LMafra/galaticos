(ns galaticos.breadcrumbs-test
  (:require [cljs.test :refer-macros [deftest is]]
            [galaticos.breadcrumbs :as bc]))

(deftest label-for-test
  (is (= "Campeonatos" (bc/label-for :championships)))
  (is (= "Jogadores" (bc/label-for :players))))

(deftest championship-detail-crumbs
  (let [items (bc/build-breadcrumbs :championship-detail {:id "c1"}
                                    :entity-label "Paulistão")]
    (is (= 2 (count items)))
    (is (= :championships (:route (first items))))
    (is (= "Paulistão" (:label (second items))))
    (is (nil? (:route (second items))))))

(deftest championship-season-crumbs
  (let [items (bc/build-breadcrumbs :championship-season-detail
                                    {:id "c1" :season-id "s1"}
                                    :championship-label "Paulistão"
                                    :season-label "2026")]
    (is (= 3 (count items)))
    (is (= {:label "Campeonatos" :route :championships} (first items)))
    (is (= :championship-detail (:route (second items))))
    (is (= {:id "c1"} (:route-params (second items))))
    (is (= "2026" (:label (nth items 2))))
    (is (nil? (:route (nth items 2))))))

(deftest player-edit-crumbs
  (let [items (bc/build-breadcrumbs :player-edit {:id "p1"}
                                    :entity-label "João")]
    (is (= 3 (count items)))
    (is (= :players (:route (first items))))
    (is (= :player-detail (:route (second items))))
    (is (= "Editar" (:label (nth items 2))))))

(deftest player-detail-crumbs
  (is (= ["Jogadores" "Maria"]
         (mapv :label (bc/build-breadcrumbs :player-detail {:id "p1"}
                                            :entity-label "Maria")))))

(deftest match-hub-crumbs
  (let [items (bc/build-breadcrumbs :matches-by-championship
                                    {:championship-id "c1"}
                                    :entity-label "Copa")]
    (is (= 3 (count items)))
    (is (= "Copa" (:label (last items))))))

(deftest unknown-route-empty
  (is (= [] (bc/build-breadcrumbs :dashboard {}))))
