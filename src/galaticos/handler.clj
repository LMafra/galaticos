(ns galaticos.handler
  "Ring handler for the Galáticos application"
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :refer [response content-type]]
            [galaticos.routes.api :refer [api-routes]]
            [galaticos.middleware.cors :refer [wrap-cors]]
            [galaticos.middleware.errors :refer [wrap-errors]]
            [galaticos.util.response :as resp]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- ensure-log-dir! []
  "Ensure the log directory exists"
  (let [log-dir (java.io.File. "/app/.cursor")]
    (when-not (.exists log-dir)
      (.mkdirs log-dir))))

(defn- write-debug-log [log-entry]
  "Write a debug log entry"
  (try
    (ensure-log-dir!)
    (let [log-file (java.io.FileWriter. "/app/.cursor/debug.log" true)]
      (.write log-file (str log-entry "\n"))
      (.close log-file))
    (catch Exception _ nil)))

(defn health-check
  "Health check endpoint for Docker and monitoring"
  [_request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str {:status "ok"
                          :service "galaticos"
                          :timestamp (str (java.util.Date.))})})

(defn serve-index
  "Serve the frontend index.html"
  [_request]
  ;; #region agent log
  (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_serve\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"handler.clj:26\",\"message\":\"serve-index called\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}"))
  ;; #endregion
  (let [resource-url (io/resource "templates/index.html")]
    ;; #region agent log
    (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_resource\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"handler.clj:28\",\"message\":\"Resource check\",\"data\":{\"has-resource\":" (if resource-url "true" "false") "},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}"))
    ;; #endregion
    (if resource-url
      (let [html-content (slurp resource-url)]
        ;; #region agent log
        (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_html\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"handler.clj:30\",\"message\":\"HTML content loaded\",\"data\":{\"content-length\":" (count html-content) "},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}"))
        ;; #endregion
        (-> (response html-content)
            (content-type "text/html; charset=utf-8")
            (assoc-in [:headers "Cache-Control"] "no-cache, no-store, must-revalidate")
            (assoc-in [:headers "Pragma"] "no-cache")
            (assoc-in [:headers "Expires"] "0")))
      (do
        ;; #region agent log
        (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_fallback\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"handler.clj:35\",\"message\":\"Using fallback HTML\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}"))
        ;; #endregion
        (-> (response "<!DOCTYPE html><html><head><title>Galáticos</title></head><body><div id=\"app\"></div><script src=\"/js/compiled/app.js\"></script></body></html>")
            (content-type "text/html; charset=utf-8")
            (assoc-in [:headers "Cache-Control"] "no-cache, no-store, must-revalidate")
            (assoc-in [:headers "Pragma"] "no-cache")
            (assoc-in [:headers "Expires"] "0"))))))

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
      ;; #region agent log
      (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_json\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"handler.clj:87\",\"message\":\"wrap-json-body\",\"data\":{\"method\":\"" (name method) "\",\"has-body\":" has-body? ",\"json?\":" json? "},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\"}"))
      ;; #endregion
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
            ;; #region agent log
            (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_jsonerr\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"handler.clj:105\",\"message\":\"JSON parsing exception\",\"data\":{\"class\":\"" (.getName (class e)) "\",\"message\":\"" (.getMessage e) "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\"}"))
            ;; #endregion
            (if (= 400 (-> e ex-data :status))
              (resp/error "Invalid JSON payload" 400)
              (resp/error "Invalid request body" 400))))))))

(defn wrap-static-cache
  "Middleware to add cache headers for static assets"
  [handler]
  (fn [request]
    (let [response (handler request)
          uri (:uri request)]
      (if (and (map? response)
               (or (.startsWith uri "/js/")
                   (.startsWith uri "/css/")
                   (.startsWith uri "/images/")
                   (.startsWith uri "/fonts/")))
        (-> response
            (assoc-in [:headers "Cache-Control"] "public, max-age=31536000")
            (assoc-in [:headers "Expires"] (str (java.util.Date. (+ (System/currentTimeMillis) (* 365 24 60 60 1000))))))
        response))))

(def app
  "Main application handler with middleware stack"
  (let [wrapped (-> app-routes
                    wrap-errors
                    wrap-cors
                    wrap-json-body
                    wrap-static-cache
                    (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false)))]
    (fn [request]
      ;; #region agent log
      (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_req\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"handler.clj:133\",\"message\":\"Request received\",\"data\":{\"method\":\"" (name (:request-method request)) "\",\"uri\":\"" (:uri request) "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A,C\"}"))
      ;; #endregion
      (let [response (wrapped request)]
        ;; #region agent log
        (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_resp\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"handler.clj:140\",\"message\":\"Response generated\",\"data\":{\"status\":" (get response :status "nil") ",\"has-body\":" (if (:body response) "true" "false") "},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A,C\"}"))
        ;; #endregion
        response))))

