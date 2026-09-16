(ns galaticos.logic.matches-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [galaticos.analytics.player-stats-jobs :as player-stats-jobs]
            [galaticos.logic.matches :as logic]
            [galaticos.support.match-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(defn- active-season-store
  [champ-id team-id player-id season-id & {:keys [players teams find-season-for-new-match
                                                  create-match add-match-to-season!]
                                           :as extra}]
  (let [season {:_id season-id :status "active" :enrolled-player-ids [player-id]}
        default-find-team (fn [tid]
                            (when (= tid team-id)
                              {:_id team-id :name "T" :active-player-ids [player-id]}))
        find-team (or teams default-find-team)
        base {:find-season-for-new-match (or find-season-for-new-match
                                            (fn [_ _ _] season))
              :find-default-season-for-championship (fn [_ _] season)
              :find-season-by-id (fn [_ sid] (when (= sid season-id) season))
              :find-championship-by-id (fn [_ _] {:enrolled-player-ids [player-id]})
              :find-players-by-ids (or players
                                       (fn [_ _] [{:_id player-id :name "P" :team-id team-id}]))
              :find-team-by-id find-team
              :find-teams-by-ids (fixtures/teams-by-id-fn find-team)
              :create-match (or create-match
                                (fn [_ _ _ _] {:_id (ObjectId.)
                                               :championship-id champ-id
                                               :season-id season-id}))
              :add-match-to-season! (or add-match-to-season! (fn [_ _ _] nil))}]
    (fixtures/match-store (merge base (dissoc extra
                                              :players :teams :find-season-for-new-match
                                              :create-match :add-match-to-season!)))))

(deftest create-rejects-no-active-season
  (let [champ-id (ObjectId.)
        team-id (ObjectId.)
        player-id (ObjectId.)
        store (fixtures/match-store
                 {:find-season-for-new-match (fn [_ _ _] nil)})]
    (try
      (logic/create! store {} {:championship-id champ-id
                               :player-statistics [{:player-id player-id :team-id team-id}]})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 400 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "No active season"))))))

(deftest create-rejects-completed-season
  (let [champ-id (ObjectId.)
        team-id (ObjectId.)
        player-id (ObjectId.)
        season-id (ObjectId.)
        completed {:_id season-id :status "completed"}
        store (fixtures/match-store
                 {:find-season-for-new-match (fn [_ _ sid] (when (= sid season-id) completed))
                  :find-season-by-id (fn [_ sid] (when (= sid season-id) completed))})]
    (try
      (logic/create! store {} {:championship-id champ-id
                               :season-id season-id
                               :player-statistics [{:player-id player-id :team-id team-id}]})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 403 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "completed season"))))))

(deftest create-rejects-player-not-found
  (let [champ-id (ObjectId.)
        team-id (ObjectId.)
        player-id (ObjectId.)
        season-id (ObjectId.)
        store (active-season-store champ-id team-id player-id season-id
                                   :players (fn [_ _] []))]
    (try
      (logic/create! store {} {:championship-id champ-id
                               :player-statistics [{:player-id player-id :team-id team-id}]})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 400 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "Player not found"))))))

(deftest create-rejects-wrong-team-id
  (let [champ-id (ObjectId.)
        team-id (ObjectId.)
        other-team (ObjectId.)
        player-id (ObjectId.)
        season-id (ObjectId.)
        store (active-season-store champ-id team-id player-id season-id
                                   :players (fn [_ _] [{:_id player-id :name "P" :team-id other-team}]))]
    (try
      (logic/create! store {} {:championship-id champ-id
                               :player-statistics [{:player-id player-id :team-id team-id}]})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 400 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "Invalid team-id"))))))

(deftest create-success-submits-recalc-and-attaches-season
  (let [champ-id (ObjectId.)
        team-id (ObjectId.)
        player-id (ObjectId.)
        season-id (ObjectId.)
        created {:_id (ObjectId.)}
        add-match-called (atom false)
        recalc-intent (atom nil)
        store (active-season-store champ-id team-id player-id season-id
                                   :create-match (fn [_ _ _ _] created)
                                   :add-match-to-season!
                                   (fn [_ sid mid]
                                     (when (and (= sid season-id) (= mid (:_id created)))
                                       (reset! add-match-called true))))]
    (with-redefs [player-stats-jobs/submit-incremental-recalc-after-match!
                  (fn [intent] (reset! recalc-intent intent))]
      (let [result (logic/create! store {}
                                  {:championship-id champ-id
                                   :player-statistics [{:player-id player-id :team-id team-id}]})]
        (is (= (:_id created) (:_id result)))
        (is (true? @add-match-called))
        (is (= :after-match-create (:reason @recalc-intent)))
        (is (= :create (:crud-op @recalc-intent)))))))

