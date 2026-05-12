(ns galaticos.db.matches
  "Database operations for matches collection.
  
  Incremental data preservation:
  - Uses atomic MongoDB operators ($set, $setOnInsert, $inc, $push)
  - Never overwrites protected fields (created-at, created-by, data-source)
  - Historical data from Python seed is preserved unless explicitly commanded"
  (:require [clojure.string :as str]
            [monger.collection :as mc]
            [monger.operators :refer [$set $setOnInsert $inc $push]]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]])
  (:import [org.bson.types ObjectId]))

(def collection-name "matches")

(def data-source-ui-create "ui-create")
(def data-source-ui-update "ui-update")
(def data-source-python-seed "python-seed")

(def ^:private protected-fields
  "Fields that should never be overwritten by updates"
  #{:_id :created-at :created-by :data-source :version})

(defn- safe-stat-int
  "Coerce match stat field to int for BSON ($sum) and home-score. Mirrors aggregation semantics: nil/bad -> 0."
  [value]
  (cond
    (number? value) (int (long value))
    (nil? value) 0
    (string? value)
    (let [s (str/trim value)]
      (if (str/blank? s)
        0
        (try (int (long (Double/parseDouble s)))
             (catch Exception _ 0))))
    :else
    (try (int (long value))
         (catch Exception _ 0))))

(defn normalize-player-statistics
  "Force goals/assists/cards/minutes to numeric ints on each player line (API may send strings).
  Use from maintenance tasks; create/update call this before persisting."
  [player-statistics]
  (if-not (sequential? player-statistics)
    player-statistics
    (mapv
     (fn [row]
       (if (map? row)
         (merge row
                {:goals (safe-stat-int (:goals row))
                 :assists (safe-stat-int (:assists row))
                 :yellow-cards (safe-stat-int (:yellow-cards row))
                 :red-cards (safe-stat-int (:red-cards row))
                 :minutes-played (safe-stat-int (:minutes-played row))})
         row))
     player-statistics)))

(defn- sum-goals [team-id player-statistics]
  (if team-id
    (reduce + 0 (map #(or (:goals %) 0)
                     (filter #(= (:team-id %) team-id) player-statistics)))
    0))

(defn- calculate-scores
  "home-score is always calculated from our team's goals; away-score is manual (no opponent stats)."
  [match-data player-statistics]
  (let [home-team-id (:home-team-id match-data)
        home-score (if (seq player-statistics)
                     (sum-goals home-team-id player-statistics)
                     0)
        ;; away-score: use value from request when provided (single-team platform), else 0
        away-score (if (some? (:away-score match-data))
                     (safe-stat-int (or (:away-score match-data) 0))
                     0)]
    {:home-score home-score
     :away-score away-score}))

(defn create
  "Create a new match with player statistics using atomic upsert.
  
  Options:
    :created-by - ObjectId of admin creating the match (from auth context)
    :data-source - Source identifier (defaults to ui-create)
  
  Uses $setOnInsert for immutable fields (created-at, created-by, data-source, version)
  and $set for mutable fields. Upsert ensures idempotency."
  ([match-data player-statistics]
   (create match-data player-statistics {}))
  ([match-data player-statistics {:keys [created-by data-source]}]
   (let [player-statistics (normalize-player-statistics player-statistics)
         now (java.util.Date.)
         id (ObjectId.)
         scores (calculate-scores match-data player-statistics)
         source (or data-source data-source-ui-create)
         mutable-fields (merge (dissoc match-data :_id :created-at :created-by :data-source :version)
                               scores
                               {:player-statistics player-statistics
                                :updated-at now})
         immutable-fields (cond-> {:_id id
                                   :created-at now
                                   :data-source source
                                   :version 1}
                            created-by (assoc :created-by created-by))]
     (mc/update (db) collection-name
                {:_id id}
                {$setOnInsert immutable-fields
                 $set mutable-fields}
                {:upsert true})
     (merge immutable-fields mutable-fields))))

(defn find-by-id
  "Find match by ID"
  [id]
  (mc/find-one-as-map (db) collection-name {:_id (->object-id id)}))

(defn find-all
  "Find all matches, optionally filtered"
  ([]
   (find-all {}))
  ([filters]
   (mc/find-maps (db) collection-name filters)))

(defn find-by-championship
  "Find matches by championship ID, ordered by date descending"
  [championship-id]
  (sort-by :date #(compare %2 %1)
           (mc/find-maps (db) collection-name
                         {:championship-id (->object-id championship-id)})))

(defn find-by-season
  "Find matches by season ID, ordered by date descending"
  [season-id]
  (sort-by :date #(compare %2 %1)
           (mc/find-maps (db) collection-name
                         {:season-id (->object-id season-id)})))

(defn find-by-date-range
  "Find matches within date range"
  [start-date end-date]
  (find-all {:date {:$gte start-date :$lte end-date}}))

(defn find-by-player
  "Find matches where player participated"
  [player-id]
  (find-all {"player-statistics.player-id" (->object-id player-id)}))

(defn update-by-id
  "Update match by ID using atomic $set.
  
  Only updates fields explicitly provided in updates map.
  Protected fields (created-at, created-by, data-source, version) are never overwritten.
  Player-statistics is normalized before persisting.
  
  Options:
    :force-overwrite - If true, allows updating protected data-source (use with caution)"
  ([id updates]
   (update-by-id id updates {}))
  ([id updates {:keys [force-overwrite]}]
   (let [safe-updates (if force-overwrite
                        (dissoc updates :_id :created-at :version)
                        (apply dissoc updates protected-fields))
         safe-updates (if (contains? safe-updates :player-statistics)
                        (update safe-updates :player-statistics
                                (fn [ps] (if (sequential? ps) (normalize-player-statistics ps) ps)))
                        safe-updates)
         match (when (or (contains? safe-updates :player-statistics)
                         (contains? safe-updates :home-team-id)
                         (contains? safe-updates :away-score))
                 (find-by-id id))
         merged-for-scores (merge (select-keys (or match {}) [:home-team-id :away-team-id :player-statistics :away-score])
                                  safe-updates)
         scores (when (or (contains? safe-updates :player-statistics)
                          (contains? safe-updates :away-score))
                  (calculate-scores merged-for-scores (:player-statistics merged-for-scores)))
         final-updates (merge safe-updates scores {:updated-at (java.util.Date.)})]
     (mc/update (db) collection-name
                {:_id (->object-id id)}
                {$set final-updates}))))

(defn increment-player-stats
  "Atomically increment player statistics using positional $ operator.
  
  Only increments for player already in player-statistics array.
  Returns true if player was found and updated, false otherwise."
  [match-id player-id deltas]
  (let [oid (->object-id match-id)
        pid (->object-id player-id)
        inc-fields (into {}
                         (for [[k v] deltas
                               :when (and v (pos? v))]
                           [(str "player-statistics.$." (name k)) v]))]
    (if (empty? inc-fields)
      false
      (let [result (mc/update (db) collection-name
                              {:_id oid "player-statistics.player-id" pid}
                              {$inc inc-fields
                               $set {:updated-at (java.util.Date.)}})]
        (pos? (.getN result))))))

(defn add-player-to-match
  "Atomically add a new player to match's player-statistics array.
  
  Uses $push to append player. Does not check for duplicates - caller
  should verify player is not already in the match."
  [match-id player-stat]
  (let [oid (->object-id match-id)
        normalized (first (normalize-player-statistics [player-stat]))]
    (mc/update (db) collection-name
               {:_id oid}
               {$push {:player-statistics normalized}
                $set {:updated-at (java.util.Date.)}})))

(defn upsert-player-stats
  "Atomically update or add player statistics.
  
  If player exists in match, increments their stats.
  If player does not exist, adds them to the match.
  Returns :updated, :added, or :error."
  [match-id player-stat]
  (let [player-id (:player-id player-stat)
        match (find-by-id match-id)]
    (cond
      (nil? match)
      :error

      (some #(= (:player-id %) player-id) (:player-statistics match))
      (do
        (increment-player-stats match-id player-id
                                (select-keys player-stat [:goals :assists :yellow-cards :red-cards :minutes-played]))
        :updated)

      :else
      (do
        (add-player-to-match match-id player-stat)
        :added))))

(defn is-historical-data?
  "Check if match was created from Python seed (historical data).
  
  Historical data should not be overwritten by normal UI operations."
  [match-id]
  (let [match (find-by-id match-id)]
    (= (:data-source match) data-source-python-seed)))

(defn delete-by-id
  "Delete match by ID"
  [id]
  (mc/remove (db) collection-name {:_id (->object-id id)}))

(defn exists?
  "Check if match exists"
  [id]
  (some? (find-by-id id)))

