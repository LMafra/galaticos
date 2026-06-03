(ns galaticos.domain.seasons
  "Pure season rules."
  (:require [galaticos.domain.championships :as championships]))

(defn parse-titles-award-count
  [v]
  (championships/parse-titles-award-count v))

(defn finalize-payload
  "Normalize finalize body: winner ids as ObjectIds and titles count."
  [body ->object-id]
  {:winner-player-ids (mapv ->object-id (get body :winner-player-ids []))
   :titles-award-count (parse-titles-award-count (get body :titles-award-count))})
