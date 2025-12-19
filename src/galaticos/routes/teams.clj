(ns galaticos.routes.teams
  "API routes for team operations"
  (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
            [galaticos.handlers.teams :as handlers]
            [galaticos.middleware.auth :refer [wrap-auth]]))

(defroutes team-routes
  (GET "/api/teams" request ((wrap-auth handlers/list-teams) request))
  (GET "/api/teams/:id" [id :as request] ((wrap-auth handlers/get-team) (assoc request :params {:id id})))
  (POST "/api/teams" request ((wrap-auth handlers/create-team) request))
  (PUT "/api/teams/:id" [id :as request] ((wrap-auth handlers/update-team) (assoc request :params {:id id})))
  (DELETE "/api/teams/:id" [id :as request] ((wrap-auth handlers/delete-team) (assoc request :params {:id id})))
  (POST "/api/teams/:id/players/:player-id" [id player-id :as request]
        ((wrap-auth handlers/add-player-to-team) (assoc request :params {:id id :player-id player-id})))
  (DELETE "/api/teams/:id/players/:player-id" [id player-id :as request]
          ((wrap-auth handlers/remove-player-from-team) (assoc request :params {:id id :player-id player-id}))))

