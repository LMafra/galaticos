(ns galaticos.routes.seasons
  "API routes for season operations"
  (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
            [galaticos.handlers.seasons :as handlers]
            [galaticos.middleware.auth :refer [wrap-auth]]))

(defroutes season-routes
  (GET "/api/championships/:id/seasons" [id :as request]
       (handlers/list-seasons (assoc request :params {:id id})))

  (GET "/api/seasons/:id" [id :as request]
       (handlers/get-season (assoc request :params {:id id})))

  (POST "/api/championships/:id/seasons" [id :as request]
        ((wrap-auth handlers/create-season) (assoc request :params {:id id})))

  (PUT "/api/seasons/:id" [id :as request]
       ((wrap-auth handlers/update-season) (assoc request :params {:id id})))

  (DELETE "/api/seasons/:id" [id :as request]
          ((wrap-auth handlers/delete-season) (assoc request :params {:id id})))

  (POST "/api/seasons/:id/activate" [id :as request]
        ((wrap-auth handlers/activate-season) (assoc request :params {:id id})))

  (POST "/api/seasons/:id/enroll/:player-id" [id player-id :as request]
        ((wrap-auth handlers/enroll-player) (assoc request :params {:id id :player-id player-id})))

  (DELETE "/api/seasons/:id/unenroll/:player-id" [id player-id :as request]
          ((wrap-auth handlers/unenroll-player) (assoc request :params {:id id :player-id player-id})))

  (GET "/api/seasons/:id/players" [id :as request]
       (handlers/get-season-players (assoc request :params {:id id})))

  (POST "/api/seasons/:id/finalize" [id :as request]
        ((wrap-auth handlers/finalize-season) (assoc request :params {:id id}))))

