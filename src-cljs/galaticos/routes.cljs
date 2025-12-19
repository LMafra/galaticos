(ns galaticos.routes
  "Frontend routing configuration"
  (:require [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(def routes
  [["/login" {:name :login
              :title "Login"}]
   ["/" {:name :dashboard
         :title "Dashboard"}]
   ["/players" {:name :players
                :title "Players"}]
   ["/players/:id" {:name :player-detail
                     :title "Player Details"}]
   ["/matches" {:name :matches
                :title "Matches"}]
   ["/matches/new" {:name :match-new
                    :title "New Match"}]
   ["/championships" {:name :championships
                      :title "Championships"}]
   ["/championships/:id" {:name :championship-detail
                          :title "Championship Details"}]])

(def router
  (rf/router routes))

(defn match-by-path
  "Match route by path"
  [path]
  (rf/match-by-path router path))

(defn href
  "Generate href for route"
  [route-name & params]
  (apply rfe/href route-name params))

