(ns galaticos.handlers.players
  "Thin HTTP handlers for player operations."
  (:require [galaticos.logic.players :as logic]
            [galaticos.util.response :as resp]
            [galaticos.validation.entity :as validation]))

(defn list-players
  [request]
  (resp/success (logic/list-all request)))

(defn get-player
  [request]
  (resp/success (logic/get-by-id (get-in request [:params :id]))))

(defn get-player-detail-bundle
  [request]
  (resp/success (logic/detail-bundle (get-in request [:params :id]))))

(defn create-player
  [request]
  (let [{:keys [error data]} (validation/validate-player-body (:json-body request))]
    (if error
      (resp/error error 400)
      (resp/success (logic/create! data) 201))))

(defn update-player
  [request]
  (let [id (get-in request [:params :id])
        {:keys [error data]} (validation/validate-player-body (:json-body request) false)]
    (if error
      (resp/error error 400)
      (resp/success (logic/update! id data)))))

(defn delete-player
  [request]
  (resp/success (logic/delete! (get-in request [:params :id]))))
