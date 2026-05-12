(ns galaticos.handlers.player-merge
  "Duplicate detection and transactional player merge (admin)."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [environ.core :refer [env]]
            [galaticos.championship.roster :as roster]
            [galaticos.db.admins :as admins]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.merge-audit :as merge-audit]
            [galaticos.db.player-merge-refs :as refs]
            [galaticos.db.players :as players-db]
            [galaticos.db.teams :as teams-db]
            [galaticos.middleware.auth :as auth]
            [galaticos.util.fuzzy :as fuzzy]
            [galaticos.util.response :as resp]))

(def ^:private mergeable-fields
  #{:name :nickname :position :team-id :birth-date :nationality :height :weight
    :preferred-foot :shirt-number :active :email :phone :number :photo-url :notes
    :aggregated-stats})

(defonce ^:private root-config
  (try
    (when-let [res (io/resource "config.edn")]
      (with-open [r (io/reader res)]
        (edn/read (java.io.PushbackReader. r))))
    (catch Exception _ nil)))

(defn- fuzzy-threshold []
  (let [raw (or (some-> (env :fuzzy-match-threshold) str)
                (System/getenv "FUZZY_MATCH_THRESHOLD"))
        from-config (some-> root-config :fuzzy-match :threshold double)]
    (or (when-not (str/blank? raw)
          (try (Double/parseDouble (str/trim raw))
               (catch Exception _ nil)))
        from-config
        0.85)))

(defn- query-string [request k]
  (some-> (or (get-in request [:query-params k])
              (get-in request [:query-params (name k)]))
          str
          str/trim
          not-empty))

(defn list-player-duplicates
  "GET /api/players/duplicates — fuzzy duplicate pairs.
  Optional query championship-id: only players enrolled in that championship."
  [request]
  (try
    (let [thr (fuzzy-threshold)
          cid (query-string request "championship-id")
          players (if cid
                    (let [ids (roster/enrolled-player-object-ids cid)]
                      (if (empty? ids)
                        []
                        (players-db/find-by-ids ids)))
                    (players-db/find-all {}))
          report (fuzzy/duplicate-report-for-players players thr)]
      (resp/success report))
    (catch Exception e
      (log/error e "list-player-duplicates")
      (resp/server-error "Failed to list duplicate players"))))

(defn- parse-min-similarity [request]
  (when-let [raw (or (query-string request "min-similarity")
                     (some-> (get-in request [:params :min-similarity]) str str/trim not-empty))]
    (try (Double/parseDouble raw)
         (catch Exception _ nil))))

