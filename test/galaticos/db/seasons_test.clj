(ns galaticos.db.seasons-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.db.seasons :as seasons]
            [galaticos.db.core :as db-core]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.players :as players-db]
            [monger.collection :as mc])
  (:import [org.bson.types ObjectId]))

(def ^:private cid "507f1f77bcf86cd799439011")
(def ^:private cid-oid (ObjectId. cid))

(defn- doc-matches-query? [doc q]
  (cond
    (:_id q) (= (:_id doc) (:_id q))
    (and (:championship-id q) (:status q))
    (and (= (:championship-id doc) (:championship-id q))
         (= (:status doc) (:status q)))
    (and (:championship-id q) (contains? q :season))
    (and (= (:championship-id doc) (:championship-id q))
         (= (:season doc) (:season q)))
    (:championship-id q) (= (:championship-id doc) (:championship-id q))
    :else false))

(defn- apply-mongo-update [doc update-doc]
  (let [d (if-let [s (:$set update-doc)] (merge doc s) doc)
        d (reduce (fn [d [field v]]
                    (update d field (fn [xs] (vec (distinct (conj (vec xs) v))))))
                  d
                  (seq (:$addToSet update-doc)))
        d (reduce (fn [d [field v]]
                    (update d field (fn [xs] (vec (remove #{v} (vec xs))))))
                  d
                  (seq (:$pull update-doc)))]
    d))

(defn- mc-store-fixture [initial-docs]
  (let [a (atom initial-docs)]
    {:doc-atom a
     :find-one (fn [_ coll q]
                 (when (= coll seasons/collection-name)
                   (first (filter #(doc-matches-query? % q) @a))))
     :find-maps (fn [_ coll q]
                  (when (= coll seasons/collection-name)
                    (vec (filter #(doc-matches-query? % q) @a))))
     :insert (fn [_ _ doc] (swap! a conj doc) nil)
     :update (fn [_ coll q update-doc]
               (when (= coll seasons/collection-name)
                 (swap! a
                        (fn [docs]
                          (mapv (fn [d]
                                  (if (doc-matches-query? d q)
                                    (apply-mongo-update d update-doc)
                                    d))
                                docs)))))
     :remove (fn [_ coll q]
               (when (= coll seasons/collection-name)
                 (cond
                   (:_id q)
                   (swap! a (fn [docs] (vec (remove #(= (:_id %) (:_id q)) docs))))
                   (:championship-id q)
                   (swap! a (fn [docs] (vec (remove #(= (:championship-id %) (:championship-id q)) docs)))))))}))

(deftest find-by-id-and-exists
  (let [sid (ObjectId.)
        doc {:_id sid :season "2024"}
        {:keys [find-one]} (mc-store-fixture [doc])]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one]
      (is (= doc (seasons/find-by-id (str sid))))
      (is (true? (seasons/exists? (str sid))))
      (is (false? (seasons/exists? "507f1f77bcf86cd799439099"))))))

(deftest find-all-and-active-by-championship
  (let [s1 {:_id (ObjectId.) :championship-id cid-oid :season "2024" :status "inactive"}
        s2 {:_id (ObjectId.) :championship-id cid-oid :season "2025" :status "active"}
        {:keys [find-one find-maps]} (mc-store-fixture [s1 s2])]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps find-maps]
      (is (= 2 (count (seasons/find-all-by-championship cid))))
      (is (= "2025" (:season (seasons/find-active-by-championship cid)))))))

(deftest find-by-championship-and-season-via-create
  (let [{:keys [find-one insert update remove] :as st} (mc-store-fixture [])
        add-called (atom false)]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps (:find-maps st)
                  mc/insert insert
                  mc/update update
                  mc/remove remove
                  championships-db/add-season-id (fn [c oid]
                                                     (when (and (= c cid) (instance? ObjectId oid))
                                                       (reset! add-called true)))]
      (let [created (seasons/create {:championship-id cid :season "2026"})]
        (is (= "2026" (:season created)))
        (is (true? @add-called))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"already exists"
                              (seasons/create {:championship-id cid :season "2026"})))))))

(deftest update-and-delete-by-id
  (let [sid (ObjectId.)
        doc {:_id sid :season "A" :championship-id cid-oid :status "inactive"}
        {:keys [find-one insert update remove find-maps] :as st} (mc-store-fixture [doc])]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps (:find-maps st)
                  mc/insert insert
                  mc/update update
                  mc/remove remove]
      (seasons/update-by-id (str sid) {:season "B"})
      (is (= "B" (:season (seasons/find-by-id (str sid)))))
      (seasons/delete-by-id (str sid))
      (is (nil? (seasons/find-by-id (str sid)))))))

(deftest delete-by-championship
  (let [d {:_id (ObjectId.) :championship-id cid-oid :season "x"}
        {:keys [find-maps find-one insert update remove] :as st} (mc-store-fixture [d])]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps find-maps
                  mc/insert insert
                  mc/update update
                  mc/remove remove]
      (seasons/delete-by-championship cid)
      (is (empty? (seasons/find-all-by-championship cid))))))

(deftest add-and-remove-player
  (let [sid (ObjectId.)
        pid (ObjectId.)
        doc {:_id sid :enrolled-player-ids [] :championship-id cid-oid :season "s"}
        {:keys [find-one insert update remove find-maps] :as st} (mc-store-fixture [doc])]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps find-maps
                  mc/insert insert
                  mc/update update
                  mc/remove remove]
      (seasons/add-player (str sid) (str pid))
      (is (= [pid] (:enrolled-player-ids (seasons/find-by-id (str sid)))))
      (seasons/remove-player (str sid) (str pid))
      (is (empty? (:enrolled-player-ids (seasons/find-by-id (str sid))))))))

(deftest add-and-remove-match
  (let [sid (ObjectId.)
        mid (ObjectId.)
        doc {:_id sid :match-ids [] :championship-id cid-oid :season "s"}
        {:keys [find-one insert update remove find-maps] :as st} (mc-store-fixture [doc])]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps find-maps
                  mc/insert insert
                  mc/update update
                  mc/remove remove]
      (seasons/add-match (str sid) (str mid))
      (is (= [mid] (:match-ids (seasons/find-by-id (str sid)))))
      (seasons/remove-match (str sid) (str mid))
      (is (empty? (:match-ids (seasons/find-by-id (str sid))))))))

(deftest get-players-and-enrolled-ids
  (let [pid (ObjectId.)
        sid (ObjectId.)
        doc {:_id sid :enrolled-player-ids [pid] :championship-id cid-oid :season "s"}
        {:keys [find-one insert update remove find-maps] :as st} (mc-store-fixture [doc])]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps find-maps
                  mc/insert insert
                  mc/update update
                  mc/remove remove
                  players-db/find-by-ids (fn [ids] (is (= [pid] ids)) [{:name "P"}])]
      (is (= [{:name "P"}] (seasons/get-players (str sid))))
      (is (= [pid] (seasons/get-enrolled-player-ids (str sid)))))))

(deftest activate!-deactivates-others
  (let [s1 (ObjectId.)
        s2 (ObjectId.)
        d1 {:_id s1 :championship-id cid-oid :season "a" :status "active"}
        d2 {:_id s2 :championship-id cid-oid :season "b" :status "inactive"}
        {:keys [find-one insert update remove find-maps] :as st} (mc-store-fixture [d1 d2])]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps find-maps
                  mc/insert insert
                  mc/update update
                  mc/remove remove]
      (seasons/activate! (str s2))
      (is (= "inactive" (:status (seasons/find-by-id (str s1)))))
      (is (= "active" (:status (seasons/find-by-id (str s2))))))))

(deftest finalize!-success-and-validation
  (let [pid (ObjectId.)
        sid (ObjectId.)
        doc {:_id sid :championship-id cid-oid :season "s"
             :enrolled-player-ids [pid] :status "active"}
        {:keys [find-one insert update remove find-maps] :as st} (mc-store-fixture [doc])
        titles (atom nil)]
    (with-redefs [db-core/db (constantly :db)
                  mc/find-one-as-map find-one
                  mc/find-maps find-maps
                  mc/insert insert
                  mc/update update
                  mc/remove remove
                  players-db/increment-titles (fn [winners n] (reset! titles [winners n]))]
      (let [out (seasons/finalize! (str sid) [pid] 2)]
        (is (= "completed" (:status out)))
        (is (= 2 (second @titles)))
        (is (= pid (first (first @titles)))))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Winners must be enrolled"
                            (seasons/finalize! (str sid) [(ObjectId.)] 1))))))
