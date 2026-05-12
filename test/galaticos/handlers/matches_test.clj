(ns galaticos.handlers.matches-test
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.matches :as handlers]
            [galaticos.db.matches :as matches-db]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.db.seasons :as seasons-db]
            [galaticos.db.teams :as teams-db]
            [galaticos.analytics.player-stats-jobs :as player-stats-jobs])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(deftest list-matches
  (testing "all"
    (let [request {:params {}}
          result (with-redefs [matches-db/find-all (fn [] [])]
                  (handlers/list-matches request))]
      (is (= 200 (:status result)))))
  (testing "by championship-id"
    (let [champ-id (str (ObjectId.))
          request {:params {:championship-id champ-id}}
          result (with-redefs [matches-db/find-by-championship (fn [_] [])]
                  (handlers/list-matches request))]
      (is (= 200 (:status result)))))
  (testing "by season-id"
    (let [sid (str (ObjectId.))
          request {:params {:season-id sid}}
          result (with-redefs [matches-db/find-by-season (fn [x] (when (= x sid) [{:_id "m1"}]))]
                  (handlers/list-matches request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= 1 (count (:data body)))))))

(deftest get-match
  (testing "found"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          match {:_id (ObjectId. id) :date "2024-01-01"}
          result (with-redefs [matches-db/find-by-id (fn [x] (when (= x id) match))]
                  (handlers/get-match request))]
      (is (= 200 (:status result)))))
  (testing "enriches player-name and team-name on player-statistics"
    (let [pid (ObjectId.)
          tid (ObjectId.)
          id (str (ObjectId.))
          request {:params {:id id}}
          match {:_id (ObjectId. id)
                 :player-statistics [{:player-id pid :team-id tid :goals 1}]}
          result (with-redefs [matches-db/find-by-id (fn [x] (when (= x id) match))
                               players-db/find-by-ids (fn [_ids] [{:_id pid :name "João"}])
                               teams-db/find-by-ids (fn [_ids] [{:_id tid :name "Time A"}])]
                  (handlers/get-match request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "João" (get-in body [:data :player-statistics 0 :player-name])))
      (is (= "Time A" (get-in body [:data :player-statistics 0 :team-name])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [matches-db/find-by-id (fn [_] nil)]
                  (handlers/get-match request))]
      (is (= 404 (:status result))))))

