(ns galaticos.handlers.championships
  "Request handlers for championship operations"
  (:require [galaticos.db.championships :as championships-db]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private allowed-championship-fields
  #{:name :season :status :format :start-date :end-date :location :notes :titles-count})

(def ^:private required-championship-fields
  #{:name :season :titles-count})

(defn- validate-championship-body
  ([body] (validate-championship-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-championship-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-championship-fields)))]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else {:data body})))))

(defn- handle-exception [e user-message]
  (if (= 400 (-> e ex-data :status))
    (resp/error (or user-message (.getMessage e)) 400)
    (do
      (log/error e user-message)
      (resp/server-error user-message))))

(defn list-championships
  "List all championships"
  [request]
  (try
    (let [status (get-in request [:params :status])
          championships (if status
                          (championships-db/find-all {:status status})
                          (championships-db/find-all))]
      (resp/success championships))
    (catch Exception e
      (handle-exception e "Failed to list championships"))))

(defn get-championship
  "Get a single championship by ID"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [championship (championships-db/find-by-id id)]
        (resp/success championship)
        (resp/not-found "Championship not found")))
    (catch Exception e
      (handle-exception e "Failed to get championship"))))

(defn create-championship
  "Create a new championship"
  [request]
  (try
    (let [championship-data (:json-body request)
          {:keys [error data]} (validate-championship-body championship-data)]
      (if error
        (resp/error error 400)
        (let [created (championships-db/create data)]
          (resp/success created 201))))
    (catch Exception e
      (handle-exception e "Failed to create championship"))))

(defn update-championship
  "Update an existing championship"
  [request]
  (try
    (let [id (get-in request [:params :id])
          updates (:json-body request)
          {:keys [error data]} (validate-championship-body updates false)]
      (if error
        (resp/error error 400)
        (if (championships-db/exists? id)
          (do
            (championships-db/update-by-id id data)
            (if-let [updated (championships-db/find-by-id id)]
              (resp/success updated)
              (resp/server-error "Failed to retrieve updated championship")))
          (resp/not-found "Championship not found"))))
    (catch Exception e
      (handle-exception e "Failed to update championship"))))

(defn delete-championship
  "Delete a championship"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if (championships-db/exists? id)
        (if (championships-db/has-matches? id)
          (resp/error "Cannot delete championship: it has associated matches. Please delete or reassign matches first." 409)
          (do
            (championships-db/delete-by-id id)
            (resp/success {:message "Championship deleted"})))
        (resp/not-found "Championship not found")))
    (catch Exception e
      (handle-exception e "Failed to delete championship"))))

