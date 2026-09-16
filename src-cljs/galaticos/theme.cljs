(ns galaticos.theme
  "Pure theme preference resolution + sole DOM class mutator (Wave 4).
   Preference: light | dark | system. Effective: light | dark."
  (:require [clojure.string :as str]))

(def storage-key
  "galaticos.theme")

(defn normalize-preference
  "Coerce stored value to light|dark|system. Blank/unknown → system."
  [stored]
  (let [s (some-> stored str str/trim not-empty)]
    (case s
      ("light" "dark" "system") s
      "system")))

(defn resolve-theme
  "Map preference + OS prefers-dark? → effective light|dark for classList."
  [preference prefers-dark?]
  (case (normalize-preference preference)
    "light" "light"
    "dark" "dark"
    ;; system
    (if prefers-dark? "dark" "light")))

(defn dark-effective?
  [preference prefers-dark?]
  (= "dark" (resolve-theme preference prefers-dark?)))

(defn opposite-preference
  "Explicit light|dark opposite of the current effective appearance.
   Used by the header/login toggle (does not cycle through system)."
  [preference prefers-dark?]
  (if (dark-effective? preference prefers-dark?)
    "light"
    "dark"))

(defn apply-theme!
  "Sole mutator of document.documentElement classList for dark mode.
   Pass effective theme (light|dark), not preference."
  [effective-theme]
  (try
    (when-let [cl (some-> js/document .-documentElement .-classList)]
      (if (= "dark" (str effective-theme))
        (.add cl "dark")
        (.remove cl "dark")))
    (catch :default _ nil)))
