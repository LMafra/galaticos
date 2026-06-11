(ns galaticos.logic.championships-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [galaticos.logic.championships :as logic]
            [galaticos.support.championship-store-fixtures :as fixtures]
            [galaticos.util.response :as resp])
  (:import [org.bson.types ObjectId]))

(deftest delete-conflict-when-has-matches
  (let [id (str (ObjectId.))
        store (fixtures/championship-store
                 {:championship-exists? (fn [_ x] (= x id))
                  :championship-has-matches? (fn [_ _] true)
                  :delete-championship-by-id (fn [_ _] (throw (Exception. "should not run")))})]
    (try
      (logic/delete! store id)
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 409 (-> e ex-data :status)))
        (is (str/includes? (-> e ex-data :message) "associated matches"))))))

(deftest delete-success-when-no-matches
  (let [id (str (ObjectId.))
        deleted (atom false)
        store (fixtures/championship-store
                 {:championship-exists? (fn [_ x] (= x id))
                  :championship-has-matches? (fn [_ _] false)
                  :delete-championship-by-id (fn [_ _] (reset! deleted true))})]
    (is (= "Championship deleted" (:message (logic/delete! store id))))
    (is (true? @deleted))))

(deftest finalize-only-active-championship
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "completed" :enrolled-player-ids []}
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ _] champ)})]
    (try
      (logic/finalize! store champ-id {:winner-player-ids [] :titles-award-count 0})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Only active championships can be finalized" (-> e ex-data :message)))))))

(deftest finalize-already-finalized
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :finished-at (java.util.Date.) :enrolled-player-ids []}
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ _] champ)})]
    (try
      (logic/finalize! store champ-id {:winner-player-ids [] :titles-award-count 0})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Championship has already been finalized" (-> e ex-data :message)))))))

(deftest finalize-titles-award-count-negative
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids [(ObjectId.)]}
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ _] champ)})]
    (try
      (logic/finalize! store champ-id {:winner-player-ids [(str (ObjectId.))] :titles-award-count -1})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= "titles-award-count must be non-negative" (-> e ex-data :message)))))))

(deftest finalize-winners-required-when-awarding-titles
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids []}
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ _] champ)})]
    (try
      (logic/finalize! store champ-id {:winner-player-ids [] :titles-award-count 2})
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= "At least one winner must be specified when awarding titles" (-> e ex-data :message)))))))

(deftest finalize-success-with-titles-award-count
  (let [champ-id (str (ObjectId.))
        winner-id (ObjectId.)
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids [winner-id]}
        updated (atom nil)
        increment-args (atom nil)
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ _] champ)
                  :update-championship-by-id (fn [_ _ m] (reset! updated m))
                  :increment-player-titles (fn [_ ids amount] (reset! increment-args [ids amount]))})]
    (with-redefs [resp/->object-id (fn [x] (if (string? x) (ObjectId. x) x))]
      (let [result (logic/finalize! store champ-id
                                    {:winner-player-ids [(str winner-id)] :titles-award-count 2})]
        (is (= "Championship finalized" (:message result)))
        (is (= "completed" (:status @updated)))
        (is (some? (:finished-at @updated)))
        (is (= 2 (:titles-award-count @updated)))
        (is (= 2 (second @increment-args)))))))

(deftest finalize-zero-titles-no-winners
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids []}
        updated (atom nil)
        increment-called (atom false)
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ _] champ)
                  :update-championship-by-id (fn [_ _ m] (reset! updated m))
                  :increment-player-titles (fn [_ _] (reset! increment-called true))})]
    (with-redefs [resp/->object-id (fn [x] (if (string? x) (ObjectId. x) x))]
      (let [result (logic/finalize! store champ-id {:winner-player-ids [] :titles-award-count 0})]
        (is (= "Championship finalized" (:message result)))
        (is (= "completed" (:status @updated)))
        (is (= 0 (:titles-award-count @updated)))
        (is (false? @increment-called))))))

