(ns galaticos.handlers.matches
  "Thin HTTP handlers for match operations.
  
  Delega para logic/matches que implementa as regras de temporada:
  - CREATE: só temporada ativa (RN-MATCH-08)
  - UPDATE/DELETE: permitidos mesmo com temporada concluída (RN-MATCH-09)
  
  Ver: docs/reference/domain/matches-seasons-hybrid-stats.md"
  (:require [galaticos.logic.matches :as logic]
            [galaticos.util.response :as resp]
            [galaticos.validation.entity :as validation]))

(defn list-matches
  [request]
  (resp/success (logic/list-matches request)))

(defn get-match
  [request]
  (resp/success (logic/get-by-id (get-in request [:params :id]))))

(defn create-match
  [request]
  (let [{:keys [error data]} (validation/validate-match-body (:json-body request))]
    (if error
      (resp/error error 400)
      (resp/success (logic/create! request data) 201))))

(defn update-match
  [request]
  (let [id (get-in request [:params :id])
        {:keys [error data]} (validation/validate-match-body (:json-body request) false)]
    (if error
      (resp/error error 400)
      (resp/success (logic/update! id request data)))))

(defn delete-match
  [request]
  (resp/success (logic/delete! (get-in request [:params :id]) request)))
