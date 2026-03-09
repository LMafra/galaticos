(ns galaticos.middleware.errors
  "Error handling middleware"
  (:require [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]))

(defn wrap-errors
  "Catch exceptions and return appropriate error responses"
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (log/error e "Exception with data:" data)
          (if-let [status (:status data)]
            (resp/error (:message data) status)
            (resp/server-error "An error occurred"))))
      (catch Exception e
        (log/error e "Unhandled exception")
        (resp/server-error "Internal server error")))))

