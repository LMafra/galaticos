(ns galaticos.util.string
  "Utility functions for working with strings (normalization, search helpers, etc.)."
  (:require [clojure.string :as str])
  (:import [java.text Normalizer Normalizer$Form]))

;; NOTE: These helpers are intended for business-level text normalization,
;; e.g. case-insensitive and accent-insensitive comparisons.

(defn normalize-text
  "Normalize a string for case/accent-insensitive comparisons.

  - Converts to String
  - Applies NFD normalization
  - Removes combining marks (accents)
  - Lowercases and trims

  Returns nil for nil input."
  [s]
  (when (some? s)
    (let [s* (str s)
          decomposed (Normalizer/normalize s* Normalizer$Form/NFD)
          ;; \\p{M} = combining marks (accents/diacritics)
          no-accents (str/replace decomposed #"\p{M}+" "")]
      (-> no-accents
          str/lower-case
          str/trim))))

(defn blank-normalized?
  "Return true if the normalized form of s is nil or blank."
  [s]
  (let [n (normalize-text s)]
    (or (nil? n) (str/blank? n))))

