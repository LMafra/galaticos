(ns galaticos.db.core
  "MongoDB connection and database configuration using Monger"
  (:require [monger.core :as mg]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

(defonce ^:private conn (atom nil))
(defonce ^:private db-instance (atom nil))

(defn connect!
  "Connect to MongoDB using URI from environment or config.
   URI format: mongodb://host:port/database"
  ([]
   (let [uri (or (env :database-url) "mongodb://localhost:27017/galaticos")
         db-name (or (env :database-name) "galaticos")]
     (connect! uri db-name)))
  ([uri db-name]
   (try
     (log/info "Connecting to MongoDB:" uri)
     (let [result (mg/connect-via-uri uri)
           connection (:conn result)
           database (:db result)]
       (reset! conn connection)
       (reset! db-instance database)
       (log/info "Connected to MongoDB database:" db-name)
       {:status :connected :db-name db-name})
     (catch Exception e
       (log/error e "Failed to connect to MongoDB")
       {:status :error :message (.getMessage e)}))))

(defn disconnect!
  "Disconnect from MongoDB"
  []
  (when @conn
    (try
      (mg/disconnect @conn)
      (reset! conn nil)
      (reset! db-instance nil)
      (log/info "Disconnected from MongoDB")
      {:status :disconnected}
      (catch Exception e
        (log/error e "Error disconnecting from MongoDB")
        {:status :error :message (.getMessage e)}))))

(defn db
  "Get the database instance"
  []
  (when (nil? @db-instance)
    (connect!))
  @db-instance)

(defn connection
  "Get the MongoDB connection"
  []
  @conn)

