(ns galaticos.handlers.seasons-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.db.championship-store :as championship-store]
            [galaticos.handlers.seasons :as handlers]
            [galaticos.middleware.errors :as errors]
            [galaticos.support.championship-store-fixtures :as fixtures])
  (:import [org.bson.types ObjectId]))

(def ^:private cid "507f1f77bcf86cd799439011")
(def ^:private sid "507f1f77bcf86cd799439012")
(def ^:private pid "507f1f77bcf86cd799439013")

(defn- invoke [handler request]
  ((errors/wrap-errors handler) request))

(defn- with-store [store f]
  (binding [championship-store/*store* store]
    (f)))

(deftest list-seasons-test
  (let [rows [{:season "2024"}]
        store (fixtures/championship-store
                {:find-all-seasons-by-championship (fn [_ _] rows)})
        result (with-store store #(invoke handlers/list-seasons {:params {:id cid}}))]
    (is (= 200 (:status result)))))

(deftest get-season-test
  (testing "found"
    (let [season {:_id sid :season "2024"}
          store (fixtures/championship-store
                  {:find-season-by-id (fn [_ x] (when (= x sid) season))})
          result (with-store store #(invoke handlers/get-season {:params {:id sid}}))]
      (is (= 200 (:status result)))))
  (testing "not found"
    (let [store (fixtures/championship-store {})
          result (with-store store #(invoke handlers/get-season {:params {:id sid}}))]
      (is (= 404 (:status result))))))

(deftest create-season-test
  (testing "validation error"
    (let [result (invoke handlers/create-season {:params {:id cid}
                                                 :json-body {:foo 1}})]
      (is (= 400 (:status result)))))
  (testing "missing required season"
    (let [result (invoke handlers/create-season {:params {:id cid}
                                                 :json-body {:status "inactive"}})]
      (is (= 400 (:status result)))))
  (testing "success inactive"
    (let [created {:_id (ObjectId.) :season "2025" :status "inactive"}
          store (fixtures/championship-store
                  {:find-championship-by-id (fn [_ _] {:format "Pontos"})
                   :create-season (fn [_ d] (merge created d))})
          result (with-store store #(invoke handlers/create-season {:params {:id cid}
                                                                    :json-body {:season "2025"}}))]
      (is (= 201 (:status result)))))
  (testing "success active triggers activate!"
    (let [oid (ObjectId.)
          created {:_id oid :season "2025" :status "active"}
          activated (atom false)
          store (fixtures/championship-store
                  {:find-championship-by-id (fn [_ _] {:format "Pontos"})
                   :create-season (fn [_ d] (merge created d))
                   :activate-season! (fn [_ x] (when (= x oid) (reset! activated true)))})
          result (with-store store #(invoke handlers/create-season {:params {:id cid}
                                                                    :json-body {:season "2025" :status "active"}}))]
      (is (= 201 (:status result)))
      (is (true? @activated))))
  (testing "conflict 409"
    (let [store (fixtures/championship-store
                  {:find-championship-by-id (fn [_ _] {})
                   :create-season (fn [_ _]
                                    (throw (ex-info "dup" {:status 409})))})
          result (with-store store #(invoke handlers/create-season {:params {:id cid}
                                                                    :json-body {:season "2025"}}))]
      (is (= 409 (:status result))))))

(deftest update-season-test
  (testing "validation error"
    (let [result (invoke handlers/update-season {:params {:id sid}
                                                 :json-body {:unknown 1}})]
      (is (= 400 (:status result)))))
  (testing "not found"
    (let [store (fixtures/championship-store {})
          result (with-store store #(invoke handlers/update-season {:params {:id sid}
                                                                    :json-body {:season "2026"}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [updated {:_id sid :season "2026"}
          store (fixtures/championship-store
                  {:season-exists? (fn [_ _] true)
                   :update-season-by-id (fn [_ _ _] nil)
                   :find-season-by-id (fn [_ _] updated)})
          result (with-store store #(invoke handlers/update-season {:params {:id sid}
                                                                    :json-body {:season "2026"}}))]
      (is (= 200 (:status result)))))
  (testing "server error when update loses row"
    (let [store (fixtures/championship-store
                  {:season-exists? (fn [_ _] true)
                   :update-season-by-id (fn [_ _ _] nil)
                   :find-season-by-id (fn [_ _] nil)})
          result (with-store store #(invoke handlers/update-season {:params {:id sid}
                                                                    :json-body {:season "2026"}}))]
      (is (= 500 (:status result))))))

(deftest delete-season-test
  (testing "not found"
    (let [store (fixtures/championship-store {})
          result (with-store store #(invoke handlers/delete-season {:params {:id sid}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [deleted (atom false)
          store (fixtures/championship-store
                  {:season-exists? (fn [_ _] true)
                   :delete-season-by-id (fn [_ x] (when (= x sid) (reset! deleted true)))})
          result (with-store store #(invoke handlers/delete-season {:params {:id sid}}))]
      (is (= 200 (:status result)))
      (is (true? @deleted)))))

(deftest activate-season-test
  (testing "not found"
    (let [store (fixtures/championship-store {})]
      (is (= 404 (:status (with-store store #(invoke handlers/activate-season {:params {:id sid}})))))))
  (testing "success"
    (let [called (atom false)
          store (fixtures/championship-store
                  {:season-exists? (fn [_ _] true)
                   :activate-season! (fn [_ x] (when (= x sid) (reset! called true)))})
          result (with-store store #(invoke handlers/activate-season {:params {:id sid}}))]
      (is (= 200 (:status result)))
      (is (true? @called)))))

(deftest enroll-player-test
  (testing "missing ids"
    (is (= 400 (:status (invoke handlers/enroll-player {:params {:id sid}})))))
  (testing "season not found"
    (let [store (fixtures/championship-store {})
          result (with-store store #(invoke handlers/enroll-player {:params {:id sid :player-id pid}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [store (fixtures/championship-store
                  {:season-exists? (fn [_ _] true)
                   :add-player-to-season (fn [_ a b]
                                           (is (= sid a))
                                           (is (= pid b)))})
          result (with-store store #(invoke handlers/enroll-player {:params {:id sid :player-id pid}}))]
      (is (= 200 (:status result))))))

(deftest unenroll-player-test
  (testing "missing ids"
    (is (= 400 (:status (invoke handlers/unenroll-player {:params {:id sid}})))))
  (testing "season not found"
    (let [store (fixtures/championship-store {})
          result (with-store store #(invoke handlers/unenroll-player {:params {:id sid :player-id pid}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [store (fixtures/championship-store
                  {:season-exists? (fn [_ _] true)
                   :remove-player-from-season (fn [_ _ _] nil)})
          result (with-store store #(invoke handlers/unenroll-player {:params {:id sid :player-id pid}}))]
      (is (= 200 (:status result))))))

(deftest get-season-players-test
  (testing "not found"
    (let [store (fixtures/championship-store {})
          result (with-store store #(invoke handlers/get-season-players {:params {:id sid}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [pids [(ObjectId.)]
          players [{:name "A"}]
          store (fixtures/championship-store
                  {:find-season-by-id (fn [_ _] {:enrolled-player-ids pids})
                   :find-players-by-ids (fn [_ ids] (is (= pids ids)) players)})
          result (with-store store #(invoke handlers/get-season-players {:params {:id sid}}))]
      (is (= 200 (:status result))))))

(deftest finalize-season-test
  (testing "not found"
    (let [store (fixtures/championship-store {})
          result (with-store store #(invoke handlers/finalize-season {:params {:id sid} :json-body {}}))]
      (is (= 404 (:status result)))))
  (testing "success"
    (let [store (fixtures/championship-store
                  {:season-exists? (fn [_ _] true)
                   :finalize-season! (fn [_ _ _ _] nil)})
          result (with-store store #(invoke handlers/finalize-season {:params {:id sid} :json-body {}}))]
      (is (= 200 (:status result)))))
  (testing "finalize! validation 400 from db"
    (let [store (fixtures/championship-store
                  {:season-exists? (fn [_ _] true)
                   :finalize-season!
                   (fn [_ _ _ _]
                     (throw (ex-info "bad" {:status 400})))})
          result (with-store store #(invoke handlers/finalize-season {:params {:id sid} :json-body {}}))]
      (is (= 400 (:status result))))))
