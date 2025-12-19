(ns galaticos.db.admins
  "Database operations for admins collection"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]]
            [buddy.hashers :as hashers])
  (:import [org.bson.types ObjectId]))

(def collection-name "admins")

(defn list-all
  "List all admins"
  []
  (mc/find-maps (db) collection-name {}))

(defn create
  "Create a new admin with hashed password"
  [username password]
  (let [now (java.util.Date.)
        password-hash (hashers/derive password)
        doc {:_id (ObjectId.)
             :username username
             :password-hash password-hash
             :created-at now}]
    (mc/insert (db) collection-name doc)
    doc))

(defn find-by-id
  "Find admin by ID"
  [id]
  (mc/find-one-as-map (db) collection-name {:_id (->object-id id)}))

(defn find-by-username
  "Find admin by username"
  [username]
  (mc/find-one-as-map (db) collection-name {:username username}))

(defn verify-password
  "Verify admin password"
  [username password]
  (if-let [admin (find-by-username username)]
    (hashers/check password (:password-hash admin))
    false))

(defn update-last-login
  "Update admin's last login timestamp"
  [username]
  (mc/update (db) collection-name
             {:username username}
             {:$set {:last-login (java.util.Date.)}}))

(defn update-password
  "Update admin password"
  [username new-password]
  (let [password-hash (hashers/derive new-password)]
    (mc/update (db) collection-name
               {:username username}
               {:$set {:password-hash password-hash}})))

(defn delete-by-id
  "Delete admin by ID"
  [id]
  (mc/remove (db) collection-name {:_id (->object-id id)}))

(defn exists?
  "Check if admin exists"
  [username]
  (some? (find-by-username username)))

