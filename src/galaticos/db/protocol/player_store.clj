(ns galaticos.db.protocol.player-store
  "Persistence contract for players and related team lookups.")

(defprotocol PlayerStore
  (find-all-players [this filters])
  (find-active-players [this])
  (find-player-by-id [this id])
  (player-exists? [this id])
  (create-player [this doc])
  (update-player-by-id [this id updates])
  (delete-player-by-id [this id])
  (find-team-by-id [this id])
  (team-exists? [this id])
  (add-player-to-team [this team-id player-id]))