(deftest create-match
  (testing "invalid body - unknown fields"
    (let [cid (str (ObjectId.))
          pid (str (ObjectId.))
          tid (str (ObjectId.))
          request {:json-body {:championship-id cid
                               :player-statistics [{:player-id pid :team-id tid}]
                               :not-allowed 1}}
          result (handlers/create-match request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "Unknown fields"))))
  (testing "invalid body - missing required"
    (let [request {:json-body {:championship-id (str (ObjectId.))}}
          result (handlers/create-match request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "player-statistics"))))
  (testing "player-statistics not a vector"
    (let [request {:json-body {:championship-id (str (ObjectId.))
                               :player-statistics "bad"}}
          result (handlers/create-match request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "player-statistics"))))
  (testing "player not found for team coherence"
    (let [champ-id (ObjectId.)
          team-id (ObjectId.)
          player-id (ObjectId.)
          request {:json-body {:championship-id (str champ-id)
                               :player-statistics [{:player-id (str player-id) :team-id (str team-id)}]}}
          result (with-redefs [seasons-db/find-active-by-championship (fn [_] nil)
                               championships-db/find-by-id (fn [_]
                                                             {:enrolled-player-ids [player-id]})
                               players-db/find-by-ids (fn [_] [])
                               teams-db/find-by-id (fn [_] {:_id team-id :active-player-ids [player-id]})]
                  (handlers/create-match request))
          body (parse-body result)]
      (is (= 400 (:status result)))
      ;; handle-exception prefers user-message over ex-message for {:status 400}
      (is (str/includes? (:error body) "Failed to create match"))))
  (testing "wrong team-id for player"
    (let [champ-id (ObjectId.)
          team-id (ObjectId.)
          other-team (ObjectId.)
          player-id (ObjectId.)
          request {:json-body {:championship-id (str champ-id)
                               :player-statistics [{:player-id (str player-id) :team-id (str team-id)}]}}
          result (with-redefs [seasons-db/find-active-by-championship (fn [_] nil)
                               championships-db/find-by-id (fn [_]
                                                             {:enrolled-player-ids [player-id]})
                               players-db/find-by-ids
                               (fn [_] [{:_id player-id :name "P" :team-id other-team}])
                               teams-db/find-by-id (fn [tid]
                                                     (when (= tid team-id)
                                                       {:_id team-id :active-player-ids [player-id]}))]
                  (handlers/create-match request))
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (str/includes? (:error body) "Failed to create match"))))
  (testing "success with minimal player-statistics"
    (let [champ-id (ObjectId.)
          team-id (ObjectId.)
          player-id (ObjectId.)
          request {:json-body {:championship-id (str champ-id)
                               :player-statistics [{:player-id (str player-id) :team-id (str team-id)}]}}
          created {:_id (ObjectId.)}
          result (with-redefs [seasons-db/find-active-by-championship (fn [_] nil)
                               championships-db/find-by-id (fn [x]
                                                             (when (or (= x (str champ-id))
                                                                       (= x champ-id))
                                                               {:enrolled-player-ids [player-id]}))
                               players-db/find-by-ids
                               (fn [_ids]
                                 [{:_id player-id :name "P" :team-id team-id}])
                               teams-db/find-by-id
                               (fn [tid]
                                 (when (= tid team-id)
                                   {:_id team-id :name "T" :active-player-ids [player-id]}))
                               matches-db/create (fn [_ _ _] created)
                               player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
                  (handlers/create-match request))
          body (parse-body result)]
      (is (= 201 (:status result)))
      (is (true? (:success body)))))
  (testing "success attaches active season and registers match on season"
    (let [champ-id (ObjectId.)
          team-id (ObjectId.)
          player-id (ObjectId.)
          season-id (ObjectId.)
          created {:_id (ObjectId.)}
          add-match-called (atom false)
          request {:json-body {:championship-id (str champ-id)
                               :player-statistics [{:player-id (str player-id) :team-id (str team-id)}]}}
          result (with-redefs [seasons-db/find-active-by-championship (fn [_] {:_id season-id
                                                                               :enrolled-player-ids [player-id]})
                               seasons-db/find-by-id (fn [sid]
                                                        (when (= sid season-id)
                                                          {:_id season-id :enrolled-player-ids [player-id]}))
                               championships-db/find-by-id (fn [_]
                                                             {:enrolled-player-ids [player-id]})
                               players-db/find-by-ids
                               (fn [_] [{:_id player-id :name "P" :team-id team-id}])
                               teams-db/find-by-id
                               (fn [tid]
                                 (when (= tid team-id)
                                   {:_id team-id :name "T" :active-player-ids [player-id]}))
                               matches-db/create (fn [_ _ _] created)
                               seasons-db/add-match (fn [sid mid]
                                                     (when (and (= sid season-id) (= mid (:_id created)))
                                                       (reset! add-match-called true)))
                               player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
                  (handlers/create-match request))]
      (is (= 201 (:status result)))
      (is (true? @add-match-called)))))

(deftest update-match
  (testing "not found"
    (let [id (str (ObjectId.))
          request {:params {:id id} :json-body {}}
          result (with-redefs [matches-db/exists? (fn [_] false)]
                  (handlers/update-match request))]
      (is (= 404 (:status result)))))
  (testing "success updates stats and season match list"
    (let [id (str (ObjectId.))
          mid (ObjectId. id)
          champ-id (ObjectId.)
          team-id (ObjectId.)
          player-id (ObjectId.)
          season-id (ObjectId.)
          existing {:_id mid :championship-id champ-id :season-id season-id
                    :player-statistics [{:player-id player-id :team-id team-id}]}
          add-called (atom false)
          request {:params {:id id}
                   :json-body {:notes "x"}}
          result (with-redefs [matches-db/exists? (fn [x] (= x id))
                               matches-db/find-by-id (fn [x] (when (= x id) existing))
                               matches-db/update-by-id (fn [_ _ _] nil)
                               seasons-db/find-by-id (fn [sid]
                                                        (when (= sid season-id)
                                                          {:_id season-id :enrolled-player-ids [player-id]}))
                               seasons-db/find-active-by-championship (fn [_] {:_id season-id})
                               championships-db/find-by-id (fn [_] {:enrolled-player-ids [player-id]})
                               players-db/find-by-ids
                               (fn [_] [{:_id player-id :team-id team-id}])
                               teams-db/find-by-id
                               (fn [tid]
                                 (when (= tid team-id)
                                   {:_id team-id :active-player-ids [player-id]}))
                               seasons-db/add-match (fn [sid m]
                                                     (when (= sid season-id)
                                                       (reset! add-called (= m mid))))
                               player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
                  (handlers/update-match request))]
      (is (= 200 (:status result)))
      (is (true? @add-called)))))

(deftest delete-match
  (testing "success"
    (let [id (str (ObjectId.))
          mid (ObjectId. id)
          season-id (ObjectId.)
          removed (atom false)
          request {:params {:id id}}
          result (with-redefs [matches-db/exists? (fn [x] (= x id))
                               matches-db/find-by-id (fn [_] {:_id mid :season-id season-id})
                               matches-db/delete-by-id (fn [_] nil)
                               seasons-db/remove-match (fn [sid m]
                                                         (when (and (= sid season-id) (= m mid))
                                                           (reset! removed true)))
                               player-stats-jobs/submit-incremental-recalc-after-match! (fn [_] nil)]
                  (handlers/delete-match request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Match deleted" (get-in body [:data :message])))
      (is (true? @removed))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [matches-db/exists? (fn [_] false)]
                  (handlers/delete-match request))]
      (is (= 404 (:status result))))))
