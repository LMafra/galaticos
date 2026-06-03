(ns galaticos.logic.seasons
  "Season orchestration."
  (:require [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.db.seasons :as seasons-db]
            [galaticos.domain.errors :as errors]
            [galaticos.domain.seasons :as domain]
            [galaticos.util.response :as resp]))

(defn list-by-championship
  [championship-id]
  (seasons-db/find-all-by-championship championship-id))

(defn get-by-id
  [id]
  (if-let [season (seasons-db/find-by-id id)]
    season
    (errors/not-found! "Season not found")))

(defn create!
  [championship-id data]
  (let [championship (championships-db/find-by-id championship-id)
        format (or (:format data) (:format championship))
        created (seasons-db/create (merge data {:championship-id championship-id
                                                :format format}))]
    (when (= "active" (:status created))
      (seasons-db/activate! (:_id created)))
    created))

(defn update!
  [id data]
  (if (seasons-db/exists? id)
    (do
      (seasons-db/update-by-id id data)
      (if-let [updated (seasons-db/find-by-id id)]
        updated
        (throw (ex-info "Failed to retrieve updated season"
                        {:status 500 :message "Failed to retrieve updated season"}))))
    (errors/not-found! "Season not found")))

(defn delete!
  [id]
  (if (seasons-db/exists? id)
    (do
      (seasons-db/delete-by-id id)
      {:message "Season deleted"})
    (errors/not-found! "Season not found")))

(defn activate!
  [id]
  (if (seasons-db/exists? id)
    (do
      (seasons-db/activate! id)
      {:message "Season activated"})
    (errors/not-found! "Season not found")))

(defn enroll!
  [season-id player-id]
  (if-let [season (seasons-db/find-by-id season-id)]
    (do
      (seasons-db/add-player season-id player-id)
      {:message "Player enrolled"})
    (errors/not-found! "Season not found")))

(defn unenroll!
  [season-id player-id]
  (if (seasons-db/exists? season-id)
    (do
      (seasons-db/remove-player season-id player-id)
      {:message "Player unenrolled"})
    (errors/not-found! "Season not found")))

(defn season-players
  [season-id]
  (if-let [season (seasons-db/find-by-id season-id)]
    (players-db/find-by-ids (:enrolled-player-ids season []))
    (errors/not-found! "Season not found")))

(defn finalize!
  [season-id body]
  (if (seasons-db/exists? season-id)
    (let [{:keys [winner-player-ids titles-award-count]}
          (domain/finalize-payload body resp/->object-id)]
      (when (= titles-award-count ::invalid)
        (errors/validation! "titles-award-count must be a non-negative number"))
      (seasons-db/finalize! season-id winner-player-ids titles-award-count)
      {:message "Season finalized"})
    (errors/not-found! "Season not found")))
