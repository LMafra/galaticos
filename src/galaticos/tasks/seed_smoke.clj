(ns galaticos.tasks.seed-smoke
  "Deterministic, minimal seed for smoke/E2E tests.

  Creates/ensures:
  - admin/admin user
  - a single team, championship, player, and match with non-empty stats"
  (:require [clojure.tools.logging :as log]
            [galaticos.db.core :as db]
            [galaticos.db.admins :as admins]
            [galaticos.db.championships :as championships]
            [galaticos.db.matches :as matches]
            [galaticos.db.players :as players]
            [galaticos.db.teams :as teams]
            [monger.collection :as mc])
  (:gen-class))

(def ^:private admin-username "admin")
(def ^:private admin-password "admin")

(def ^:private team-name "Galáticos")
(def ^:private championship-name "Smoke Championship")
(def ^:private championship-season "2025")
(def ^:private player-name "Smoke Player")

(def ^:private match-opponent "Smoke Opponent")

(defn- clear-database! []
  ;; Remove all documents from seed-related collections so smoke data does not accumulate
  ;; with production or previous runs.
  (doseq [coll ["teams" "players" "championships" "seasons" "matches" "admins"]]
    (mc/remove (db/db) coll {}))
  (log/info "Database cleared before smoke seed"))

(defn- ensure-admin! []
  (if (admins/exists? admin-username)
    (do
      (log/info "Admin exists; ensuring password is set to smoke default")
      (admins/update-password admin-username admin-password)
      (admins/find-by-username admin-username))
    (do
      (log/info "Creating smoke admin user")
      (admins/create admin-username admin-password))))

(defn- ensure-team! []
  (or (teams/find-by-name team-name)
      (do
        (log/info "Creating smoke team:" team-name)
        (teams/create {:name team-name}))))

(defn- ensure-championship! []
  (or (championships/find-by-name-and-season championship-name championship-season)
      (do
        (log/info "Creating smoke championship:" championship-name championship-season)
        (championships/create {:name championship-name
                               :season championship-season
                               :format "society-7"
                               :status "active"
                               :titles-count 0
                               :start-date (java.util.Date.)
                               :end-date (java.util.Date.)}))))

(defn- ensure-player! [team-id championship-id]
  (let [existing (mc/find-one-as-map (db/db) "players" {:name player-name})
        player (or existing
                   (do
                     (log/info "Creating smoke player:" player-name)
                     (players/create {:name player-name
                                      :nickname "Smoke"
                                      :position "Atacante"
                                      :team-id team-id})))]
    ;; Ensure team references player
    (try
      (teams/add-player (:_id (teams/find-by-name team-name)) (:_id player))
      (catch Exception e
        (log/warn e "Failed to add player to team (non-fatal)")))
    ;; Ensure aggregated stats are non-empty so dashboard has data
    (players/update-stats (:_id player)
                          {:total {:games 1 :goals 1 :assists 0 :titles 0}
                           :by-championship [{:championship-id championship-id
                                              :championship-name championship-name
                                              :games 1
                                              :goals 1
                                              :assists 0
                                              :titles 0}]})
    (players/find-by-id (:_id player))))

(defn- ensure-match! [championship-id player]
  ;; Fixed-ish timestamp to keep idempotency; any stable Date works.
  (let [match-date (java.util.Date. 1735689600000) ;; 2025-01-01T00:00:00Z
        existing (mc/find-one-as-map (db/db) "matches"
                                     {:championship-id championship-id
                                      :opponent match-opponent
                                      :date match-date})]
    (or existing
        (do
          (log/info "Creating smoke match:" match-opponent)
          (matches/create {:championship-id championship-id
                           :date match-date
                           :opponent match-opponent
                           :venue "Smoke Arena"
                           :result {:our-score 1 :opponent-score 0 :outcome "win"}}
                          [{:player-id (:_id player)
                            :player-name (:name player)
                            :position (:position player)
                            :goals 1
                            :assists 0
                            :yellow-cards 0
                            :red-cards 0
                            :minutes-played 60
                            :substituted false}])))))

(defn -main [& _args]
  (let [conn-result (db/connect!)]
    (if (= (:status conn-result) :error)
      (do
        (binding [*out* *err*]
          (println "Failed to connect to MongoDB:" (:message conn-result)))
        (System/exit 1))
      (try
        (log/info "Seeding smoke dataset...")
        (clear-database!)
        (ensure-admin!)
        (let [team (ensure-team!)
              championship (ensure-championship!)
              player (ensure-player! (:_id team) (:_id championship))
              match (ensure-match! (:_id championship) player)]
          (log/info "Smoke seed complete"
                    {:admin admin-username
                     :team-id (str (:_id team))
                     :championship-id (str (:_id championship))
                     :player-id (str (:_id player))
                     :match-id (str (:_id match))}))
        (finally
          (db/disconnect!))))))


