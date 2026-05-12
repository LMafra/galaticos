(ns galaticos.handlers.championships
  "Request handlers for championship operations"
  (:require [galaticos.championship.roster :as roster]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.seasons :as seasons-db]
            [galaticos.db.players :as players-db]
            [galaticos.util.response :as resp]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import [org.bson.types ObjectId]
           [java.util Date]))

(def ^:private allowed-championship-fields
  #{:name :season :status :format :start-date :end-date :location :notes :titles-count
    :max-players :enrolled-player-ids :winner-player-ids :finished-at})

(def ^:private required-championship-fields
  #{:name :season :titles-count})

(defn- sum-season-titles-across
  [championship-id]
  (long (reduce + 0
                (map #(long (or (:titles-count %) 0))
                     (seasons-db/find-all-by-championship championship-id)))))

(defn- season-year-sort-key
  [season-row]
  (try (Long/parseLong (str/trim (str (:season season-row ""))))
       (catch Exception _ Long/MIN_VALUE)))

(defn- pick-latest-season-for-display
  "Latest by :season year (desc), then :updated-at (desc). Used when no season is active."
  [season-rows]
  (when (seq season-rows)
    (first (sort-by (fn [s]
                      (let [t (:updated-at s)]
                        [(- (season-year-sort-key s))
                         (- (if (instance? Date t) (.getTime ^Date t) 0))]))
                    season-rows))))

(defn- merge-display-from-season-row
  [championship row]
  (merge championship
         {:season (or (:season championship) (:season row))
          :status (or (:status championship) (:status row))
          :active-season-id nil
          :enrolled-player-ids (:enrolled-player-ids row [])
          :winner-player-ids (:winner-player-ids row [])
          :titles-award-count (:titles-award-count row)
          :titles-count (or (:titles-count row) (:titles-award-count row) 0)
          :finished-at (:finished-at row)
          :max-players (:max-players row)
          :start-date (:start-date row)
          :end-date (:end-date row)
          :format (or (:format championship) (:format row))}))

(defn- enrich-championship-with-active-season
  [championship]
  (let [cid (:_id championship)
        total-titles (sum-season-titles-across cid)
        all-seasons (seasons-db/find-all-by-championship cid)
        active (seasons-db/find-active-by-championship cid)
        fallback (when (and (nil? active) (seq all-seasons))
                   (pick-latest-season-for-display all-seasons))
        enriched (cond
                   active
                   (merge championship
                          {:season (:season active)
                           :status (:status active)
                           :active-season-id (:_id active)
                           :enrolled-player-ids (:enrolled-player-ids active [])
                           :winner-player-ids (:winner-player-ids active [])
                           :titles-award-count (:titles-award-count active)
                           :titles-count (or (:titles-count active) (:titles-award-count active) 0)
                           :finished-at (:finished-at active)
                           :max-players (:max-players active)
                           :start-date (:start-date active)
                           :end-date (:end-date active)
                           ;; Keep root format as fallback; active season may denormalize too.
                           :format (or (:format championship) (:format active))})

                   fallback
                   (merge-display-from-season-row championship fallback)

                   :else
                   championship)]
    (assoc enriched :total-titles-across-seasons total-titles)))

(defn- normalize-championship-body [body]
  (let [max-players (:max-players body)]
    (cond-> body
      (some? max-players)
      (update :max-players (fn [value]
                             (cond
                               (number? value) value
                               (string? value) (let [trimmed (str/trim value)]
                                                 (when (not (str/blank? trimmed))
                                                   (Long/parseLong trimmed)))
                               :else value)))
      (:enrolled-player-ids body)
      (update :enrolled-player-ids (fn [ids]
                                     (mapv resp/->object-id ids))))))

(defn- validate-championship-body
  ([body] (validate-championship-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-championship-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-championship-fields)))
           normalized (normalize-championship-body body)
           max-players (:max-players normalized)]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         (and (some? max-players) (or (not (number? max-players)) (neg? max-players)))
         {:error "max-players must be a non-negative number"}
         :else {:data normalized})))))

(defn- handle-exception [e user-message]
  (if (= 400 (-> e ex-data :status))
    (resp/error (or user-message (.getMessage e)) 400)
    (do
      (log/error e user-message)
      (resp/server-error user-message))))

(defn list-championships
  "List all championships"
  [request]
  (try
    (let [status (get-in request [:params :status])
          championships (map enrich-championship-with-active-season (championships-db/find-all))
          championships (if status
                          (filter #(= (:status %) status) championships)
                          championships)]
      (resp/success (vec championships)))
    (catch Exception e
      (handle-exception e "Failed to list championships"))))

(defn get-championship
  "Get a single championship by ID"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [championship (championships-db/find-by-id id)]
        (resp/success (enrich-championship-with-active-season championship))
        (resp/not-found "Championship not found")))
    (catch Exception e
      (handle-exception e "Failed to get championship"))))

