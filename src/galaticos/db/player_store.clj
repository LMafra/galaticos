(ns galaticos.db.player-store
  "Monger-backed PlayerStore."
  (:require [galaticos.db.players :as players-db]
            [galaticos.db.protocol.player-store :as store-protocol]
            [galaticos.db.teams :as teams-db]))

(defn monger-store
  []
  (reify store-protocol/PlayerStore
    (find-all-players [_ filters] (players-db/find-all filters))
    (find-active-players [_] (players-db/find-active))
    (find-player-by-id [_ id] (players-db/find-by-id id))
    (player-exists? [_ id] (players-db/exists? id))
    (create-player [_ doc] (players-db/create doc))
    (update-player-by-id [_ id updates] (players-db/update-by-id id updates))
    (delete-player-by-id [_ id] (players-db/delete-by-id id))
    (find-team-by-id [_ id] (teams-db/find-by-id id))
    (team-exists? [_ id] (teams-db/exists? id))
    (add-player-to-team [_ team-id player-id]
      (teams-db/add-player team-id player-id))))

(def ^:dynamic *store* (monger-store))