(deftest enroll-max-players-on-season
  (let [champ-id (str (ObjectId.))
        player-id (str (ObjectId.))
        season {:_id (ObjectId.) :max-players 1 :enrolled-player-ids [(ObjectId.)]}
        store (fixtures/championship-store
                 {:find-active-season-by-championship (fn [_ _] season)
                  :find-player-by-id (fn [_ _] {:_id (ObjectId. player-id)})
                  :add-player-to-season (fn [_ _ _] (throw (Exception. "should not run")))})]
    (try
      (logic/enroll! store champ-id player-id)
      (is false "should throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= 409 (-> e ex-data :status)))
        (is (= "Season has reached maximum number of players" (-> e ex-data :message)))))))

(deftest enroll-success-on-championship-without-active-season
  (let [champ-id (str (ObjectId.))
        player-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :enrolled-player-ids []}
        enrolled (atom false)
        store (fixtures/championship-store
                 {:find-active-season-by-championship (fn [_ _] nil)
                  :find-championship-by-id (fn [_ _] champ)
                  :find-player-by-id (fn [_ _] {:_id (ObjectId. player-id)})
                  :add-player-to-championship (fn [_ _ _] (reset! enrolled true))})]
    (is (= "Player enrolled" (:message (logic/enroll! store champ-id player-id))))
    (is (true? @enrolled))))

(deftest get-by-id-enriches-titles-and-season
  (let [id (str (ObjectId.))
        oid (ObjectId. id)
        champ {:_id oid :name "Champ"}
        store (fixtures/championship-store
                 {:find-championship-by-id (fn [_ x] (when (= x id) champ))
                  :find-all-seasons-by-championship (fn [_ cid]
                                                        (when (= cid oid)
                                                          [{:season "2024"
                                                            :status "completed"
                                                            :titles-count 2
                                                            :enrolled-player-ids []
                                                            :updated-at (java.util.Date. 1000)}
                                                           {:season "2025"
                                                            :status "completed"
                                                            :titles-count 3
                                                            :enrolled-player-ids []
                                                            :updated-at (java.util.Date. 2000)}]))})]
    (let [result (logic/get-by-id store id)]
      (is (= "Champ" (:name result)))
      (is (= 5 (:total-titles-across-seasons result)))
      (is (= "2025" (:season result)))
      (is (= "completed" (:status result)))
      (is (= 3 (:titles-count result))))))

(deftest championship-players-fallback-to-root-enrolled
  (let [champ-id (str (ObjectId.))
        enrolled-id (ObjectId.)
        store (fixtures/championship-store
                 {:championship-exists? (fn [_ _] true)
                  :enrolled-player-object-ids (fn [_ _] [enrolled-id])
                  :find-players-by-ids (fn [_ _] [{:name "P1" :_id enrolled-id}])})]
    (let [players (logic/championship-players store champ-id)]
      (is (= 1 (count players)))
      (is (= "P1" (:name (first players)))))))

(deftest championship-players-union-from-all-seasons
  (let [champ-id (str (ObjectId.))
        p1 (ObjectId.)
        p2 (ObjectId.)
        store (fixtures/championship-store
                 {:championship-exists? (fn [_ _] true)
                  :enrolled-player-object-ids (fn [_ _] [p1 p2])
                  :find-players-by-ids (fn [_ ids]
                                         (vec (for [id ids]
                                                {:name (if (= id p1) "Ana" "Beto") :_id id})))})]
    (let [players (logic/championship-players store champ-id)
          names (sort (map :name players))]
      (is (= 2 (count players)))
      (is (= ["Ana" "Beto"] names)))))

(deftest list-championships-filters-by-status
  (let [store (fixtures/championship-store
                 {:find-all-championships (fn [_]
                                             [{:name "A" :status "active" :_id (ObjectId.)}
                                              {:name "B" :status "completed" :_id (ObjectId.)}])})]
    (is (= 1 (count (logic/list-championships store {:params {:status "active"}}))))
    (is (= 2 (count (logic/list-championships store {:params {}}))))))

(deftest unenroll-on-championship-without-active-season
  (let [champ-id (str (ObjectId.))
        player-id (str (ObjectId.))
        removed (atom false)
        store (fixtures/championship-store
                 {:find-active-season-by-championship (fn [_ _] nil)
                  :championship-exists? (fn [_ _] true)
                  :remove-player-from-championship (fn [_ _ _] (reset! removed true))})]
    (is (= "Player unenrolled" (:message (logic/unenroll! store champ-id player-id))))
    (is (true? @removed))))
