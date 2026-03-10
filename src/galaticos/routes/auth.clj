(ns galaticos.routes.auth
  "API routes for authentication"
  (:require [compojure.core :refer [defroutes POST GET]]
            [galaticos.handlers.auth :as handlers]
            [galaticos.middleware.auth :as auth]))

(defroutes auth-routes
  (POST "/api/auth/login" [] handlers/login)
  (POST "/api/auth/logout" [] handlers/logout)
  ;; Ensure Authorization Bearer token is parsed (but still allow unauthenticated to return 401)
  (GET "/api/auth/check" request ((auth/wrap-optional-auth handlers/check-auth) request)))

