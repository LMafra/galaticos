(ns galaticos.handlers.seasons-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.handlers.seasons :as handlers]
            [galaticos.db.seasons :as seasons-db]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db])
  (:import [org.bson.types ObjectId]))

(def ^:private cid "507f1f77bcf86cd799439011")
(def ^:private sid "507f1f77bcf86cd799439012")
(def ^:private pid "507f1f77bcf86cd799439013")

(deftest list-seasons-test
  (let [rows [{:season "2024"}]
        result (with-redefs [seasons-db/find-all-by-championship (fn [_] rows)]
                (handlers/list-seasons {:params {:id cid}}))]
    (is (= 200 (:status result)))))

(deftest get-season-test
  (testing "found"
    (let [season {:_id sid :season "2024"}
          result (with-redefs [seasons-db/find-by-id (fn [x] (when (= x sid) season))]
                  (handlers/get-season {:params {:id sid}}))]
      (is (= 200 (:status result)))))
  (testing "not found"
    (let [result (with-redefs [seasons-db/find-by-id (fn [_] nil)]
                  (handlers/get-season {:params {:id sid}}))]
      (is (= 404 (:status result))))))

(deftest create-season-test
  (testing "validation error"
    (let [result (handlers/create-season {:params {:id cid}
                                         :json-body {:foo 1}})]
      (is (= 400 (:status result)))))
  (testing "missing required season"
    (let [result (handlers/create-season {:params {:id cid}
                                         :json-body {:status "inactive"}})]
      (is (= 400 (:status result)))))
  (testing "success inactive"
    (let [created {:_id (ObjectId.) :season "2025" :status "inactive"}
          result (with-redefs [championships-db/find-by-id (fn [_] {:format "Pontos"})
                               seasons-db/create (fn [d] (merge created d))]
                  (handlers/create-season {:params {:id cid}
                                          :json-body {:season "2025"}}))]
      (is (= 201 (:status result)))))
  (testing "success active triggers activate!"
    (let [oid (ObjectId.)
          created {:_id oid :season "2025" :status "active"}
          activated (atom false)
          result (with-redefs [championships-db/find-by-id (fn [_] {:format "Pontos"})
                               seasons-db/create (fn [d] (merge created d))
                               seasons-db/activate! (fn [x] (when (= x oid) (reset! activated true)))]
                  (handlers/create-season {:params {:id cid}
                                          :json-body {:season "2025" :status "active"}}))]
      (is (= 201 (:status result)))
      (is (true? @activated))))
  (testing "conflict 409"
    (let [result (with-redefs [championships-db/find-by-id (fn [_] {})
                               seasons-db/create (fn [_]
                                                   (throw (ex-info "dup" {:status 409})))]
                  (handlers/create-season {:params {:id cid}
                                          :json-body {:season "2025"}}))]
      (is (= 409 (:status result))))))

(deftest update-season-test
  (testing "validation error"
    (let [result (handlers/update-season {:params {:id sid}
                                         :json-body {:unknown 1}})]
      (is (= 400 (:status result)))))
  (testing "not found"
    (let [result (with-redefs [seasons-db/exists? (fn [_] false)]
                  (handlers/update-season {:params {:id sid}
                                          :json-body {:season "2026"}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [updated {:_id sid :season "2026"}
          result (with-redefs [seasons-db/exists? (fn [_] true)
                               seasons-db/update-by-id (fn [_ _] nil)
                               seasons-db/find-by-id (fn [_] updated)]
                  (handlers/update-season {:params {:id sid}
                                          :json-body {:season "2026"}}))]
      (is (= 200 (:status result)))))
  (testing "server error when update loses row"
    (let [result (with-redefs [seasons-db/exists? (fn [_] true)
                               seasons-db/update-by-id (fn [_ _] nil)
                               seasons-db/find-by-id (fn [_] nil)]
                  (handlers/update-season {:params {:id sid}
                                          :json-body {:season "2026"}}))]
      (is (= 500 (:status result))))))

(deftest delete-season-test
  (testing "not found"
    (let [result (with-redefs [seasons-db/exists? (fn [_] false)]
                  (handlers/delete-season {:params {:id sid}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [deleted (atom false)
          result (with-redefs [seasons-db/exists? (fn [_] true)
                               seasons-db/delete-by-id (fn [x] (when (= x sid) (reset! deleted true)))]
                  (handlers/delete-season {:params {:id sid}}))]
      (is (= 200 (:status result)))
      (is (true? @deleted)))))

(deftest activate-season-test
  (testing "not found"
    (is (= 404 (:status (with-redefs [seasons-db/exists? (fn [_] false)]
                         (handlers/activate-season {:params {:id sid}}))))))
  (testing "success"
    (let [called (atom false)
          result (with-redefs [seasons-db/exists? (fn [_] true)
                               seasons-db/activate! (fn [x] (when (= x sid) (reset! called true)))]
                  (handlers/activate-season {:params {:id sid}}))]
      (is (= 200 (:status result)))
      (is (true? @called)))))

(deftest enroll-player-test
  (testing "missing ids"
    (is (= 400 (:status (handlers/enroll-player {:params {:id sid}})))))
  (testing "season not found"
    (let [result (with-redefs [seasons-db/find-by-id (fn [_] nil)]
                  (handlers/enroll-player {:params {:id sid :player-id pid}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [result (with-redefs [seasons-db/find-by-id (fn [_] {:_id sid})
                               seasons-db/add-player (fn [a b] (is (= sid a)) (is (= pid b)))]
                  (handlers/enroll-player {:params {:id sid :player-id pid}}))]
      (is (= 200 (:status result))))))

(deftest unenroll-player-test
  (testing "missing ids"
    (is (= 400 (:status (handlers/unenroll-player {:params {:id sid}})))))
  (testing "season not found"
    (let [result (with-redefs [seasons-db/exists? (fn [_] false)]
                  (handlers/unenroll-player {:params {:id sid :player-id pid}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [result (with-redefs [seasons-db/exists? (fn [_] true)
                               seasons-db/remove-player (fn [_ _] nil)]
                  (handlers/unenroll-player {:params {:id sid :player-id pid}}))]
      (is (= 200 (:status result))))))

(deftest get-season-players-test
  (testing "not found"
    (let [result (with-redefs [seasons-db/find-by-id (fn [_] nil)]
                  (handlers/get-season-players {:params {:id sid}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [pids [(ObjectId.)]
          players [{:name "A"}]
          result (with-redefs [seasons-db/find-by-id (fn [_] {:enrolled-player-ids pids})
                               players-db/find-by-ids (fn [ids] (is (= pids ids)) players)]
                  (handlers/get-season-players {:params {:id sid}}))]
      (is (= 200 (:status result))))))

(deftest finalize-season-test
  (testing "not found"
    (let [result (with-redefs [seasons-db/exists? (fn [_] false)]
                  (handlers/finalize-season {:params {:id sid} :json-body {}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [result (with-redefs [seasons-db/exists? (fn [_] true)
                               seasons-db/finalize! (fn [_ _ _] nil)]
                  (handlers/finalize-season {:params {:id sid} :json-body {}}))]
      (is (= 200 (:status result)))))
  (testing "finalize! validation 400 from db"
    (let [result (with-redefs [seasons-db/exists? (fn [_] true)
                               seasons-db/finalize!
                               (fn [_ _ _]
                                 (throw (ex-info "bad" {:status 400})))]
                  (handlers/finalize-season {:params {:id sid} :json-body {}}))]
      (is (= 400 (:status result))))))
