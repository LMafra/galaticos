(ns galaticos.routes.players
  "API routes for player operations"
  (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
            [galaticos.handlers.players :as handlers]
            [galaticos.middleware.auth :refer [wrap-auth]]))

(defroutes player-routes
  (GET "/api/players" request (handlers/list-players request))
  (GET "/api/players/:id/detail" [id :as request] (handlers/get-player-detail-bundle (assoc request :params {:id id})))
  (GET "/api/players/:id" [id :as request] (handlers/get-player (assoc request :params {:id id})))
  (POST "/api/players" request ((wrap-auth handlers/create-player) request))
  (PUT "/api/players/:id" [id :as request] ((wrap-auth handlers/update-player) (assoc request :params {:id id})))
  (DELETE "/api/players/:id" [id :as request] ((wrap-auth handlers/delete-player) (assoc request :params {:id id}))))

