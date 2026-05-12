(ns galaticos.handlers.player-merge-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [galaticos.championship.roster :as roster]
            [galaticos.db.admins :as admins]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.merge-audit :as merge-audit]
            [galaticos.db.player-merge-refs :as refs]
            [galaticos.db.players :as players-db]
            [galaticos.db.teams :as teams-db]
            [galaticos.handlers.player-merge :as merge-handlers]
            [galaticos.middleware.auth :as auth])
  (:import [org.bson.types ObjectId]))

(defn- parse-body [response]
  (when (:body response)
    (json/read-str (:body response) :key-fn keyword)))

(deftest list-player-duplicates-returns-report
  (testing "two players with identical normalized names produce duplicate entries"
    (let [a (ObjectId.)
          b (ObjectId.)
          players [{:_id a :name "João Silva"}
                   {:_id b :name "João Silva"}]
          result (with-redefs [players-db/find-all (fn [_] players)]
                   (merge-handlers/list-player-duplicates {}))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (true? (:success body)))
      (is (vector? (:data body)))
      (is (pos? (count (:data body))))
      (is (every? #(and (:player-id %) (:candidates %) (seq (:candidates %))) (:data body))))))

(deftest list-player-duplicates-scoped-to-championship-uses-find-by-ids
  (testing "championship-id query uses roster ids subset"
    (let [a (ObjectId.)
          b (ObjectId.)
          players [{:_id a :name "João Silva"}
                   {:_id b :name "João Silva"}]
          cid "507f1f77bcf86cd799439011"
          result (with-redefs [roster/enrolled-player-object-ids (constantly [a b])
                               players-db/find-by-ids (fn [_ids] players)
                               players-db/find-all (fn [_] (throw (ex-info "should not call find-all" {})))]
                   (merge-handlers/list-player-duplicates
                    {:query-params {"championship-id" cid}}))
          body (parse-body result)]
      (is (= 200 (:status result)))
      (is (true? (:success body)))
      (is (vector? (:data body))))))

(deftest list-merge-candidates-excludes-anchor-and-threshold
  (let [a (ObjectId.)
        b (ObjectId.)
        c (ObjectId.)
        anchor {:_id a :name "Lucas Mafra"}
        sim {:_id b :name "Mafra"}
        far {:_id c :name "Xyz Abc"}
        req {:params {:id (str a)}
             :query-params {"min-similarity" "0.5"}}]
    (let [result (with-redefs [players-db/find-by-id (fn [id] (when (= id (str a)) anchor))
                               players-db/find-all (constantly [anchor sim far])]
                   (merge-handlers/list-merge-candidates req))
          body (parse-body result)
          rows (:data body)]
      (is (= 200 (:status result)))
      (is (vector? rows))
      (is (every? #(contains? % :similarity) rows))
      (is (some #(= (str b) (:id %)) rows))
      (is (every? #(not= (str a) (:id %)) rows)))))

(deftest list-merge-candidates-anchor-not-in-championship-roster-400
  (let [a (ObjectId.) b (ObjectId.)
        anchor {:_id a :name "A"}
        other {:_id b :name "B"}
        cid "507f1f77bcf86cd799439011"
        req {:params {:id (str a)}
             :query-params {"championship-id" cid}}]
    (let [result (with-redefs [roster/enrolled-player-object-ids (constantly [b])
                                 players-db/find-by-id (fn [id] (when (= id (str a)) anchor))]
                   (merge-handlers/list-merge-candidates req))]
      (is (= 400 (:status result))))))

(deftest merge-players-success-updates-master-deletes-merged-writes-audit
  (let [master-oid (ObjectId.)
        merged-oid (ObjectId.)
        master-id (str master-oid)
        merged-id (str merged-oid)
        master {:_id master-oid :name "Old Name" :nickname "KeepNick" :position "FW"}
        merged {:_id merged-oid :name "Better Name" :nickname "DropNick" :position "MF"}
        stored (atom master)
        deleted (atom [])
        audit-payload (atom nil)
        incremental-args (atom nil)
        audit-id (ObjectId.)
        request {:json-body {:master-id master-id
                              :merged-ids [merged-id]
                              :field-selections {:name "merged-0"
                                                 :nickname "master"}}}
        result (with-redefs [auth/current-user (constantly "admin")
                             admins/find-by-username (fn [_] {:_id (ObjectId.) :username "admin"})
                             players-db/find-by-id (fn [id]
                                                     (cond
                                                       (= id master-id) @stored
                                                       (= id merged-id) merged
                                                       :else nil))
                             players-db/update-by-id (fn [_id patch]
                                                         (swap! stored merge patch))
                             players-db/hard-delete-by-id! (fn [id] (swap! deleted conj id))
                             refs/rewrite-all-player-refs! (fn [_master-id _merged-ids _display-name])
                             teams-db/find-all (constantly [])
                             agg/update-incremental-player-stats! (fn
                                                                    ([ids] (reset! incremental-args {:ids ids :opts :default-one-arg}))
                                                                    ([ids opts] (reset! incremental-args {:ids ids :opts opts})))
                             merge-audit/create! (fn [payload]
                                                   (reset! audit-payload payload)
                                                   {:_id audit-id})]
                 (merge-handlers/merge-players request))
        body (parse-body result)]
    (is (= 200 (:status result)) (pr-str body))
    (is (= "Better Name" (get-in body [:data :merged-player :name])))
    (is (= "KeepNick" (get-in body [:data :merged-player :nickname])))
    (is (= [merged-id] @deleted))
    (is (= {:ids [master-id]
            :opts {:zero-if-no-matches? false
                   :drop-stale-without-match-rollups? false}}
           @incremental-args))
    (is (= master-id (str (:master-id @audit-payload))))
    (is (= [merged-id] (map str (:merged-ids @audit-payload))))
    (is (= (str audit-id) (get-in body [:data :audit-id])))))

(deftest merge-players-validation
  (testing "missing master-id"
    (let [r (merge-handlers/merge-players {:json-body {:merged-ids ["x"] :field-selections {:name "master"}}})]
      (is (= 400 (:status r)))))
  (testing "empty merged-ids"
    (let [r (merge-handlers/merge-players {:json-body {:master-id "abc" :merged-ids [] :field-selections {:name "master"}}})]
      (is (= 400 (:status r)))))
  (testing "master-id inside merged-ids"
    (let [id "507f1f77bcf86cd799439011"
          r (merge-handlers/merge-players {:json-body {:master-id id :merged-ids [id] :field-selections {:name "master"}}})]
      (is (= 400 (:status r)))))
  (testing "master not found"
    (let [mid (str (ObjectId.))
          merge-id (str (ObjectId.))
          r (with-redefs [players-db/find-by-id (constantly nil)]
              (merge-handlers/merge-players {:json-body {:master-id mid
                                                        :merged-ids [merge-id]
                                                        :field-selections {:name "master"}}}))]
      (is (= 404 (:status r))))))
