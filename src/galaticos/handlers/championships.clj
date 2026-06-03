(ns galaticos.handlers.championships
  "Thin HTTP handlers for championship operations."
  (:require [galaticos.logic.championships :as logic]
            [galaticos.util.response :as resp]
            [galaticos.validation.entity :as validation]))

(defn list-championships
  [request]
  (resp/success (logic/list request)))

(defn get-championship
  [request]
  (resp/success (logic/get-by-id (get-in request [:params :id]))))

(defn create-championship
  [request]
  (let [{:keys [error data]} (validation/validate-championship-body (:json-body request))]
    (if error
      (resp/error error 400)
      (resp/success (logic/create! data) 201))))

(defn update-championship
  [request]
  (let [id (get-in request [:params :id])
        {:keys [error data]} (validation/validate-championship-body (:json-body request) false)]
    (if error
      (resp/error error 400)
      (resp/success (logic/update! id data)))))

(defn delete-championship
  [request]
  (resp/success (logic/delete! (get-in request [:params :id]))))

(defn enroll-player
  [request]
  (let [championship-id (get-in request [:params :id])
        player-id (get-in request [:params :player-id])]
    (if-not (and championship-id player-id)
      (resp/error "Championship ID and player ID are required" 400)
      (resp/success (logic/enroll! championship-id player-id)))))

(defn unenroll-player
  [request]
  (resp/success (logic/unenroll! (get-in request [:params :id])
                                  (get-in request [:params :player-id]))))

(defn get-championship-players
  [request]
  (resp/success (logic/championship-players (get-in request [:params :id]))))

(defn finalize-championship
  [request]
  (resp/success (logic/finalize! (get-in request [:params :id])
                                 (get-in request [:json-body] {}))))
