(ns galaticos.middleware.cors
  "CORS middleware for allowing frontend access"
  (:require [clojure.string :as str]
            [environ.core :refer [env]]))

(def ^:private default-origins
  #{"http://localhost:3000" "http://localhost:5173"})

(defn- parse-allowed-origins []
  (let [env-origins (or (env :cors-allow-origins) (env :cors_allow_origins))]
    (if (and env-origins (not (str/blank? env-origins)))
      (set (map str/trim (str/split env-origins #",")))
      default-origins)))

(defn- allowed-origin [request]
  (let [origin (get-in request [:headers "origin"])
        origins (parse-allowed-origins)]
    (cond
      (and origin (origins origin)) origin
      (seq origins) (first origins)
      :else "*")))

(defn- cors-headers [request]
  {"Access-Control-Allow-Origin" (allowed-origin request)
   "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS, PATCH"
   "Access-Control-Allow-Headers" "Content-Type, Authorization"
   "Access-Control-Expose-Headers" "Authorization"
   "Access-Control-Max-Age" "3600"})

(defn wrap-cors
  "Add CORS headers and handle OPTIONS preflight"
  [handler]
  (fn [request]
    (let [headers (cors-headers request)]
      (if (= :options (:request-method request))
        {:status 204 :headers headers :body ""}
        (let [response (handler request)]
          (if (map? response)
            (update response :headers #(merge headers (or % {})))
            response))))))

