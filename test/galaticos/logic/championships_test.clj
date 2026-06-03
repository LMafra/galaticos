(ns galaticos.logic.championships-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [galaticos.db.protocol.championship-store :as protocol :refer [ChampionshipStore]]
            [galaticos.logic.championships :as logic]
            [galaticos.util.response :as resp])
  (:import [org.bson.types ObjectId]))

(deftest delete-conflict-when-has-matches
  (let [id (str (ObjectId.))
        store (reify ChampionshipStore
                (find-all-championships [_] [])
                (find-championship-by-id [_ _] nil)
                (create-championship [_ _] nil)
                (update-championship-by-id [_ _ _] nil)
                (delete-championship-by-id [_ _] (throw (Exception. "should not run")))
                (championship-exists? [_ x] (= x id))
                (championship-has-matches? [_ _] true)
                (add-player-to-championship [_ _ _] nil)
                (remove-player-from-championship [_ _ _] nil)
                (find-all-seasons-by-championship [_ _] [])
                (find-active-season-by-championship [_ _] nil)
                (create-season [_ _] nil)
                (update-season-by-id [_ _ _] nil)
                (delete-seasons-by-championship [_ _] nil)
                (activate-season! [_ _] nil)
                (finalize-season! [_ _ _ _] nil)
                (add-player-to-season [_ _ _] nil)
                (remove-player-from-season [_ _ _] nil)
                (find-player-by-id [_ _] nil)
                (find-players-by-ids [_ _] [])
                (increment-player-titles [_ _ _] nil)
                (enrolled-player-object-ids [_ _] []))]
    (try
      (logic/delete! store id)
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 409 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "associated matches"))))))

(deftest finalize-only-active-championship
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "completed" :enrolled-player-ids []}
        store (reify ChampionshipStore
                (find-all-championships [_] [])
                (find-championship-by-id [_ _] champ)
                (create-championship [_ _] nil)
                (update-championship-by-id [_ _ _] nil)
                (delete-championship-by-id [_ _] nil)
                (championship-exists? [_ _] true)
                (championship-has-matches? [_ _] false)
                (add-player-to-championship [_ _ _] nil)
                (remove-player-from-championship [_ _ _] nil)
                (find-all-seasons-by-championship [_ _] [])
                (find-active-season-by-championship [_ _] nil)
                (create-season [_ _] nil)
                (update-season-by-id [_ _ _] nil)
                (delete-seasons-by-championship [_ _] nil)
                (activate-season! [_ _] nil)
                (finalize-season! [_ _ _ _] nil)
                (add-player-to-season [_ _ _] nil)
                (remove-player-from-season [_ _ _] nil)
                (find-player-by-id [_ _] nil)
                (find-players-by-ids [_ _] [])
                (increment-player-titles [_ _ _] nil)
                (enrolled-player-object-ids [_ _] []))]
    (try
      (logic/finalize! store champ-id {:winner-player-ids [] :titles-award-count 0})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Only active championships can be finalized" (-> e ex-data :message)))))))

(deftest finalize-success-with-titles-award-count
  (let [champ-id (str (ObjectId.))
        winner-id (ObjectId.)
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids [winner-id]}
        updated (atom nil)
        increment-args (atom nil)
        store (reify ChampionshipStore
                (find-all-championships [_] [])
                (find-championship-by-id [_ _] champ)
                (create-championship [_ _] nil)
                (update-championship-by-id [_ id m] (reset! updated m))
                (delete-championship-by-id [_ _] nil)
                (championship-exists? [_ _] true)
                (championship-has-matches? [_ _] false)
                (add-player-to-championship [_ _ _] nil)
                (remove-player-from-championship [_ _ _] nil)
                (find-all-seasons-by-championship [_ _] [])
                (find-active-season-by-championship [_ _] nil)
                (create-season [_ _] nil)
                (update-season-by-id [_ _ _] nil)
                (delete-seasons-by-championship [_ _] nil)
                (activate-season! [_ _] nil)
                (finalize-season! [_ _ _ _] nil)
                (add-player-to-season [_ _ _] nil)
                (remove-player-from-season [_ _ _] nil)
                (find-player-by-id [_ _] nil)
                (find-players-by-ids [_ _] [])
                (increment-player-titles [_ ids amount] (reset! increment-args [ids amount]))
                (enrolled-player-object-ids [_ _] []))]
    (with-redefs [resp/->object-id (fn [x] (if (string? x) (ObjectId. x) x))]
      (let [result (logic/finalize! store champ-id
                                    {:winner-player-ids [(str winner-id)] :titles-award-count 2})]
        (is (= "Championship finalized" (:message result)))
        (is (= "completed" (:status @updated)))
        (is (some? (:finished-at @updated)))
        (is (= 2 (:titles-award-count @updated)))
        (is (= 2 (second @increment-args)))))))

(deftest enroll-max-players-on-season
  (let [champ-id (str (ObjectId.))
        player-id (str (ObjectId.))
        season {:_id (ObjectId.) :max-players 1 :enrolled-player-ids [(ObjectId.)]}
        store (reify ChampionshipStore
                (find-all-championships [_] [])
                (find-championship-by-id [_ _] nil)
                (create-championship [_ _] nil)
                (update-championship-by-id [_ _ _] nil)
                (delete-championship-by-id [_ _] nil)
                (championship-exists? [_ _] false)
                (championship-has-matches? [_ _] false)
                (add-player-to-championship [_ _ _] nil)
                (remove-player-from-championship [_ _ _] nil)
                (find-all-seasons-by-championship [_ _] [])
                (find-active-season-by-championship [_ _] season)
                (create-season [_ _] nil)
                (update-season-by-id [_ _ _] nil)
                (delete-seasons-by-championship [_ _] nil)
                (activate-season! [_ _] nil)
                (finalize-season! [_ _ _ _] nil)
                (add-player-to-season [_ _ _] (throw (Exception. "should not run")))
                (remove-player-from-season [_ _ _] nil)
                (find-player-by-id [_ _] {:_id (ObjectId. player-id)})
                (find-players-by-ids [_ _] [])
                (increment-player-titles [_ _ _] nil)
                (enrolled-player-object-ids [_ _] []))]
    (try
      (logic/enroll! store champ-id player-id)
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 409 (-> e ex-data :status)))
        (is (= "Season has reached maximum number of players" (-> e ex-data :message)))))))
