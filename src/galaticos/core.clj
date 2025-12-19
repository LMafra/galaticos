(ns galaticos.core
  "Main entry point for the Galáticos application"
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [galaticos.handler :refer [app]]
            [galaticos.db.core :as db]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log])
  (:gen-class))

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

(defn init-db!
  "Initialize database connection"
  []
  (log/info "Initializing database connection...")
  ;; #region agent log
  (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_dbinit\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"core.clj:13\",\"message\":\"Initializing database\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"B\"}"))
  ;; #endregion
  (let [result (db/connect!)]
    ;; #region agent log
    (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_dbresult\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"core.clj:15\",\"message\":\"Database connection result\",\"data\":{\"status\":\"" (name (:status result)) "\",\"message\":\"" (str (:message result)) "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"B\"}"))
    ;; #endregion
    (if (= :error (:status result))
      (do
        (log/error "Failed to connect to database:" (:message result))
        (System/exit 1))
      (log/info "Database connection established"))))

(defn shutdown-hook
  "Cleanup function called on shutdown"
  []
  (log/info "Shutting down application...")
  (db/disconnect!)
  (log/info "Application shutdown complete"))

(defn start-server
  "Start the Jetty server"
  [port]
  (log/info (format "Starting server on port %d..." port))
  ;; #region agent log
  (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_start\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"core.clj:32\",\"message\":\"Starting Jetty server\",\"data\":{\"port\":" port "},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}"))
  ;; #endregion
  (let [server (run-jetty app {:port port
                                :join? false})]
    ;; #region agent log
    (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_started\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"core.clj:34\",\"message\":\"Jetty server started\",\"data\":{\"port\":" port ",\"server\":\"" (str server) "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\"}"))
    ;; #endregion
    (log/info (format "Server started on http://localhost:%d" port))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. shutdown-hook))
    server))

(defn -main
  "Main entry point"
  [& _args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (init-db!)
    (start-server port)))

