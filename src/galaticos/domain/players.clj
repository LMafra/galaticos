(ns galaticos.domain.players
  "Pure player view rules."
  (:require [clojure.string :as str]))

(defn- error [type message]
  {:error {:type type :message message}})

(defn- ok [data]
  {:ok data})

(defn team-assignment-decision
  [team-id team-exists?]
  (if (and team-id (not team-exists?))
    (error :validation "Team not found")
    (ok true)))

(defn attach-team-name
  "Strip stale :team-name from player doc; attach name from team lookup when present."
  [player team-name]
  (let [team-name (when team-name (not-empty (str/trim (str team-name))))]
    (cond-> (dissoc player :team-name)
      team-name (assoc :team-name team-name))))
