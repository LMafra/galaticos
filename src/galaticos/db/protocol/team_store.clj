(ns galaticos.db.protocol.team-store
  "Persistence contract for teams.")

(defprotocol TeamStore
  (find-all-teams [this])
  (find-team-by-id [this id])
  (create-team [this doc])
  (update-team-by-id [this id updates])
  (delete-team-by-id [this id])
  (team-exists? [this id])
  (team-has-players? [this id])
  (add-player-to-team [this team-id player-id])
  (remove-player-from-team [this team-id player-id]))
