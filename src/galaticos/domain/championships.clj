(ns galaticos.domain.championships
  "Pure championship rules and enrich transforms."
  (:require [clojure.string :as str])
  (:import [java.util Date]))

(defn- error [type message]
  {:error {:type type :message message}})

(defn- ok [data]
  {:ok data})

(defn sum-season-titles-across
  [season-rows]
  (long (reduce + 0
                (map #(long (or (:titles-count %) 0))
                     season-rows))))

(defn- season-year-sort-key
  [season-row]
  (try (Long/parseLong (str/trim (str (:season season-row ""))))
       (catch Exception _ Long/MIN_VALUE)))

(defn pick-latest-season-for-display
  [season-rows]
  (when (seq season-rows)
    (first (sort-by (fn [s]
                      (let [t (:updated-at s)]
                        [(- (season-year-sort-key s))
                         (- (if (instance? Date t) (.getTime ^Date t) 0))]))
                    season-rows))))

(defn merge-display-from-season-row
  [championship row]
  (merge championship
         {:season (or (:season championship) (:season row))
          :status (or (:status championship) (:status row))
          :active-season-id nil
          :enrolled-player-ids (:enrolled-player-ids row [])
          :winner-player-ids (:winner-player-ids row [])
          :titles-award-count (:titles-award-count row)
          :titles-count (or (:titles-count row) (:titles-award-count row) 0)
          :finished-at (:finished-at row)
          :max-players (:max-players row)
          :start-date (:start-date row)
          :end-date (:end-date row)
          :format (or (:format championship) (:format row))}))

(defn enrich
  "Merge active season or latest fallback into championship map."
  [championship {:keys [all-seasons active-season]}]
  (let [total-titles (sum-season-titles-across all-seasons)
        fallback (when (and (nil? active-season) (seq all-seasons))
                   (pick-latest-season-for-display all-seasons))
        enriched (cond
                   active-season
                   (merge championship
                          {:season (:season active-season)
                           :status (:status active-season)
                           :active-season-id (:_id active-season)
                           :enrolled-player-ids (:enrolled-player-ids active-season [])
                           :winner-player-ids (:winner-player-ids active-season [])
                           :titles-award-count (:titles-award-count active-season)
                           :titles-count (or (:titles-count active-season)
                                             (:titles-award-count active-season)
                                             0)
                           :finished-at (:finished-at active-season)
                           :max-players (:max-players active-season)
                           :start-date (:start-date active-season)
                           :end-date (:end-date active-season)
                           :format (or (:format championship) (:format active-season))})

                   fallback
                   (merge-display-from-season-row championship fallback)

                   :else
                   championship)]
    (assoc enriched :total-titles-across-seasons total-titles)))

(defn enrolled-object-id-set
  [m]
  (let [raw (:enrolled-player-ids m [])]
    (set (cond
           (sequential? raw) raw
           (some? raw) [raw]
           :else []))))

(defn parse-titles-award-count
  [v]
  (cond
    (nil? v) 1
    (number? v) (long v)
    (string? v) (let [trimmed (str/trim v)]
                  (if (str/blank? trimmed)
                    1
                    (try (Long/parseLong trimmed)
                         (catch NumberFormatException _ ::invalid))))
    :else ::invalid))

(defn can-delete?
  [exists? has-matches?]
  (cond
    (not exists?) (error :not-found "Championship not found")
    has-matches? (error :conflict
                        "Cannot delete championship: it has associated matches. Please delete or reassign matches first.")
    :else (ok true)))

(defn enrollment-decision
  [{:keys [enrolled-count max-players already-enrolled? scope]}]
  (if (and max-players (>= enrolled-count max-players) (not already-enrolled?))
    (error :conflict
           (if (= scope :season)
             "Season has reached maximum number of players"
             "Championship has reached maximum number of players"))
    (ok true)))

(defn- titles-validation-errors
  [titles-award-count winner-player-ids]
  (cond
    (= titles-award-count ::invalid)
    (error :validation "titles-award-count must be a non-negative number")

    (nil? titles-award-count)
    (error :validation "titles-award-count must be a non-negative number")

    (neg? titles-award-count)
    (error :validation "titles-award-count must be non-negative")

    (and (pos? titles-award-count) (empty? winner-player-ids))
    (error :validation "At least one winner must be specified when awarding titles")

    :else nil))

(defn finalization-decision-for-season
  [active-season winner-player-ids titles-award-count]
  (or (titles-validation-errors titles-award-count winner-player-ids)
      (when (:finished-at active-season)
        (error :validation "Season has already been finalized"))
      (let [enrolled-ids (set (:enrolled-player-ids active-season []))
            not-enrolled (remove #(contains? enrolled-ids %) winner-player-ids)]
        (when (seq not-enrolled)
          (error :validation "Winners must be enrolled in the season")))
      (ok {:target :season :season active-season :titles-award-count titles-award-count})))

(defn finalization-decision-for-championship
  [championship winner-player-ids titles-award-count]
  (or (titles-validation-errors titles-award-count winner-player-ids)
      (let [status (or (:status championship) "active")]
        (when (not= status "active")
          (error :validation "Only active championships can be finalized")))
      (when (:finished-at championship)
        (error :validation "Championship has already been finalized"))
      (let [enrolled-ids (set (:enrolled-player-ids championship []))
            not-enrolled (remove #(contains? enrolled-ids %) winner-player-ids)]
        (when (seq not-enrolled)
          (error :validation "Winners must be enrolled in the championship")))
      (ok {:target :championship
           :championship championship
           :titles-award-count titles-award-count})))

(defn finalization-decision
  [active-season championship winner-player-ids titles-award-count]
  (if active-season
    (finalization-decision-for-season active-season winner-player-ids titles-award-count)
    (if championship
      (finalization-decision-for-championship championship winner-player-ids titles-award-count)
      (error :not-found "Championship not found"))))