(deftest update-not-found
  (let [id (str (ObjectId.))
        store (fixtures/match-store {:match-exists? (fn [_ _] false)})]
    (try
      (logic/update! store id {} {})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 404 (-> e ex-data :status)))))))

(deftest update-success-on-completed-season
  (let [id (str (ObjectId.))
        mid (ObjectId. id)
        champ-id (ObjectId.)
        team-id (ObjectId.)
        player-id (ObjectId.)
        season-id (ObjectId.)
        existing {:_id mid :championship-id champ-id :season-id season-id
                  :player-statistics [{:player-id player-id :team-id team-id}]}
        add-called (atom false)
        store (active-season-store champ-id team-id player-id season-id
                                   :find-season-for-new-match (fn [_ _ _] {:_id season-id :status "completed"})
                                   :find-season-by-id (fn [_ sid] (when (= sid season-id)
                                                                    {:_id season-id :status "completed"
                                                                     :enrolled-player-ids [player-id]}))
                                   :match-exists? (fn [_ x] (= x id))
                                   :find-match-by-id (fn [_ x] (when (= x id) existing))
                                   :update-match-by-id (fn [_ _ _ _] nil)
                                   :add-match-to-season!
                                   (fn [_ sid m]
                                     (when (= sid season-id)
                                       (reset! add-called (= m mid)))))]
    (with-redefs [player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
      (let [result (logic/update! store id {} {:notes "x"})]
        (is (= mid (:_id result)))
        (is (true? @add-called))))))

(deftest delete-python-seed-without-force
  (let [id (str (ObjectId.))
        mid (ObjectId. id)
        store (fixtures/match-store
                 {:match-exists? (fn [_ x] (= x id))
                  :find-match-by-id (fn [_ x]
                                      (when (= x id)
                                        {:_id mid :data-source "python-seed"}))
                  :delete-match-by-id (fn [_ _] (throw (Exception. "should not run")))})]
    (try
      (logic/delete! store id {:params {}})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 403 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "python-seed"))))))

(deftest delete-success-removes-from-season
  (let [id (str (ObjectId.))
        mid (ObjectId. id)
        season-id (ObjectId.)
        removed (atom false)
        store (fixtures/match-store
                 {:match-exists? (fn [_ x] (= x id))
                  :find-match-by-id (fn [_ _] {:_id mid :season-id season-id :player-statistics []})
                  :delete-match-by-id (fn [_ _] nil)
                  :remove-match-from-season!
                  (fn [_ sid m]
                    (when (and (= sid season-id) (= m mid))
                      (reset! removed true)))})]
    (with-redefs [player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
      (is (= "Match deleted" (:message (logic/delete! store id {:params {}}))))
      (is (true? @removed)))))

(deftest get-by-id-enriches-player-and-team-names
  (let [pid (ObjectId.)
        tid (ObjectId.)
        id (str (ObjectId.))
        match {:_id (ObjectId. id)
               :player-statistics [{:player-id pid :team-id tid :goals 1}]}
        store (fixtures/match-store
                 {:find-match-by-id (fn [_ x] (when (= x id) match))
                  :find-players-by-ids (fn [_ _] [{:_id pid :name "João"}])
                  :find-teams-by-ids (fn [_ _] [{:_id tid :name "Time A"}])})]
    (let [result (logic/get-by-id store id)]
      (is (= "João" (get-in result [:player-statistics 0 :player-name])))
      (is (= "Time A" (get-in result [:player-statistics 0 :team-name]))))))

(deftest list-matches-by-season
  (let [sid (str (ObjectId.))
        store (fixtures/match-store
                 {:find-matches-by-season (fn [_ x] (when (= x sid) [{:_id "m1"}]))})]
    (is (= 1 (count (logic/list-matches store {:params {:season-id sid}}))))))
