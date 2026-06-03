(ns galaticos.handlers.teams
  "Thin HTTP handlers for team operations."
  (:require [galaticos.logic.teams :as logic]
            [galaticos.util.response :as resp]
            [galaticos.validation.entity :as validation]))

(defn list-teams
  [request]
  (resp/success (logic/list-all request)))

(defn get-team
  [request]
  (resp/success (logic/get-by-id (get-in request [:params :id]))))

(defn create-team
  [request]
  (let [{:keys [error data]} (validation/validate-team-body (:json-body request))]
    (if error
      (resp/error error 400)
      (resp/success (logic/create! data) 201))))

(defn update-team
  [request]
  (let [id (get-in request [:params :id])
        {:keys [error data]} (validation/validate-team-body (:json-body request) false)]
    (if error
      (resp/error error 400)
      (resp/success (logic/update! id data)))))

(defn delete-team
  [request]
  (resp/success (logic/delete! (get-in request [:params :id]))))

(defn add-player-to-team
  [request]
  (let [team-id (get-in request [:params :id])
        player-id (get-in request [:params :player-id])]
    (if (and team-id player-id)
      (resp/success (logic/add-player! team-id player-id))
      (resp/error "Team ID and player ID required" 400))))

(defn remove-player-from-team
  [request]
  (let [team-id (get-in request [:params :id])
        player-id (get-in request [:params :player-id])]
    (if (and team-id player-id)
      (resp/success (logic/remove-player! team-id player-id))
      (resp/error "Team ID and player ID required" 400))))
