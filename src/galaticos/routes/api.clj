(ns galaticos.routes.api
  "Main API router that combines all resource routes"
  (:require [compojure.core :refer [routes GET POST]]
            [galaticos.routes.players :refer [player-routes]]
            [galaticos.routes.championships :refer [championship-routes]]
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
   match-routes
   team-routes
   (GET "/api/aggregations/stats" request ((wrap-auth agg-handlers/dashboard-stats) request))
   (POST "/api/aggregations/stats/reconcile" request ((wrap-auth agg-handlers/reconcile-stats) request))
   (GET "/api/aggregations/players/stats/:championship-id" [championship-id :as request]
        ((wrap-auth agg-handlers/player-stats-by-championship) (assoc request :params {:championship-id championship-id})))
   (GET "/api/aggregations/positions/:championship-id" [championship-id :as request]
        ((wrap-auth agg-handlers/avg-goals-by-position) (assoc request :params {:championship-id championship-id})))
   (GET "/api/aggregations/players/:player-id/evolution" [player-id :as request]
        ((wrap-auth agg-handlers/player-performance-evolution) (assoc request :params {:player-id player-id})))
   (GET "/api/aggregations/players/search" request ((wrap-auth agg-handlers/search-players) request))
   (GET "/api/aggregations/championships/comparison" request ((wrap-auth agg-handlers/championship-comparison) request))
   (GET "/api/aggregations/players/top" request ((wrap-auth agg-handlers/top-players) request))
   (GET "/api/exports/dashboard.csv" request ((wrap-auth export-handlers/export-dashboard-csv) request))
   (GET "/api/exports/championships/:id.csv" [id :as request]
        ((wrap-auth export-handlers/export-championship-csv) (assoc request :params {:id id})))))

