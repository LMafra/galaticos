(ns galaticos.logic.seasons
  "Season orchestration over ChampionshipStore."
  (:require [clojure.string :as str]
            [galaticos.db.championship-store :as store]
            [galaticos.db.protocol.championship-store :as protocol]
            [galaticos.domain.errors :as errors]
            [galaticos.domain.seasons :as domain]
            [galaticos.util.response :as resp]))

(defn list-by-championship
  ([championship-id] (list-by-championship store/*store* championship-id))
  ([store championship-id]
   (protocol/find-all-seasons-by-championship store championship-id)))

(defn get-by-id
  ([id] (get-by-id store/*store* id))
  ([store id]
   (if-let [season (protocol/find-season-by-id store id)]
     season
     (errors/not-found! "Season not found"))))

(defn create!
  ([championship-id data] (create! store/*store* championship-id data))
  ([store championship-id data]
   (let [championship (protocol/find-championship-by-id store championship-id)
         format (or (:format data) (:format championship))
         created (protocol/create-season store (merge data {:championship-id championship-id
                                                            :format format}))]
     (when (= "active" (:status created))
       (protocol/activate-season! store (:_id created)))
     created)))

(defn update!
  ([id data] (update! store/*store* id data))
  ([store id data]
   (if (protocol/season-exists? store id)
     (do
       (protocol/update-season-by-id store id data)
       (if-let [updated (protocol/find-season-by-id store id)]
         updated
         (throw (ex-info "Failed to retrieve updated season"
                         {:status 500 :message "Failed to retrieve updated season"}))))
     (errors/not-found! "Season not found"))))

(defn delete!
  ([id] (delete! store/*store* id))
  ([store id]
   (if (protocol/season-exists? store id)
     (do
       (protocol/delete-season-by-id store id)
       {:message "Season deleted"})
     (errors/not-found! "Season not found"))))

(defn activate!
  ([id] (activate! store/*store* id))
  ([store id]
   (if (protocol/season-exists? store id)
     (do
       (protocol/activate-season! store id)
       {:message "Season activated"})
     (errors/not-found! "Season not found"))))

(defn enroll!
  ([season-id player-id] (enroll! store/*store* season-id player-id))
  ([store season-id player-id]
   (if (protocol/season-exists? store season-id)
     (do
       (protocol/add-player-to-season store season-id player-id)
       {:message "Player enrolled"})
     (errors/not-found! "Season not found"))))

(defn unenroll!
  ([season-id player-id] (unenroll! store/*store* season-id player-id))
  ([store season-id player-id]
   (if (protocol/season-exists? store season-id)
     (do
       (protocol/remove-player-from-season store season-id player-id)
       {:message "Player unenrolled"})
     (errors/not-found! "Season not found"))))

(defn season-players
  ([season-id] (season-players store/*store* season-id))
  ([store season-id]
   (if-let [season (protocol/find-season-by-id store season-id)]
     (protocol/find-players-by-ids store (:enrolled-player-ids season []))
     (errors/not-found! "Season not found"))))

(defn finalize!
  ([season-id body] (finalize! store/*store* season-id body))
  ([store season-id body]
   (if (protocol/season-exists? store season-id)
     (let [{:keys [winner-player-ids titles-award-count]}
           (domain/finalize-payload body resp/->object-id)
           reason (some-> (get body :reason) str str/trim not-empty)]
       (when (= titles-award-count ::invalid)
         (errors/validation! "titles-award-count must be a non-negative number"))
       (protocol/finalize-season! store season-id winner-player-ids titles-award-count)
       (when reason
         (protocol/update-season-by-id store season-id {:finalize-reason reason}))
       {:message "Season finalized"})
     (errors/not-found! "Season not found"))))
