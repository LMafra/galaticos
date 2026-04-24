(ns galaticos.handler
  "Ring handler for the Galáticos application"
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [galaticos.middleware.gzip :refer [wrap-gzip]]
            [ring.util.response :refer [response content-type]]
            [galaticos.routes.api :refer [api-routes]]
            [galaticos.middleware.cors :refer [wrap-cors]]
            [galaticos.middleware.errors :refer [wrap-errors]]
            [galaticos.middleware.request-id :refer [wrap-request-id]]
            [galaticos.util.response :as resp]
            [galaticos.db.core :as db]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def ^:private app-version "1.0.0")

(defn health-check
  "Health check endpoint for Docker and monitoring"
  [_request]
  (let [mongodb-status (try (if (db/connection) "connected" "disconnected")
                           (catch Exception _ "error"))]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str {:status "ok"
                            :service "galaticos"
                            :version app-version
                            :timestamp (str (java.util.Date.))
                            :dependencies {:mongodb mongodb-status}})}))

(defn serve-index
  "Serve the frontend index.html"
  [_request]
  (let [resource-url (io/resource "templates/index.html")]
    (if resource-url
      (let [html-content (slurp resource-url)]
        (-> (response html-content)
            (content-type "text/html; charset=utf-8")
            (assoc-in [:headers "Cache-Control"] "no-cache, no-store, must-revalidate")
            (assoc-in [:headers "Pragma"] "no-cache")
            (assoc-in [:headers "Expires"] "0")))
      (-> (response "<!DOCTYPE html><html><head><title>Galáticos</title></head><body><div id=\"app\"></div><script src=\"/js/compiled/app.js\"></script></body></html>")
          (content-type "text/html; charset=utf-8")
          (assoc-in [:headers "Cache-Control"] "no-cache, no-store, must-revalidate")
          (assoc-in [:headers "Pragma"] "no-cache")
          (assoc-in [:headers "Expires"] "0")))))

(defroutes app-routes
  ;; Health check
  (GET "/health" [] health-check)
  
  ;; API routes
  api-routes
  
  ;; Static files - serve from resources/public or resources
  ;; This must come before the catch-all route
  (route/resources "/")
  
  ;; Frontend routes - serve index.html for all non-API, non-static routes
  (GET "/" [] serve-index)
  ;; Catch-all for SPA routes, but exclude static file paths
  ;; If a static file path is requested but doesn't exist, return 404 with proper content type
  (GET "*" {uri :uri :as request}
       (cond
         (.startsWith uri "/api/")
         {:status 404
          :headers {"Content-Type" "application/json"}
          :body (json/write-str {:error "Not found"})}
         (or (.startsWith uri "/js/")
             (.startsWith uri "/css/")
             (.startsWith uri "/images/")
             (.startsWith uri "/fonts/"))
         {:status 404
          :headers {"Content-Type" "text/plain"}
          :body "Not found"}
         (.startsWith uri "/health")
         {:status 404
          :headers {"Content-Type" "application/json"}
          :body (json/write-str {:error "Not found"})}
         :else
         (serve-index request)))
  
  ;; 404 handler
  (route/not-found {:status 404
                    :headers {"Content-Type" "application/json"}
                    :body (json/write-str {:error "Not found"})}))

(defn wrap-json-body
  "Middleware to parse JSON request bodies once and attach as :json-body.
   Returns 415 when a POST/PUT/PATCH has a body but no JSON Content-Type.
   Returns 400 when JSON is malformed."
  [handler]
  (fn [request]
    (let [method (:request-method request)
          body (:body request)
          content-type (some-> (get-in request [:headers "content-type"])
                               str/lower-case)
          json? (and content-type (str/includes? content-type "application/json"))
          method-requires-json? (contains? #{:post :put :patch} method)
          has-body? (some? body)]
      (cond
        ;; Reject non-JSON bodies for mutating methods
        (and method-requires-json? has-body? (not json?))
        (resp/error "Unsupported Media Type: application/json required" 415)

        :else
        (try
          ;; Only parse JSON if Content-Type explicitly indicates JSON, or if body is already a map
          ;; Don't try to parse InputStream/string bodies unless Content-Type says it's JSON
          (let [parsed (when (and has-body?
                               (or json?
                                   (map? body)))
                         (resp/parse-json-body request))]
            (handler (cond-> request
                       parsed (assoc :json-body parsed))))
          (catch Exception e
            (if (= 400 (-> e ex-data :status))
              (resp/error "Invalid JSON payload" 400)
              (resp/error "Invalid request body" 400))))))))

(defn wrap-static-cache
  "Cache headers for static assets.
  `/js` e `/css`: `max-age=0, must-revalidate` (bundles share stable URLs; evita browser
  ficar com JS/CSS antigo por 1 ano). `/images` e `/fonts`: cache longo."
  [handler]
  (fn [request]
    (let [response (handler request)
          uri (:uri request)
          static-asset? (or (.startsWith uri "/js/") (.startsWith uri "/css/")
                            (.startsWith uri "/images/") (.startsWith uri "/fonts/"))]
      (if-not (and (map? response) static-asset?)
        response
        (cond
          (or (.startsWith uri "/js/") (.startsWith uri "/css/"))
          (assoc-in response [:headers "Cache-Control"] "public, max-age=0, must-revalidate")

          :else
          (-> response
              (assoc-in [:headers "Cache-Control"] "public, max-age=31536000")
              (assoc-in [:headers "Expires"] (str (java.util.Date. (+ (System/currentTimeMillis) (* 365 24 60 60 1000)))))))))))

(def app
  "Main application handler with middleware stack"
  ;; wrap-gzip last so it sees the final response (incl. from site-defaults).
  (let [wrapped (-> app-routes
                    wrap-request-id
                    wrap-errors
                    wrap-cors
                    wrap-json-body
                    wrap-static-cache
                    (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
                    wrap-gzip)]
    (fn [request]
      (wrapped request))))

