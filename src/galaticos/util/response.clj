(ns galaticos.util.response
  "Utilities for creating standardized HTTP responses"
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [ring.util.response :refer [response status content-type]])
  (:import [org.bson.types ObjectId]
           [java.util Date]
           [java.time.format DateTimeFormatter]))

(def ^:private iso-formatter
  DateTimeFormatter/ISO_INSTANT)

(defn prepare-for-json
  "Recursively convert ObjectId instances and Date objects to strings in data structures"
  [data]
  (cond
    (instance? ObjectId data)
    (str data)
    
    (instance? Date data)
    (.format iso-formatter (.toInstant ^Date data))
    
    (map? data)
    (into {} (for [[k v] data]
               [k (prepare-for-json v)]))
    
    (sequential? data)
    (mapv prepare-for-json data)
    
    :else
    data))

(defn json-response
  "Create a JSON response with the given data and status code"
  [data status-code]
  (-> (response (json/write-str (prepare-for-json data)))
      (status status-code)
      (content-type "application/json; charset=utf-8")))

(defn success
  "Create a success response with data"
  ([data]
   (json-response {:success true :data data} 200))
  ([data status-code]
   (json-response {:success true :data data} status-code)))

(defn error
  "Create an error response"
  ([message]
   (json-response {:success false :error message} 400))
  ([message status-code]
   (json-response {:success false :error message} status-code)))

(defn not-found
  "Create a 404 not found response"
  [message]
  (error message 404))

(defn unauthorized
  "Create a 401 unauthorized response"
  [message]
  (error message 401))

(defn forbidden
  "Create a 403 forbidden response"
  [message]
  (error message 403))

(defn server-error
  "Create a 500 server error response"
  [message]
  (error message 500))

(defn parse-json-body
  "Parse JSON request body. Accepts pre-parsed :json-body (set by middleware),
  Clojure maps, strings, or InputStreams. Returns a map or {} when body is
  empty. Throws ex-info with {:status 400} on malformed JSON."
  [request]
  (let [from-middleware (:json-body request)
        body (:body request)
        content-type (some-> (get-in request [:headers "content-type"])
                             str/lower-case)]
    (try
      (cond
        (map? from-middleware) from-middleware
        (and (map? body) (not (instance? java.io.InputStream body))) body
        (nil? body) {}
        (string? body)
        (let [trimmed (str/trim body)]
          (if (empty? trimmed)
            {}
            (json/read-str trimmed :key-fn keyword)))
        (instance? java.io.InputStream body)
        (let [raw (slurp body)
              trimmed (str/trim raw)]
          (if (empty? trimmed)
            {}
            (json/read-str trimmed :key-fn keyword)))
        :else
        (if (and content-type (str/includes? content-type "application/json"))
          (throw (ex-info "Unsupported JSON body type" {:status 400}))
          {}))
      (catch Exception e
        (throw (ex-info "Invalid JSON payload" {:status 400} e))))))

(defn ->object-id
  "Coerce a value to ObjectId or throw ex-info {:status 400} when invalid."
  [value]
  (cond
    (instance? ObjectId value) value
    (nil? value) (throw (ex-info "Missing id" {:status 400}))
    :else
    (try
      (ObjectId. (str value))
      (catch Exception e
        (throw (ex-info "Invalid id format" {:status 400 :value value} e))))))

