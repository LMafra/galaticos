(ns galaticos.routes.championships
  "API routes for championship operations"
  (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
            [galaticos.handlers.championships :as handlers]
            [galaticos.middleware.auth :refer [wrap-auth]]))

(defroutes championship-routes
  (GET "/api/championships" request ((wrap-auth handlers/list-championships) request))
  (GET "/api/championships/:id" [id :as request] ((wrap-auth handlers/get-championship) (assoc request :params {:id id})))
  (POST "/api/championships" request ((wrap-auth handlers/create-championship) request))
  (PUT "/api/championships/:id" [id :as request] ((wrap-auth handlers/update-championship) (assoc request :params {:id id})))
  (DELETE "/api/championships/:id" [id :as request] ((wrap-auth handlers/delete-championship) (assoc request :params {:id id}))))

