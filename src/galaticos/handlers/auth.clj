(ns galaticos.handlers.auth
  "Request handlers for authentication"
  (:require [galaticos.middleware.auth :as auth]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]))

(defn login
  "Handle login request"
  [request]
  (try
    (let [body (:json-body request)
          username (:username body)
          password (:password body)]
      (if (and username password)
        (let [result (auth/login! username password)]
          (if (:success result)
            (resp/success {:username username
                           :token (:token result)})
            (resp/unauthorized "Invalid credentials")))
        (resp/error "Username and password required")))
    (catch Exception e
      (log/error e "Error during login")
      (resp/server-error "Failed to process login"))))

(defn logout
  "Handle logout request"
  [request]
  (try
    (auth/logout! request)
    (resp/success {:message "Logged out"})
    (catch Exception e
      (log/error e "Error during logout")
      (resp/server-error "Failed to process logout"))))

(defn check-auth
  "Check if user is authenticated. Always returns 200 so clients and browsers do not log a failed resource for a normal 'not logged in' or invalid-token probe."
  [request]
  (if (auth/authenticated? request)
    (resp/success {:authenticated true
                   :user (or (some-> (auth/current-user request) str not-empty)
                             (when (auth/authentication-disabled?) "dev"))})
    (resp/success {:authenticated false})))

