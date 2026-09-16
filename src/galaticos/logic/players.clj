(ns galaticos.logic.players
  "Player orchestration over PlayerStore."
  (:require [galaticos.db.aggregations :as agg]
            [galaticos.db.player-store :as store]
            [galaticos.db.protocol.player-store :as protocol]
            [galaticos.domain.errors :as errors]
            [galaticos.domain.players :as domain]
            [galaticos.util.response :as resp]))

(defn- require-ok [result]
  (if-let [err (:error result)]
    (case (:type err)
      :not-found (errors/not-found! (:message err))
      :conflict (errors/conflict! (:message err))
      :validation (errors/validation! (:message err))
      (errors/validation! (or (:message err) "Invalid request")))
    (:ok result)))

(defn list-all
  ([request] (list-all store/*store* request))
  ([store request]
   (let [filters (if-let [team-id (get-in request [:params :team-id])]
                   {:team-id (resp/->object-id team-id)}
                   {})
         active-only (get-in request [:params :active])]
     (if (= active-only "true")
       (protocol/find-active-players store)
       (protocol/find-all-players store filters)))))

(defn get-by-id
  ([id] (get-by-id store/*store* id))
  ([store id]
   (if-let [player (protocol/find-player-by-id store id)]
     player
     (errors/not-found! "Player not found"))))

(defn- resolve-team-name
  [store player]
  (try
    (when-let [tid (:team-id player)]
      (:name (protocol/find-team-by-id store tid)))
    (catch Exception _ nil)))

(defn detail-bundle
  ([id] (detail-bundle store/*store* id))
  ([store id]
   (if-let [player (protocol/find-player-by-id store id)]
     (let [team-name (resolve-team-name store player)
           player* (domain/attach-team-name player team-name)]
       {:player player*
        :evolution (agg/player-performance-evolution id)})
     (errors/not-found! "Player not found"))))

(defn- assert-team-exists! [store data]
  (when-let [tid (:team-id data)]
    (require-ok (domain/team-assignment-decision tid (protocol/team-exists? store tid)))))

(defn create!
  ([data] (create! store/*store* data))
  ([store data]
   (assert-team-exists! store data)
   (let [created (protocol/create-player store data)]
     (when-let [tid (:team-id created)]
       (protocol/add-player-to-team store tid (:_id created)))
     created)))

(defn update!
  ([id data] (update! store/*store* id data))
  ([store id data]
   (if (protocol/player-exists? store id)
     (do
       (protocol/update-player-by-id store id data)
       (if-let [updated (protocol/find-player-by-id store id)]
         updated
         (throw (ex-info "Failed to retrieve updated player"
                         {:status 500 :message "Failed to retrieve updated player"}))))
     (errors/not-found! "Player not found"))))

(defn delete!
  ([id] (delete! store/*store* id))
  ([store id]
   (if (protocol/player-exists? store id)
     (do
       (protocol/delete-player-by-id store id)
       {:message "Player deleted"})
     (errors/not-found! "Player not found"))))
