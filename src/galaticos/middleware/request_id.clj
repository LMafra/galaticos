(ns galaticos.middleware.request-id
  "Middleware to add a unique request-id (UUID) to each request, MDC, and response header."
  (:require [ring.util.response :refer [header]])
  (:import [java.util UUID]
           [org.slf4j MDC]))

(defn wrap-request-id
  "Add :request-id (UUID) to request, put it in MDC for logging, and add X-Request-Id to response.
   Clears MDC after the request to avoid leaking between requests."
  [handler]
  (fn [request]
    (let [request-id (str (UUID/randomUUID))
          request'   (assoc request :request-id request-id)]
      (try
        (MDC/put "request_id" request-id)
        (let [response (handler request')]
          (if (map? response)
            (header response "X-Request-Id" request-id)
            response))
        (finally
          (MDC/clear)))))) 
