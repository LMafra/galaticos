(ns galaticos.match-stat-bounds
  "Pure numeric bounds for match form steppers (poka-yoke).
   Block impossible values; improbable-but-legal stays within caps.")

(def default-bounds
  "min/max for match form numeric fields."
  {:goals {:min-val 0 :max-val 20}
   :assists {:min-val 0 :max-val 20}
   :minutes-played {:min-val 0 :max-val 120}
   :yellow-cards {:min-val 0 :max-val 2}
   :red-cards {:min-val 0 :max-val 1}
   :away-score {:min-val 0 :max-val 20}})

(defn bounds-for
  "Return {:min-val :max-val} for field, or nil when unbounded."
  [field]
  (get default-bounds field))

(defn clamp-stat-value
  "Clamp n into field bounds when configured; nil/non-number → min or 0."
  [field n]
  (let [{:keys [min-val max-val]} (bounds-for field)
        min-v (or min-val 0)
        raw (cond
              (number? n) n
              (string? n) (let [p (js/parseFloat n)]
                            (if (js/isNaN p) min-v p))
              :else min-v)
        capped (max min-v raw)]
    (if (some? max-val)
      (min max-val capped)
      capped)))
