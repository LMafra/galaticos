(ns galaticos.db.championship-store
  "Monger-backed ChampionshipStore."
  (:require [galaticos.championship.roster :as roster]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.db.protocol.championship-store :as store-protocol]
            [galaticos.db.seasons :as seasons-db]))

(defn monger-store
  []
  (reify store-protocol/ChampionshipStore
    (find-all-championships [_] (championships-db/find-all))
    (find-championship-by-id [_ id] (championships-db/find-by-id id))
    (create-championship [_ doc] (championships-db/create doc))
    (update-championship-by-id [_ id updates] (championships-db/update-by-id id updates))
    (delete-championship-by-id [_ id] (championships-db/delete-by-id id))
    (championship-exists? [_ id] (championships-db/exists? id))
    (championship-has-matches? [_ id] (championships-db/has-matches? id))
    (add-player-to-championship [_ championship-id player-id]
      (championships-db/add-player championship-id player-id))
    (remove-player-from-championship [_ championship-id player-id]
      (championships-db/remove-player championship-id player-id))
    (find-all-seasons-by-championship [_ championship-id]
      (seasons-db/find-all-by-championship championship-id))
    (find-active-season-by-championship [_ championship-id]
      (seasons-db/find-active-by-championship championship-id))
    (create-season [_ doc] (seasons-db/create doc))
    (update-season-by-id [_ id updates] (seasons-db/update-by-id id updates))
    (delete-seasons-by-championship [_ championship-id]
      (seasons-db/delete-by-championship championship-id))
    (activate-season! [_ season-id] (seasons-db/activate! season-id))
    (finalize-season! [_ season-id winner-player-ids titles-award-count]
      (seasons-db/finalize! season-id winner-player-ids titles-award-count))
    (add-player-to-season [_ season-id player-id]
      (seasons-db/add-player season-id player-id))
    (remove-player-from-season [_ season-id player-id]
      (seasons-db/remove-player season-id player-id))
    (find-player-by-id [_ player-id] (players-db/find-by-id player-id))
    (find-players-by-ids [_ player-ids] (players-db/find-by-ids player-ids))
    (increment-player-titles [_ player-ids amount]
      (players-db/increment-titles player-ids amount))
    (enrolled-player-object-ids [_ championship-id]
      (roster/enrolled-player-object-ids championship-id))))

(def ^:dynamic *store* (monger-store))