(defn create-championship
  "Create a new championship"
  [request]
  (try
    (let [championship-data (:json-body request)
          {:keys [error data]} (validate-championship-body championship-data)]
      (if error
        (resp/error error 400)
        (let [{:keys [name format season status start-date end-date location notes
                      titles-count max-players enrolled-player-ids winner-player-ids
                      finished-at]} data
              ;; Create championship root (no season/status stored here)
              root (championships-db/create {:name name :format format})
              ;; Create initial season linked to the root
              season-doc (seasons-db/create
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
                            ;; Bridge existing field name from UI/seed
                            :titles-award-count (or titles-count 0)
                            :titles-count (or titles-count 0)})
              _ (when (= "active" (:status season-doc))
                  (seasons-db/activate! (:_id season-doc)))
              enriched (enrich-championship-with-active-season root)]
          (resp/success enriched 201))))
    (catch Exception e
      (handle-exception e "Failed to create championship"))))

(defn update-championship
  "Update an existing championship"
  [request]
  (try
    (let [id (get-in request [:params :id])
          updates (:json-body request)
          {:keys [error data]} (validate-championship-body updates false)]
      (if error
        (resp/error error 400)
        (if-let [_root (championships-db/find-by-id id)]
          (do
            ;; Root fields
            (let [root-updates (select-keys data #{:name :format :notes :location})]
              (when (seq root-updates)
                (championships-db/update-by-id id root-updates)))

            ;; Active season fields
            (when-let [active-season (seasons-db/find-active-by-championship id)]
              (let [season-updates (select-keys data #{:season :status :start-date :end-date :max-players
                                                       :titles-count :titles-award-count :finished-at
                                                       :enrolled-player-ids :winner-player-ids :location :notes})]
                (when (seq season-updates)
                  (seasons-db/update-by-id (:_id active-season) season-updates)
                  (when (= "active" (:status season-updates))
                    (seasons-db/activate! (:_id active-season))))))

            (if-let [updated (championships-db/find-by-id id)]
              (resp/success (enrich-championship-with-active-season updated))
              (resp/server-error "Failed to retrieve updated championship")))
          (resp/not-found "Championship not found"))))
    (catch Exception e
      (handle-exception e "Failed to update championship"))))

(defn delete-championship
  "Delete a championship"
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if (championships-db/exists? id)
        (if (championships-db/has-matches? id)
          (resp/error "Cannot delete championship: it has associated matches. Please delete or reassign matches first." 409)
          (do
            ;; Best-effort: delete linked seasons
            (try
              (seasons-db/delete-by-championship id)
              (catch Exception _ nil))
            (championships-db/delete-by-id id)
            (resp/success {:message "Championship deleted"})))
        (resp/not-found "Championship not found")))
    (catch Exception e
      (handle-exception e "Failed to delete championship"))))

(defn enroll-player
  "Enroll a player in a championship"
  [request]
  (try
    (let [championship-id (get-in request [:params :id])
          player-id (get-in request [:params :player-id])]
      (if-not (and championship-id player-id)
        (resp/error "Championship ID and player ID are required" 400)
        (if-let [_player (players-db/find-by-id player-id)]
          (if-let [active-season (seasons-db/find-active-by-championship championship-id)]
            (let [enrolled (set (:enrolled-player-ids active-season []))
                  max-players (:max-players active-season)
                  already-enrolled? (contains? enrolled (resp/->object-id player-id))]
              (if (and max-players (>= (count enrolled) max-players) (not already-enrolled?))
                (resp/error "Season has reached maximum number of players" 409)
                (do
                  (seasons-db/add-player (:_id active-season) player-id)
                  (resp/success {:message "Player enrolled"}))))
            (if-let [championship (championships-db/find-by-id championship-id)]
              (let [enrolled (set (:enrolled-player-ids championship []))
                    max-players (:max-players championship)
                    already-enrolled? (contains? enrolled (resp/->object-id player-id))]
                (if (and max-players (>= (count enrolled) max-players) (not already-enrolled?))
                  (resp/error "Championship has reached maximum number of players" 409)
                  (do
                    (championships-db/add-player championship-id player-id)
                    (resp/success {:message "Player enrolled"}))))
              (resp/not-found "Championship not found")))
          (resp/not-found "Player not found"))))
    (catch Exception e
      (handle-exception e "Failed to enroll player"))))

