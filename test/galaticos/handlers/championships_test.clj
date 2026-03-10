(ns galaticos.handlers.championships-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.championships :as handlers]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [galaticos.util.response :as resp])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

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

(deftest list-championships
  (let [request {:params {}}
        result (with-redefs [championships-db/find-all (fn [] [{:name "C1"}])]
                (handlers/list-championships request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (vector? (:data body)))))

(deftest get-championship
  (testing "found"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          champ {:_id (ObjectId. id) :name "Champ"}
          result (with-redefs [championships-db/find-by-id (fn [x] (when (= x id) champ))]
                  (handlers/get-championship request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Champ" (get-in body [:data :name])))))
  (testing "not found"
    (let [request {:params {:id (str (ObjectId.))}}
          result (with-redefs [championships-db/find-by-id (fn [_] nil)]
                  (handlers/get-championship request))]
      (is (= 404 (:status result))))))

(deftest create-championship
  (let [request {:json-body {:name "New" :season "2024" :titles-count 1}}
        created {:_id (ObjectId.) :name "New"}
        result (with-redefs [championships-db/create (fn [_] created)]
                (handlers/create-championship request))
        body (parse-body result)]
    (is (= 201 (:status result)))
    (is (= "New" (get-in body [:data :name])))))

(deftest update-championship
  (let [id (str (ObjectId.))
        request {:params {:id id} :json-body {:name "Updated"}}
        updated {:_id (ObjectId. id) :name "Updated"}
        result (with-redefs [championships-db/exists? (fn [x] (= x id))
                             championships-db/update-by-id (fn [_ _] nil)
                             championships-db/find-by-id (fn [_] updated)]
                (handlers/update-championship request))]
    (is (= 200 (:status result)))))

(deftest delete-championship
  (testing "success when no matches"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          result (with-redefs [championships-db/exists? (fn [x] (= x id))
                               championships-db/has-matches? (fn [_] false)
                               championships-db/delete-by-id (fn [_] nil)]
                  (handlers/delete-championship request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Championship deleted" (get-in body [:data :message])))))
  (testing "conflict when has matches"
    (let [id (str (ObjectId.))
          request {:params {:id id}}
          result (with-redefs [championships-db/exists? (fn [x] (= x id))
                               championships-db/has-matches? (fn [_] true)]
                  (handlers/delete-championship request))
          body (parse-body result)]
      (is (= 409 (:status result)))
      (is (string? (:error body))))))

(deftest enroll-player
  (testing "success"
    (let [champ-id (str (ObjectId.))
          player-id (str (ObjectId.))
          request {:params {:id champ-id :player-id player-id}}
          champ {:_id (ObjectId. champ-id) :enrolled-player-ids []}
          result (with-redefs [championships-db/find-by-id (fn [_] champ)
                               players-db/find-by-id (fn [_] {:_id (ObjectId. player-id)})
                               championships-db/add-player (fn [_ _] nil)]
                  (handlers/enroll-player request))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (= "Player enrolled" (get-in body [:data :message])))))
  (testing "missing params"
    (let [request {:params {}}
          result (handlers/enroll-player request)
          body (parse-body result)]
      (is (= 400 (:status result)))
      (is (= "Championship ID and player ID are required" (:error body))))))

(deftest unenroll-player
  (let [champ-id (str (ObjectId.))
        player-id (str (ObjectId.))
        request {:params {:id champ-id :player-id player-id}}
        result (with-redefs [championships-db/exists? (fn [x] (= x champ-id))
                             championships-db/remove-player (fn [_ _] nil)]
                (handlers/unenroll-player request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (= "Player unenrolled" (get-in body [:data :message])))))

(deftest get-championship-players
  (let [champ-id (str (ObjectId.))
        request {:params {:id champ-id}}
        champ {:_id (ObjectId. champ-id) :enrolled-player-ids [(ObjectId.)]}
        result (with-redefs [championships-db/find-by-id (fn [_] champ)
                             players-db/find-by-ids (fn [_] [{:name "P1"}])]
                (handlers/get-championship-players request))
        body (parse-body result)]
    (is (= 200 (:status result)))
    (is (vector? (:data body)))
    (is (= "P1" (get-in body [:data 0 :name])))))
