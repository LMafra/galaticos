(ns galaticos.enrollment
  "Pure enrollment capacity + roster display helpers.
   No app-state, window, or HTTP."
  (:require [clojure.string :as str]))

(defn normalize-max-players
  "Coerce max-players to a non-negative number, or nil when unlimited / invalid."
  [max-players]
  (cond
    (nil? max-players) nil
    (number? max-players) (when-not (neg? max-players) max-players)
    (string? max-players)
    (let [n (js/parseFloat max-players)]
      (when (and (not (js/isNaN n)) (not (neg? n))) n))
    :else nil))

(defn at-limit?
  "True when max is known and enrolled-count >= max."
  [enrolled-count max-players]
  (let [n (or enrolled-count 0)
        mx (normalize-max-players max-players)]
    (and (some? mx) (>= n mx))))

(defn can-enroll?
  "False when at limit (poka-yoke). Unlimited max always allows enroll."
  [enrolled-count max-players]
  (not (at-limit? enrolled-count max-players)))

(defn progress-ratio
  "Fraction enrolled/max in [0 1], or nil when max is unlimited."
  [enrolled-count max-players]
  (when-let [mx (normalize-max-players max-players)]
    (if (zero? mx)
      1.0
      (min 1.0 (/ (double (or enrolled-count 0)) (double mx))))))

(defn enrollment-capacity
  "Single source for n/max RVMF. Keys:
   :enrolled, :max (nil = unlimited), :at-limit?, :ratio, :label (e.g. \"18/22\")."
  [enrolled-count max-players]
  (let [n (or enrolled-count 0)
        mx (normalize-max-players max-players)
        limited? (some? mx)]
    {:enrolled n
     :max mx
     :at-limit? (at-limit? n mx)
     :ratio (progress-ratio n mx)
     :label (if limited?
              (str n "/" (long mx))
              (str n))}))

(defn oid-str
  "Normalize Mongo id / opaque id to a non-blank string, or nil."
  [v]
  (cond
    (nil? v) nil
    (string? v) (not-empty v)
    (map? v) (not-empty (str (or (get v "$oid") (get v :$oid) (:$oid v))))
    :else (not-empty (str v))))

(defn teams-index
  "Pure map team-id-string → team name."
  [teams]
  (into {}
        (keep (fn [t]
                (when-let [id (oid-str (or (:_id t) (:id t) (get t "_id")))]
                  [id (or (not-empty (str (or (:name t) (get t "name")))) "—")]))
              (or teams []))))

(defn athlete-label
  "Wireframe-style label: \"10 - Mateus\" or plain name."
  [player]
  (let [pname (or (:name player) (get player "name") "—")
        shirt (or (:shirt-number player) (get player "shirt-number"))
        shirt-s (when (some? shirt) (str/trim (str shirt)))]
    (if (and shirt-s (not (str/blank? shirt-s)))
      (str shirt-s " - " pname)
      (str pname))))

(defn athlete-position
  [player]
  (let [p (or (:position player) (get player "position"))]
    (if (str/blank? (str p)) "—" (str p))))

(defn athlete-team-base
  "Base team name from player denorm or teams-index lookup."
  [player teams-by-id]
  (or (not-empty (str/trim (str (or (:team-name player) (get player "team-name") ""))))
      (when-let [tid (oid-str (or (:team-id player) (get player "team-id")))]
        (get teams-by-id tid))
      "—"))

(defn enrolled-display-row
  "View-model for Inscrições list/table. Pure."
  [player teams-by-id]
  {:id (oid-str (or (:_id player) (:id player) (get player "_id")))
   :label (athlete-label player)
   :position (athlete-position player)
   :team-base (athlete-team-base player (or teams-by-id {}))
   :player player})

(defn sort-enrolled-players
  "Stable A–Z by name (case-insensitive)."
  [players]
  (vec (sort-by (fn [p] (str/lower-case (str (or (:name p) (get p "name") ""))))
                (or players []))))

;; --- Batch enrollment (Fatia 3) ---

(defn remaining-slots
  "How many more players can enroll. nil = unlimited."
  [enrolled-count max-players]
  (if-let [mx (normalize-max-players max-players)]
    (max 0 (long (- mx (or enrolled-count 0))))
    nil))

(defn batch-selection-label
  "Modeless counter: \"X selecionados de Y disponíveis\"."
  [selected-count available-count]
  (str (or selected-count 0)
       " selecionados de "
       (or available-count 0)
       " disponíveis"))

(defn can-confirm-batch?
  "Confirm enabled only when ≥ 1 id selected."
  [selected-ids]
  (pos? (count selected-ids)))

(defn batch-select-allowed?
  "True when player-id may become selected given remaining slots.
   Deselect always allowed; select allowed if unlimited or room left."
  [selected-ids player-id remaining]
  (let [id (str player-id)
        selected? (contains? selected-ids id)]
    (or selected?
        (nil? remaining)
        (< (count selected-ids) remaining))))

(defn toggle-batch-id
  "Toggle player-id in the selected set. No-op when select would exceed remaining."
  [selected-ids player-id remaining]
  (let [id (str player-id)
        cur (or selected-ids #{})]
    (if (contains? cur id)
      (disj cur id)
      (if (batch-select-allowed? cur id remaining)
        (conj cur id)
        cur))))

(defn partition-batch-results
  "Split sequential enroll outcomes into :succeeded and :failed id lists."
  [results]
  (let [rows (or results [])]
    {:succeeded (mapv :id (filter :ok? rows))
     :failed (mapv :id (remove :ok? rows))
     :failed-rows (vec (remove :ok? rows))}))
