(ns galaticos.db.admins
  "Database operations for admins collection"
  (:require [monger.collection :as mc]
            [galaticos.db.core :refer [db]]
            [galaticos.util.response :refer [->object-id]]
            [clojure.tools.logging :as log])
  (:import [org.bson.types ObjectId]
           [org.mindrot.jbcrypt BCrypt]))

(def collection-name "admins")

(defn list-all
  "List all admins"
  []
  (mc/find-maps (db) collection-name {}))

(defn create
  "Create a new admin with hashed password"
  [username password]
  (let [now (java.util.Date.)
        ;; Use jBCrypt to generate bcrypt hash compatible with Python bcrypt
        password-hash (BCrypt/hashpw password (BCrypt/gensalt))
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
    (try
      (let [password-hash (:password-hash admin)]
        (when (and password-hash (string? password-hash))
          ;; Check if hash is in bcrypt format (starts with $2a$, $2b$, or $2y$)
          (if (re-matches #"^\$2[aby]\$.+" password-hash)
            ;; Use jBCrypt directly for pure bcrypt hashes (compatible with Python bcrypt)
            (BCrypt/checkpw password password-hash)
            (do
              (log/warnf "Invalid password hash format for user %s. Hash prefix: %s"
                         username
                         (subs password-hash 0 (min 10 (count password-hash))))
              false))))
      (catch Exception e
        (log/errorf e "Password verification failed for user %s. Hash length: %d, Hash prefix: %s"
                    username
                    (count (:password-hash admin))
                    (when-let [h (:password-hash admin)]
                      (subs h 0 (min 10 (count h)))))
        false))
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
  (let [password-hash (BCrypt/hashpw new-password (BCrypt/gensalt))]
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

(defn recreate-admin
  "Recreate admin with a new password hash (useful for fixing corrupted hashes)"
  [username new-password]
  (if (exists? username)
    (let [password-hash (BCrypt/hashpw new-password (BCrypt/gensalt))]
      (mc/update (db) collection-name
                 {:username username}
                 {:$set {:password-hash password-hash}})
      (log/infof "Recreated password hash for admin: %s" username)
      true)
    false))

