(ns galaticos.routes.matches
  "API routes for match operations"
  (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
            [galaticos.handlers.matches :as handlers]
            [galaticos.middleware.auth :refer [wrap-auth]]))

(defroutes match-routes
  (GET "/api/matches" request (handlers/list-matches request))
  (GET "/api/matches/:id" [id :as request] (handlers/get-match (assoc request :params {:id id})))
  (POST "/api/matches" request ((wrap-auth handlers/create-match) request))
  (PUT "/api/matches/:id" [id :as request] ((wrap-auth handlers/update-match) (assoc request :params {:id id})))
  (DELETE "/api/matches/:id" [id :as request] ((wrap-auth handlers/delete-match) (assoc request :params {:id id}))))