(defn list-merge-candidates
  "GET /api/players/:id/merge-candidates — fuzzy-ranked other players vs anchor name (auth).
  Optional championship-id limits pool to that championship's enrolled roster; anchor must be in pool."
  [request]
  (try
    (let [player-id (some-> (get-in request [:params :id]) str str/trim not-empty)
          cid (query-string request "championship-id")
          thr (or (parse-min-similarity request) (fuzzy-threshold))
          anchor (when player-id (players-db/find-by-id player-id))]
      (when-not player-id
        (throw (ex-info "Player id is required" {:status 400})))
      (when-not anchor
        (throw (ex-info "Player not found" {:status 404})))
      (let [anchor-oid (:_id anchor)
            pool (if cid
                   (let [ids (roster/enrolled-player-object-ids cid)]
                     (when (empty? ids)
                       (throw (ex-info "No enrolled players in this championship" {:status 400})))
                     (when-not (some #(= anchor-oid %) ids)
                       (throw (ex-info "Player not enrolled in this championship" {:status 400})))
                     (players-db/find-by-ids ids))
                   (players-db/find-all {}))
            anchor-nm (fuzzy/normalize-for-match (:name anchor))
            rows (->> pool
                      (remove #(= anchor-oid (:_id %)))
                      (keep (fn [p]
                              (when-let [pn (fuzzy/normalize-for-match (:name p))]
                                (let [sim (fuzzy/effective-name-similarity anchor-nm pn)]
                                  (when (>= sim thr)
                                    {:id (str (:_id p))
                                     :name (:name p)
                                     :similarity (/ (Math/round (* sim 10000.0)) 10000.0)
                                     :nickname (:nickname p)
                                     :position (:position p)
                                     :shirt-number (:shirt-number p)})))))
                      (sort-by :similarity >)
                      vec)]
        (resp/success rows)))
    (catch clojure.lang.ExceptionInfo e
      (let [msg (or (.getMessage e) "Bad request")
            st (:status (ex-data e) 400)]
        (if (= 404 st)
          (resp/not-found msg)
          (resp/error msg st))))
    (catch Exception e
      (log/error e "list-merge-candidates")
      (resp/server-error "Failed to list merge candidates"))))

(defn- parse-source [v]
  (let [s (str/trim (str v))]
    (cond
      (= "master" s) {:kind :master}
      (= "combined" s) {:kind :combined}
      :else
      (when-let [[_ idx] (re-find #"^merged-(\d+)$" s)]
        {:kind :merged :idx (Long/parseLong idx)}))))

(defn- merged-docs-in-order [merged-ids]
  (mapv (fn [id]
          (or (players-db/find-by-id id)
              (throw (ex-info "Merged player not found" {:status 400 :id id}))))
        merged-ids))

(defn- resolve-field [field-key source master merged-players]
  (case (:kind source)
    :master (get master field-key)
    :merged (let [i (:idx source)]
              (when (or (< i 0) (>= i (count merged-players)))
                (throw (ex-info "Invalid merged index in field selection" {:status 400 :field field-key})))
              (get (nth merged-players i) field-key))
    :combined (if (= :aggregated-stats field-key)
                (or (agg/combine-players-aggregated-stats (cons master merged-players))
                    (throw (ex-info "Could not combine aggregated-stats" {:status 400})))
                (throw (ex-info "'combined' is only valid for aggregated-stats" {:status 400})))))

(defn- build-merge-updates [field-selections master merged-players]
  (reduce-kv
   (fn [acc raw-k sel]
     (let [kw (cond
                (keyword? raw-k) raw-k
                (string? raw-k) (keyword raw-k)
                :else (keyword (str raw-k)))
           src (parse-source sel)]
       (when-not src
         (throw (ex-info (str "Invalid selection for field " kw) {:status 400})))
       (when-not (mergeable-fields kw)
         (throw (ex-info (str "Unsupported merge field: " (name kw)) {:status 400})))
       (assoc acc kw (resolve-field kw src master merged-players))))
   {}
   field-selections))

(defn- normalize-merge-updates [updates]
  (cond-> updates
    (:team-id updates) (update :team-id #(when % (resp/->object-id %)))))

(defn- display-name-for-stats [updates master]
  (or (:name updates) (:name master) "Player"))

(defn- sync-master-team-membership!
  [master-id-str master-oid final-team-id]
  (let [final-oid (when final-team-id (resp/->object-id final-team-id))]
    (doseq [team (teams-db/find-all)]
      (when-let [ids (:active-player-ids team)]
        (when (some #(= master-oid (resp/->object-id %)) ids)
          (when-not (and final-oid (= (:_id team) final-oid))
            (teams-db/remove-player (:_id team) master-id-str)))))
    (when final-oid
      (teams-db/add-player final-team-id master-id-str))))

(defn- audit-snapshot [players]
  (mapv #(select-keys % [:_id :name :nickname :position :team-id :aggregated-stats :active]) players))

(defn merge-players
  "POST /api/players/merge — merge duplicate player docs into master (admin).
  Note: MongoDB multi-document ACID transactions require a replica set; this handler
  runs ordered updates/deletes without a session (see player_merge_refs + audit)."
  [request]
  (try
    (let [body (walk/keywordize-keys (or (:json-body request) {}))
          master-id (some-> (:master-id body) str str/trim not-empty)
          merged-ids (vec (distinct (map str (:merged-ids body))))
          field-selections (:field-selections body)]
      (when-not master-id
        (throw (ex-info "master-id is required" {:status 400})))
      (when (empty? merged-ids)
        (throw (ex-info "merged-ids must not be empty" {:status 400})))
      (when (some #{master-id} merged-ids)
        (throw (ex-info "master-id must not appear in merged-ids" {:status 400})))
      (when-not (map? field-selections)
        (throw (ex-info "field-selections map is required" {:status 400})))
      (let [master (or (players-db/find-by-id master-id)
                       (throw (ex-info "Master player not found" {:status 404})))
            merged-players (merged-docs-in-order merged-ids)
            master-oid (resp/->object-id master-id)
            updates (-> (build-merge-updates field-selections master merged-players)
                        normalize-merge-updates)
            display-name (display-name-for-stats updates master)
            final-team-id (or (:team-id updates) (:team-id master))
            username (auth/current-user request)
            admin (when username (admins/find-by-username username))
            before-state {:master (audit-snapshot [master])
                          :merged (audit-snapshot merged-players)}]
        (refs/rewrite-all-player-refs! master-id merged-ids display-name)
        (players-db/update-by-id master-id updates)
        (sync-master-team-membership! master-id master-oid final-team-id)
        (doseq [mid merged-ids]
          (players-db/hard-delete-by-id! mid))
        (try
          (agg/update-incremental-player-stats! [master-id]
                                                {:zero-if-no-matches? false
                                                 :drop-stale-without-match-rollups? false})
          (catch Exception e
            (log/warn e "incremental stats refresh after player merge")))
        (let [merged-player (players-db/find-by-id master-id)
              after-state {:master (audit-snapshot [merged-player])}
              audit (merge-audit/create!
                     {:master-id master-id
                      :merged-ids merged-ids
                      :field-selections field-selections
                      :admin-id (:_id admin)
                      :admin-username username
                      :before-state before-state
                      :after-state after-state})]
          (resp/success {:merged-player merged-player
                         :audit-id (str (:_id audit))}))))
    (catch clojure.lang.ExceptionInfo e
      (let [msg (or (.getMessage e) "Merge failed")
            st (:status (ex-data e) 400)]
        (if (= 404 st)
          (resp/not-found msg)
          (resp/error msg st))))
    (catch Exception e
      (log/error e "merge-players")
      (resp/server-error "Failed to merge players"))))