(defn unenroll-player
  "Unenroll a player from a championship"
  [request]
  (try
    (let [championship-id (get-in request [:params :id])
          player-id (get-in request [:params :player-id])]
      (if-not (and championship-id player-id)
        (resp/error "Championship ID and player ID are required" 400)
        (if-let [active-season (seasons-db/find-active-by-championship championship-id)]
          (do
            (seasons-db/remove-player (:_id active-season) player-id)
            (resp/success {:message "Player unenrolled"}))
          (if (championships-db/exists? championship-id)
            (do
              (championships-db/remove-player championship-id player-id)
              (resp/success {:message "Player unenrolled"}))
            (resp/not-found "Championship not found")))))
    (catch Exception e
      (handle-exception e "Failed to unenroll player"))))

(defn get-championship-players
  "Get enrolled players for a championship.
  With an active season, returns only that season's roster.
  Otherwise returns distinct players enrolled in any season of this championship,
  union championship-root enrolled-player-ids (legacy)."
  [request]
  (try
    (let [championship-id (get-in request [:params :id])]
      (if-not (championships-db/exists? championship-id)
        (resp/not-found "Championship not found")
        (let [union-ids (roster/enrolled-player-object-ids championship-id)
              players (players-db/find-by-ids union-ids)
              players (sort-by (fn [p] (str/lower-case (str (:name p)))) players)]
          (resp/success players))))
    (catch Exception e
      (handle-exception e "Failed to list enrolled players"))))


(defn- parse-titles-award-count [v]
  (cond
    (nil? v) 1
    (number? v) (long v)
    (string? v) (let [trimmed (str/trim v)]
                  (if (str/blank? trimmed)
                    1
                    (Long/parseLong trimmed)))
    :else nil))

(defn finalize-championship
  "Finalize a championship and optionally award titles to winners.
   Payload: {:winner-player-ids [...] :titles-award-count N}.
   titles-award-count defaults to 1; 0 means finalize without incrementing titles."
  [request]
  (try
    (let [championship-id (get-in request [:params :id])
          body (get-in request [:json-body] {})
          winner-player-ids (mapv resp/->object-id (get body :winner-player-ids []))
          raw-count (get body :titles-award-count)]
      (if-let [active-season (seasons-db/find-active-by-championship championship-id)]
        (let [finished-at (:finished-at active-season)
              enrolled-ids (set (:enrolled-player-ids active-season []))
              not-enrolled (remove #(contains? enrolled-ids %) winner-player-ids)
              titles-award-count (parse-titles-award-count raw-count)]
          (cond
            finished-at
            (resp/error "Season has already been finalized" 400)

            (nil? titles-award-count)
            (resp/error "titles-award-count must be a non-negative number" 400)

            (neg? titles-award-count)
            (resp/error "titles-award-count must be non-negative" 400)

            (and (pos? titles-award-count) (empty? winner-player-ids))
            (resp/error "At least one winner must be specified when awarding titles" 400)

            (seq not-enrolled)
            (resp/error "Winners must be enrolled in the season" 400)

            :else
            (do
              (seasons-db/finalize! (:_id active-season) winner-player-ids titles-award-count)
              (resp/success {:message "Season finalized"}))))
        (if-let [championship (championships-db/find-by-id championship-id)]
          (let [status (or (:status championship) "active")
                finished-at (:finished-at championship)
                enrolled-ids (set (:enrolled-player-ids championship []))
                not-enrolled (remove #(contains? enrolled-ids %) winner-player-ids)
                titles-award-count (parse-titles-award-count raw-count)]
            (cond
              (not= status "active")
              (resp/error "Only active championships can be finalized" 400)

              finished-at
              (resp/error "Championship has already been finalized" 400)

              (nil? titles-award-count)
              (resp/error "titles-award-count must be a non-negative number" 400)

              (neg? titles-award-count)
              (resp/error "titles-award-count must be non-negative" 400)

              (and (pos? titles-award-count) (empty? winner-player-ids))
              (resp/error "At least one winner must be specified when awarding titles" 400)

              (seq not-enrolled)
              (resp/error "Winners must be enrolled in the championship" 400)

              :else
              (do
                (championships-db/update-by-id championship-id
                                               {:status "completed"
                                                :finished-at (java.util.Date.)
                                                :winner-player-ids winner-player-ids
                                                :titles-award-count titles-award-count})
                (when (pos? titles-award-count)
                  (players-db/increment-titles winner-player-ids titles-award-count))
                (resp/success {:message "Championship finalized"}))))
          (resp/not-found "Championship not found"))))
    (catch NumberFormatException _
      (resp/error "titles-award-count must be a valid number" 400))
    (catch Exception e
      (handle-exception e "Failed to finalize championship"))))
