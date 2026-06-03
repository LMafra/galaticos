(ns galaticos.logic.championships
  "Championship orchestration over ChampionshipStore."
  (:require [galaticos.db.championship-store :as store]
            [galaticos.db.protocol.championship-store :as protocol]
            [galaticos.domain.championships :as domain]
            [galaticos.domain.errors :as errors]
            [galaticos.util.response :as resp])
  (:import [java.util Date]))

(defn- throw-domain-error [{:keys [type message]}]
  (case type
    :not-found (errors/not-found! message)
    :conflict (errors/conflict! message)
    :validation (errors/validation! message)
    (errors/validation! (or message "Invalid request"))))

(defn- require-ok [result]
  (if-let [err (:error result)]
    (throw-domain-error err)
    (:ok result)))

(defn- enrich-championship [store championship]
  (let [cid (:_id championship)]
    (domain/enrich championship
                   {:all-seasons (protocol/find-all-seasons-by-championship store cid)
                    :active-season (protocol/find-active-season-by-championship store cid)})))

(defn list
  ([request] (list store/*store* request))
  ([store request]
   (let [status (get-in request [:params :status])
         championships (map #(enrich-championship store %)
                            (protocol/find-all-championships store))]
     (if status
       (vec (filter #(= (:status %) status) championships))
       (vec championships)))))

(defn get-by-id
  ([id] (get-by-id store/*store* id))
  ([store id]
   (if-let [championship (protocol/find-championship-by-id store id)]
     (enrich-championship store championship)
     (errors/not-found! "Championship not found"))))

(defn create!
  ([data] (create! store/*store* data))
  ([store data]
   (let [{:keys [name format season status start-date end-date location notes
                 titles-count max-players enrolled-player-ids winner-player-ids
                 finished-at]} data
         root (protocol/create-championship store {:name name :format format})
         season-doc (protocol/create-season store
                                            {:championship-id (:_id root)
                                             :season season
                                             :status (or status "inactive")
                                             :format format
                                             :start-date start-date
                                             :end-date end-date
                                             :location location
                                             :notes notes
                                             :max-players max-players
                                             :enrolled-player-ids enrolled-player-ids
                                             :winner-player-ids winner-player-ids
                                             :finished-at finished-at
                                             :titles-award-count (or titles-count 0)
                                             :titles-count (or titles-count 0)})]
     (when (= "active" (:status season-doc))
       (protocol/activate-season! store (:_id season-doc)))
     (enrich-championship store root))))

(defn update!
  ([id data] (update! store/*store* id data))
  ([store id data]
   (if-let [_root (protocol/find-championship-by-id store id)]
     (do
       (let [root-updates (select-keys data #{:name :format :notes :location})]
         (when (seq root-updates)
           (protocol/update-championship-by-id store id root-updates)))

       (when-let [active-season (protocol/find-active-season-by-championship store id)]
         (let [season-updates (select-keys data #{:season :status :start-date :end-date :max-players
                                                  :titles-count :titles-award-count :finished-at
                                                  :enrolled-player-ids :winner-player-ids :location :notes})]
           (when (seq season-updates)
             (protocol/update-season-by-id store (:_id active-season) season-updates)
             (when (= "active" (:status season-updates))
               (protocol/activate-season! store (:_id active-season))))))

       (if-let [updated (protocol/find-championship-by-id store id)]
         (enrich-championship store updated)
         (throw (ex-info "Failed to retrieve updated championship"
                         {:status 500 :message "Failed to retrieve updated championship"}))))
     (errors/not-found! "Championship not found"))))

(defn delete!
  ([id] (delete! store/*store* id))
  ([store id]
   (require-ok (domain/can-delete? (protocol/championship-exists? store id)
                                   (protocol/championship-has-matches? store id)))
   (try
     (protocol/delete-seasons-by-championship store id)
     (catch Exception _ nil))
   (protocol/delete-championship-by-id store id)
   {:message "Championship deleted"}))

(defn- enroll-on-season! [store active-season player-id]
  (let [enrolled (domain/enrolled-object-id-set active-season)
        max-players (:max-players active-season)
        player-oid (resp/->object-id player-id)
        decision (domain/enrollment-decision
                  {:enrolled-count (count enrolled)
                   :max-players max-players
                   :already-enrolled? (contains? enrolled player-oid)
                   :scope :season})]
    (require-ok decision)
    (protocol/add-player-to-season store (:_id active-season) player-id)
    {:message "Player enrolled"}))

(defn- enroll-on-championship! [store championship-id championship player-id]
  (let [enrolled (domain/enrolled-object-id-set championship)
        max-players (:max-players championship)
        player-oid (resp/->object-id player-id)
        decision (domain/enrollment-decision
                  {:enrolled-count (count enrolled)
                   :max-players max-players
                   :already-enrolled? (contains? enrolled player-oid)
                   :scope :championship})]
    (require-ok decision)
    (protocol/add-player-to-championship store championship-id player-id)
    {:message "Player enrolled"}))

(defn enroll!
  ([championship-id player-id] (enroll! store/*store* championship-id player-id))
  ([store championship-id player-id]
   (if-not (protocol/find-player-by-id store player-id)
     (errors/not-found! "Player not found")
     (if-let [active-season (protocol/find-active-season-by-championship store championship-id)]
       (enroll-on-season! store active-season player-id)
       (if-let [championship (protocol/find-championship-by-id store championship-id)]
         (enroll-on-championship! store championship-id championship player-id)
         (errors/not-found! "Championship not found"))))))

(defn unenroll!
  ([championship-id player-id] (unenroll! store/*store* championship-id player-id))
  ([store championship-id player-id]
   (if-let [active-season (protocol/find-active-season-by-championship store championship-id)]
     (do
       (protocol/remove-player-from-season store (:_id active-season) player-id)
       {:message "Player unenrolled"})
     (if (protocol/championship-exists? store championship-id)
       (do
         (protocol/remove-player-from-championship store championship-id player-id)
         {:message "Player unenrolled"})
       (errors/not-found! "Championship not found")))))

(defn championship-players
  ([championship-id] (championship-players store/*store* championship-id))
  ([store championship-id]
   (if-not (protocol/championship-exists? store championship-id)
     (errors/not-found! "Championship not found")
     (let [union-ids (protocol/enrolled-player-object-ids store championship-id)
           players (protocol/find-players-by-ids store union-ids)]
       (vec (sort-by (fn [p] (clojure.string/lower-case (str (:name p)))) players))))))

(defn finalize!
  ([championship-id body] (finalize! store/*store* championship-id body))
  ([store championship-id body]
   (let [winner-player-ids (mapv resp/->object-id (get body :winner-player-ids []))
         titles-award-count (domain/parse-titles-award-count (get body :titles-award-count))
         active-season (protocol/find-active-season-by-championship store championship-id)
         championship (when-not active-season
                        (protocol/find-championship-by-id store championship-id))
         decision (domain/finalization-decision active-season championship
                                                winner-player-ids titles-award-count)
         {:keys [target season championship titles-award-count]} (require-ok decision)]
     (case target
       :season
       (do
         (protocol/finalize-season! store (:_id season) winner-player-ids titles-award-count)
         {:message "Season finalized"})

       :championship
       (do
         (protocol/update-championship-by-id store championship-id
                                             {:status "completed"
                                              :finished-at (Date.)
                                              :winner-player-ids winner-player-ids
                                              :titles-award-count titles-award-count})
         (when (pos? titles-award-count)
           (protocol/increment-player-titles store winner-player-ids titles-award-count))
         {:message "Championship finalized"})))))
