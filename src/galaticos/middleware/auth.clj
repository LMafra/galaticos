(ns galaticos.middleware.auth
  "Authentication middleware for protecting routes"
  (:require [galaticos.util.response :as resp]
            [galaticos.db.admins :as admins]
            [ring.util.response :refer [get-header]]
            [buddy.sign.jwt :as jwt]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

(def ^:private dev-envs #{"dev" "development" "test" "testing"})

(defn- app-env []
  (or (env :app-env) (env :environment) (env :env) "dev"))

(defn- jwt-secret []
  (or (env :jwt-secret)
      (env :jwt_secret)
      (when (dev-envs (app-env))
        "dev-secret")))

(defn- token-ttl-seconds []
  (try
    (Long/parseLong (or (env :jwt-ttl-seconds) (env :jwt_ttl_seconds) "86400"))
    (catch Exception _ 86400)))

(defn- ensure-jwt-secret []
  (when-not (jwt-secret)
    (throw (ex-info "JWT_SECRET must be set in non-dev environments" {:status 500}))))

(def ^:private disable-auth?
  (= "true" (env :disable-auth)))

(def ^:private disable-auth-allowed?
  (dev-envs (app-env)))

(when (and disable-auth? (not disable-auth-allowed?))
  (throw (ex-info "DISABLE_AUTH is only allowed in dev/test environments"
                  {:env (app-env)})))

(when (and disable-auth? disable-auth-allowed?)
  (log/warn "Authentication is DISABLED for this environment; do not use in production."))

(defn- now-seconds []
  (quot (System/currentTimeMillis) 1000))

(defn- bearer-token [request]
  (when-let [auth-header (or (get-header request "authorization")
                             (get-in request [:headers "authorization"]))]
    (let [token (second (re-find #"(?i)^Bearer (.+)$" (str auth-header)))]
      (when (not (str/blank? token))
        token))))

(defn- issue-token [username]
  (ensure-jwt-secret)
  (let [issued (now-seconds)
        exp (+ issued (token-ttl-seconds))]
    (jwt/sign {:sub username
               :iat issued
               :exp exp}
              (jwt-secret)
              {:alg :hs256})))

(defn- verify-token [token]
  (try
    (jwt/unsign token (jwt-secret) {:alg :hs256})
    (catch Exception e
      (log/warn e "Invalid or expired token")
      nil)))

(defn authenticated?
  "Check if request is authenticated (token present and valid, or auth disabled)"
  [request]
  (or disable-auth?
      (some? (:identity request))))

(defn- identity->user [identity]
  (or (:sub identity) (:user identity) (:username identity)))

(defn current-user
  "Get authenticated username from request identity"
  [request]
  (some-> request :identity identity->user))

(defn wrap-auth
  "Middleware to require authentication via Bearer token unless DISABLE_AUTH is set in dev/test."
  [handler]
  (do
    (when-not disable-auth?
      (ensure-jwt-secret))
    (fn [request]
      (cond
        disable-auth?
        (handler (assoc request :identity {:bypass true}))

        :else
        (if-let [claims (some-> request bearer-token verify-token)]
          (handler (assoc request :identity claims))
          (resp/unauthorized "Authentication required"))))))

(defn wrap-auth-handler
  "Wrap a handler function with authentication"
  [handler-fn]
  (fn [request]
    (if (authenticated? request)
      (handler-fn request)
      (resp/unauthorized "Authentication required"))))

(defn wrap-optional-auth
  "Middleware that adds user to request if authenticated, but doesn't require it"
  [handler]
  (fn [request]
    (let [claims (some-> request bearer-token verify-token)]
      (handler (cond-> request
                 claims (assoc :identity claims))))))

(defn login!
  "Authenticate admin credentials and return a signed token on success."
  [username password]
  (if (admins/verify-password username password)
    (do
      (admins/update-last-login username)
      {:success true
       :username username
       :token (issue-token username)})
    {:success false :error "Invalid credentials"}))

(defn logout!
  "Stateless token auth has no server-side logout; provided for API parity."
  [_request]
  {:success true})

