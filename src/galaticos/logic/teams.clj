(ns galaticos.logic.teams
  "Team orchestration over TeamStore."
  (:require [galaticos.db.protocol.team-store :as protocol]
            [galaticos.db.team-store :as store]
            [galaticos.domain.errors :as errors]
            [galaticos.domain.teams :as domain]))

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
  ([store _request]
   (protocol/find-all-teams store)))

(defn get-by-id
  ([id] (get-by-id store/*store* id))
  ([store id]
   (if-let [team (protocol/find-team-by-id store id)]
     team
     (errors/not-found! "Team not found"))))

(defn create!
  ([data] (create! store/*store* data))
  ([store data]
   (protocol/create-team store data)))

(defn update!
  ([id data] (update! store/*store* id data))
  ([store id data]
   (if (protocol/team-exists? store id)
     (do
       (protocol/update-team-by-id store id data)
       (if-let [updated (protocol/find-team-by-id store id)]
         updated
         (throw (ex-info "Failed to retrieve updated team"
                         {:status 500 :message "Failed to retrieve updated team"}))))
     (errors/not-found! "Team not found"))))

(defn delete!
  ([id] (delete! store/*store* id))
  ([store id]
   (let [exists? (protocol/team-exists? store id)]
     (require-ok (domain/can-delete? exists? (and exists? (protocol/team-has-players? store id))))
     (protocol/delete-team-by-id store id)
     {:message "Team deleted"})))

(defn add-player!
  ([team-id player-id] (add-player! store/*store* team-id player-id))
  ([store team-id player-id]
   (protocol/add-player-to-team store team-id player-id)
   (if-let [updated (protocol/find-team-by-id store team-id)]
     updated
     (throw (ex-info "Failed to retrieve updated team"
                     {:status 500 :message "Failed to retrieve updated team"})))))

(defn remove-player!
  ([team-id player-id] (remove-player! store/*store* team-id player-id))
  ([store team-id player-id]
   (protocol/remove-player-from-team store team-id player-id)
   (if-let [updated (protocol/find-team-by-id store team-id)]
     updated
     (throw (ex-info "Failed to retrieve updated team"
                     {:status 500 :message "Failed to retrieve updated team"})))))
