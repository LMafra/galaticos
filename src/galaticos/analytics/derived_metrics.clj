(ns galaticos.analytics.derived-metrics
  "Pure derived metric helpers for API layer (delegates to domain)."
  (:require [galaticos.domain.analytics :as domain]))

(def derived-metric-keys
  #{:goal-contribution :goal-contribution-per-game :discipline-index :minutes-per-goal})

(defn derived-metric?
  [metric]
  (contains? derived-metric-keys (keyword metric)))

(defn enrich-stats
  "Attach derived fields to a stats map (games, goals, assists, cards, minutes)."
  [stats]
  (domain/enrich-derived-metrics (or stats {})))

(defn derived-from-stats
  "Return only derived metric keys from a stats map."
  [stats]
  (select-keys (enrich-stats stats) derived-metric-keys))

(defn attach-player-derived
  "Add `:derived` map from `aggregated-stats.total` without mutating cache fields."
  [player]
  (let [total (get-in player [:aggregated-stats :total] {})]
    (assoc player :derived (derived-from-stats total))))

(defn attach-players-derived
  [players]
  (mapv attach-player-derived players))

(defn metric-value
  "Numeric sort/rank value for a derived metric on a player map (with or without :derived)."
  [player metric]
  (let [k (keyword metric)
        derived (or (:derived player) (derived-from-stats (get-in player [:aggregated-stats :total] {})))]
    (get derived k 0)))
