(ns galaticos.db.match-store
  "Monger-backed MatchStore."
  (:require [galaticos.db.championships :as championships-db]
            [galaticos.db.matches :as matches-db]
            [galaticos.db.players :as players-db]
            [galaticos.db.protocol.match-store :as store-protocol]
            [galaticos.db.seasons :as seasons-db]
            [galaticos.db.teams :as teams-db]))

(defn monger-store
  []
  (reify store-protocol/MatchStore
    (find-all-matches [_] (matches-db/find-all))
    (find-match-by-id [_ id] (matches-db/find-by-id id))
    (find-matches-by-championship [_ championship-id]
      (matches-db/find-by-championship championship-id))
    (find-matches-by-season [_ season-id]
      (matches-db/find-by-season season-id))
    (create-match [_ match-data player-statistics opts]
      (matches-db/create match-data player-statistics opts))
    (update-match-by-id [_ id updates opts]
      (matches-db/update-by-id id updates opts))
    (delete-match-by-id [_ id] (matches-db/delete-by-id id))
    (match-exists? [_ id] (matches-db/exists? id))
    (find-season-by-id [_ id] (seasons-db/find-by-id id))
    (find-default-season-for-championship [_ championship-id]
      (seasons-db/find-default-for-championship championship-id))
    (find-season-for-new-match [_ championship-id season-id]
      (seasons-db/find-for-new-match championship-id season-id))
    (add-match-to-season! [_ season-id match-id]
      (seasons-db/add-match season-id match-id))
    (remove-match-from-season! [_ season-id match-id]
      (seasons-db/remove-match season-id match-id))
    (find-championship-by-id [_ id] (championships-db/find-by-id id))
    (find-players-by-ids [_ ids] (players-db/find-by-ids ids))
    (find-team-by-id [_ id] (teams-db/find-by-id id))
    (find-teams-by-ids [_ ids] (teams-db/find-by-ids ids))))

(def ^:dynamic *store* (monger-store))
