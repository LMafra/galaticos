(ns galaticos.logic.teams
  "Team orchestration."
  (:require [galaticos.db.teams :as teams-db]
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
  [_request]
  (teams-db/find-all))

(defn get-by-id
  [id]
  (if-let [team (teams-db/find-by-id id)]
    team
    (errors/not-found! "Team not found")))

(defn create!
  [data]
  (teams-db/create data))

(defn update!
  [id data]
  (if (teams-db/exists? id)
    (do
      (teams-db/update-by-id id data)
      (if-let [updated (teams-db/find-by-id id)]
        updated
        (throw (ex-info "Failed to retrieve updated team"
                        {:status 500 :message "Failed to retrieve updated team"}))))
    (errors/not-found! "Team not found")))

(defn delete!
  [id]
  (let [exists? (teams-db/exists? id)]
    (require-ok (domain/can-delete? exists? (and exists? (teams-db/has-players? id))))
    (teams-db/delete-by-id id)
    {:message "Team deleted"}))

(defn add-player!
  [team-id player-id]
  (teams-db/add-player team-id player-id)
  (if-let [updated (teams-db/find-by-id team-id)]
    updated
    (throw (ex-info "Failed to retrieve updated team"
                    {:status 500 :message "Failed to retrieve updated team"}))))

(defn remove-player!
  [team-id player-id]
  (teams-db/remove-player team-id player-id)
  (if-let [updated (teams-db/find-by-id team-id)]
    updated
    (throw (ex-info "Failed to retrieve updated team"
                    {:status 500 :message "Failed to retrieve updated team"}))))
