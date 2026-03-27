(ns galaticos.routes.championships
  "API routes for championship operations"
  (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
            [galaticos.handlers.championships :as handlers]
            [galaticos.middleware.auth :refer [wrap-auth]]))

(defroutes championship-routes
  (GET "/api/championships" request (handlers/list-championships request))
  (GET "/api/championships/:id" [id :as request] (handlers/get-championship (assoc request :params {:id id})))
  (GET "/api/championships/:id/players" [id :as request]
       (handlers/get-championship-players (assoc request :params {:id id})))
  (POST "/api/championships" request ((wrap-auth handlers/create-championship) request))
  (PUT "/api/championships/:id" [id :as request] ((wrap-auth handlers/update-championship) (assoc request :params {:id id})))
  (POST "/api/championships/:id/finalize" [id :as request]
        ((wrap-auth handlers/finalize-championship) (assoc request :params {:id id})))
  (POST "/api/championships/:id/enroll/:player-id" [id player-id :as request]
        ((wrap-auth handlers/enroll-player) (assoc request :params {:id id :player-id player-id})))
  (DELETE "/api/championships/:id/unenroll/:player-id" [id player-id :as request]
          ((wrap-auth handlers/unenroll-player) (assoc request :params {:id id :player-id player-id})))
  (DELETE "/api/championships/:id" [id :as request] ((wrap-auth handlers/delete-championship) (assoc request :params {:id id}))))

