(ns galaticos.handlers.teams
  "Request handlers for team operations"
  (:require [galaticos.db.teams :as teams-db]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private allowed-team-fields
  #{:name :city :coach :stadium :founded-year :logo-url :active-player-ids :notes})

(def ^:private required-team-fields
  #{:name})

(defn- validate-team-body
  ([body] (validate-team-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-team-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-team-fields)))
           normalized (cond-> body
                         (:active-player-ids body)
                         (update :active-player-ids #(mapv resp/->object-id %)))]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else {:data normalized})))))

(defn- handle-exception [e user-message]
  (if (= 400 (-> e ex-data :status))
    (resp/error (or user-message (.getMessage e)) 400)
    (do
      (log/error e user-message)
      (resp/server-error user-message))))

(defn list-teams
  "List all teams"
  [_request]
  (try
    (let [teams (teams-db/find-all)]
      (resp/success teams))
    (catch Exception e
      (handle-exception e "Failed to list teams"))))

(defn get-team
  "Get a single team by ID"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [team (teams-db/find-by-id id)]
        (resp/success team)
        (resp/not-found "Team not found")))
    (catch Exception e
      (handle-exception e "Failed to get team"))))

(defn create-team
  "Create a new team"
  [request]
  (try
    (let [team-data (:json-body request)
          {:keys [error data]} (validate-team-body team-data)]
      (if error
        (resp/error error 400)
        (let [created (teams-db/create data)]
          (resp/success created 201))))
    (catch Exception e
      (handle-exception e "Failed to create team"))))

(defn update-team
  "Update an existing team"
  [request]
  (try
    (let [id (get-in request [:params :id])
          updates (:json-body request)
          {:keys [error data]} (validate-team-body updates false)]
      (if error
        (resp/error error 400)
        (if (teams-db/exists? id)
          (do
            (teams-db/update-by-id id data)
            (if-let [updated (teams-db/find-by-id id)]
              (resp/success updated)
              (resp/server-error "Failed to retrieve updated team")))
          (resp/not-found "Team not found"))))
    (catch Exception e
      (handle-exception e "Failed to update team"))))

(defn delete-team
  "Delete a team"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if (teams-db/exists? id)
        (do
          (teams-db/delete-by-id id)
          (resp/success {:message "Team deleted"}))
        (resp/not-found "Team not found")))
    (catch Exception e
      (handle-exception e "Failed to delete team"))))

(defn add-player-to-team
  "Add a player to a team"
  [request]
  (try
    (let [team-id (get-in request [:params :id])
          player-id (get-in request [:params :player-id])]
      (if (and team-id player-id)
        (do
          (teams-db/add-player team-id player-id)
          (if-let [updated (teams-db/find-by-id team-id)]
            (resp/success updated)
            (resp/server-error "Failed to retrieve updated team")))
        (resp/error "Team ID and player ID required")))
    (catch Exception e
      (handle-exception e "Failed to add player to team"))))

(defn remove-player-from-team
  "Remove a player from a team"
  [request]
  (try
    (let [team-id (get-in request [:params :id])
          player-id (get-in request [:params :player-id])]
      (if (and team-id player-id)
        (do
          (teams-db/remove-player team-id player-id)
          (if-let [updated (teams-db/find-by-id team-id)]
            (resp/success updated)
            (resp/server-error "Failed to retrieve updated team")))
        (resp/error "Team ID and player ID required")))
    (catch Exception e
      (handle-exception e "Failed to remove player from team"))))

