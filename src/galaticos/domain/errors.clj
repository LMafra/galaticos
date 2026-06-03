(ns galaticos.domain.errors
  "Domain exceptions with ex-data for HTTP mapping via wrap-errors.")

(defn not-found!
  [message]
  (throw (ex-info message {:status 404 :message message :code :not-found})))

(defn conflict!
  [message]
  (throw (ex-info message {:status 409 :message message :code :conflict})))

(defn validation!
  [message]
  (throw (ex-info message {:status 400 :message message :code :validation})))

(defn forbidden!
  [message]
  (throw (ex-info message {:status 403 :message message :code :forbidden})))
