(ns galaticos.handlers.championships-test
  (:require [clojure.test :refer [deftest is]]
            [galaticos.handlers.championships :as handlers]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.util.response :as resp])
  (:import [org.bson.types ObjectId]))

(defn- make-request
  ([id] (make-request id {}))
  ([id json-body]
   {:params {:id id}
    :json-body json-body}))

(deftest finalize-championship-only-active
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "completed" :enrolled-player-ids []}
        request (make-request champ-id {:winner-player-ids [] :titles-award-count 0})]
    (with-redefs [championships-db/find-by-id (fn [_] champ)
                  championships-db/update-by-id (fn [_ _] nil)
                  players-db/increment-titles (fn [_ _] nil)
                  resp/error (fn [msg _] {:error msg})
                  resp/success (fn [_] {:success true})]
      (let [response (handlers/finalize-championship request)]
        (is (= "Only active championships can be finalized" (:error response)))))))

(deftest finalize-championship-already-finalized
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :finished-at (java.util.Date.) :enrolled-player-ids []}
        request (make-request champ-id {:winner-player-ids [] :titles-award-count 0})]
    (with-redefs [championships-db/find-by-id (fn [_] champ)
                  resp/error (fn [msg _] {:error msg})
                  resp/not-found (fn [_] {:not-found true})]
      (let [response (handlers/finalize-championship request)]
        (is (= "Championship has already been finalized" (:error response)))))))

(deftest finalize-championship-titles-award-count-negative
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids [(ObjectId.)]}
        request (make-request champ-id {:winner-player-ids [(str (ObjectId.))] :titles-award-count -1})]
    (with-redefs [championships-db/find-by-id (fn [_] champ)
                  resp/error (fn [msg _] {:error msg})]
      (let [response (handlers/finalize-championship request)]
        (is (= "titles-award-count must be non-negative" (:error response)))))))

(deftest finalize-championship-winners-required-when-awarding-titles
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids []}
        request (make-request champ-id {:winner-player-ids [] :titles-award-count 2})]
    (with-redefs [championships-db/find-by-id (fn [_] champ)
                  resp/error (fn [msg _] {:error msg})]
      (let [response (handlers/finalize-championship request)]
        (is (= "At least one winner must be specified when awarding titles" (:error response)))))))

(deftest finalize-championship-success-with-titles-award-count
  (let [champ-id (str (ObjectId.))
        winner-id (ObjectId.)
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids [winner-id]}
        request (make-request champ-id {:winner-player-ids [(str winner-id)] :titles-award-count 2})
        updated (atom nil)
        increment-args (atom nil)]
    (with-redefs [championships-db/find-by-id (fn [_] champ)
                  championships-db/update-by-id (fn [_ m] (reset! updated m))
                  players-db/increment-titles (fn [ids amount] (reset! increment-args [ids amount]))
                  resp/success (fn [m] {:success true :message (:message m)})
                  resp/->object-id (fn [x] (if (string? x) (ObjectId. x) x))]
      (let [response (handlers/finalize-championship request)]
        (is (= true (:success response)))
        (is (= "completed" (:status @updated)))
        (is (some? (:finished-at @updated)))
        (is (= 2 (:titles-award-count @updated)))
        (is (= 2 (second @increment-args)))))))

(deftest finalize-championship-zero-titles-no-winners
  (let [champ-id (str (ObjectId.))
        champ {:_id (ObjectId. champ-id) :status "active" :enrolled-player-ids []}
        request (make-request champ-id {:winner-player-ids [] :titles-award-count 0})
        updated (atom nil)
        increment-called (atom false)]
    (with-redefs [championships-db/find-by-id (fn [_] champ)
                  championships-db/update-by-id (fn [_ m] (reset! updated m))
                  players-db/increment-titles (fn [_ _] (reset! increment-called true))
                  resp/success (fn [m] {:success true :message (:message m)})
                  resp/->object-id (fn [x] (if (string? x) (ObjectId. x) x))]
      (let [response (handlers/finalize-championship request)]
        (is (= true (:success response)))
        (is (= "completed" (:status @updated)))
        (is (= 0 (:titles-award-count @updated)))
        (is (false? @increment-called))))))
