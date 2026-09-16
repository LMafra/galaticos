(ns galaticos.db.team-store
  "Monger-backed TeamStore."
  (:require [galaticos.db.protocol.team-store :as store-protocol]
            [galaticos.db.teams :as teams-db]))

(defn monger-store
  []
  (reify store-protocol/TeamStore
    (find-all-teams [_] (teams-db/find-all))
    (find-team-by-id [_ id] (teams-db/find-by-id id))
    (create-team [_ doc] (teams-db/create doc))
    (update-team-by-id [_ id updates] (teams-db/update-by-id id updates))
    (delete-team-by-id [_ id] (teams-db/delete-by-id id))
    (team-exists? [_ id] (teams-db/exists? id))
    (team-has-players? [_ id] (teams-db/has-players? id))
    (add-player-to-team [_ team-id player-id]
      (teams-db/add-player team-id player-id))
    (remove-player-from-team [_ team-id player-id]
      (teams-db/remove-player team-id player-id))))

(def ^:dynamic *store* (monger-store))
