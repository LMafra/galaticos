(ns galaticos.middleware.errors
  "Error handling middleware"
  (:require [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]))

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

(defn wrap-errors
  "Catch exceptions and return appropriate error responses"
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          ;; #region agent log
          (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_exinfo\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"errors.clj:13\",\"message\":\"ExceptionInfo caught\",\"data\":{\"message\":\"" (.getMessage e) "\",\"status\":" (get data :status "nil") "},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\"}"))
          ;; #endregion
          (log/error e "Exception with data:" data)
          (if-let [status (:status data)]
            (resp/error (:message data) status)
            (resp/server-error "An error occurred"))))
      (catch Exception e
        ;; #region agent log
        (write-debug-log (str "{\"id\":\"log_" (System/currentTimeMillis) "_exception\",\"timestamp\":" (System/currentTimeMillis) ",\"location\":\"errors.clj:19\",\"message\":\"Unhandled exception\",\"data\":{\"class\":\"" (.getName (class e)) "\",\"message\":\"" (.getMessage e) "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\"}"))
        ;; #endregion
        (log/error e "Unhandled exception")
        (resp/server-error "Internal server error")))))

