(ns galaticos.routes.api
  "Main API router that combines all resource routes"
  (:require [compojure.core :refer [routes GET]]
            [galaticos.routes.players :refer [player-routes]]
            [galaticos.routes.championships :refer [championship-routes]]
            [galaticos.routes.seasons :refer [season-routes]]
            [galaticos.routes.matches :refer [match-routes]]
            [galaticos.routes.teams :refer [team-routes]]
            [galaticos.routes.auth :refer [auth-routes]]
            [galaticos.handlers.aggregations :as agg-handlers]
            [galaticos.handlers.exports :as export-handlers]
            [galaticos.middleware.auth :refer [wrap-auth]]))

(def api-routes
  (routes
   auth-routes
   player-routes
   championship-routes
   season-routes
   match-routes
   team-routes
  (GET "/api/aggregations/stats" request (agg-handlers/dashboard-stats request))
   (GET "/api/aggregations/players/stats/:championship-id" [championship-id :as request]
       (agg-handlers/player-stats-by-championship (assoc request :params {:championship-id championship-id})))
   (GET "/api/aggregations/championships/:id/leaderboards" [id :as request]
        (agg-handlers/championship-table-leaderboards (assoc request :params {:id id})))
   (GET "/api/aggregations/positions/:championship-id" [championship-id :as request]
       (agg-handlers/avg-goals-by-position (assoc request :params {:championship-id championship-id})))
   (GET "/api/aggregations/players/:player-id/evolution" [player-id :as request]
       (agg-handlers/player-performance-evolution (assoc request :params {:player-id player-id})))
  (GET "/api/aggregations/players/search" request (agg-handlers/search-players request))
  (GET "/api/aggregations/championships/comparison" request (agg-handlers/championship-comparison request))
  (GET "/api/aggregations/players/top" request (agg-handlers/top-players request))
   (GET "/api/exports/dashboard.csv" request ((wrap-auth export-handlers/export-dashboard-csv) request))
   (GET "/api/exports/championships/:id.csv" [id :as request]
        ((wrap-auth export-handlers/export-championship-csv) (assoc request :params {:id id})))))

