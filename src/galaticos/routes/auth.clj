(ns galaticos.routes.auth
  "API routes for authentication"
  (:require [compojure.core :refer [defroutes POST GET]]
            [galaticos.handlers.auth :as handlers]))

(defroutes auth-routes
  (POST "/api/auth/login" [] handlers/login)
  (POST "/api/auth/logout" [] handlers/logout)
  (GET "/api/auth/check" [] handlers/check-auth))

