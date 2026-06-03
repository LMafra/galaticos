(ns galaticos.handlers.seasons
  "Thin HTTP handlers for season operations."
  (:require [galaticos.logic.seasons :as logic]
            [galaticos.util.response :as resp]
            [galaticos.validation.entity :as validation]))

(defn list-seasons
  [request]
  (resp/success (logic/list-by-championship (get-in request [:params :id]))))

(defn get-season
  [request]
  (resp/success (logic/get-by-id (get-in request [:params :id]))))

(defn create-season
  [request]
  (let [championship-id (get-in request [:params :id])
        {:keys [error data]} (validation/validate-season-body (:json-body request))]
    (if error
      (resp/error error 400)
      (resp/success (logic/create! championship-id data) 201))))

(defn update-season
  [request]
  (let [season-id (get-in request [:params :id])
        {:keys [error data]} (validation/validate-season-body (:json-body request) false)]
    (if error
      (resp/error error 400)
      (resp/success (logic/update! season-id data)))))

(defn delete-season
  [request]
  (resp/success (logic/delete! (get-in request [:params :id]))))

(defn activate-season
  [request]
  (resp/success (logic/activate! (get-in request [:params :id]))))

(defn enroll-player
  [request]
  (let [season-id (get-in request [:params :id])
        player-id (get-in request [:params :player-id])]
    (if-not (and season-id player-id)
      (resp/error "Season ID and player ID are required" 400)
      (resp/success (logic/enroll! season-id player-id)))))

(defn unenroll-player
  [request]
  (let [season-id (get-in request [:params :id])
        player-id (get-in request [:params :player-id])]
    (if-not (and season-id player-id)
      (resp/error "Season ID and player ID are required" 400)
      (resp/success (logic/unenroll! season-id player-id)))))

(defn get-season-players
  [request]
  (resp/success (logic/season-players (get-in request [:params :id]))))

(defn finalize-season
  [request]
  (resp/success (logic/finalize! (get-in request [:params :id])
                                 (get-in request [:json-body] {}))))
