(ns galaticos.util.fuzzy
  "Fuzzy string similarity for duplicate player name detection (Jaro–Winkler)."
  (:require [clojure.string :as str]
            [galaticos.util.string :as str-util]))

(defn normalize-for-match
  "Normalize name for similarity: accent-insensitive, lowercase, collapse whitespace."
  [s]
  (when (some? s)
    (let [base (or (str-util/normalize-text s) "")]
      (-> base
          (str/replace #"[^\p{L}\p{N}\s]" "")
          (str/replace #"\s+" " ")
          str/trim))))

(defn- common-prefix-length [s1 s2 ^long max-prefix]
  (loop [i 0]
    (if (or (>= i max-prefix)
            (>= i (count s1))
            (>= i (count s2))
            (not= (nth s1 i) (nth s2 i)))
      i
      (recur (inc i)))))

(defn jaro
  "Jaro similarity between two strings (0.0–1.0)."
  [s1 s2]
  (let [s1 (str s1)
        s2 (str s2)
        len1 (count s1)
        len2 (count s2)]
    (cond
      (zero? len1) (if (zero? len2) 1.0 0.0)
      (zero? len2) 0.0
      (= s1 s2) 1.0
      :else
      (let [match-distance (max (quot (max len1 len2) 2) (dec 1))
            s1matches (boolean-array len1)
            s2matches (boolean-array len2)
            matches (atom 0)]
        (doseq [i (range len1)]
          (let [start (max 0 (- i match-distance))
                end (min (+ i match-distance 1) len2)]
            (loop [j start]
              (when (< j end)
                (if (and (not (aget s2matches j))
                         (= (nth s1 i) (nth s2 j)))
                  (do (aset s1matches i true)
                      (aset s2matches j true)
                      (swap! matches inc))
                  (recur (inc j)))))))
        (if (zero? @matches)
          0.0
          (let [s1-matched-chars (for [i (range len1) :when (aget s1matches i)] (nth s1 i))
                s2-matched-chars (for [j (range len2) :when (aget s2matches j)] (nth s2 j))
                transpositions (count (filter false? (map = s1-matched-chars s2-matched-chars)))
                m (double @matches)
                t (/ (double transpositions) 2.0)]
            (/ (+ (/ m (double len1))
                  (/ m (double len2))
                  (/ (- m t) m))
               3.0)))))))

(defn jaro-winkler-similarity
  "Jaro–Winkler similarity with prefix boost (typical range 0.0–1.0)."
  ([s1 s2] (jaro-winkler-similarity s1 s2 {:scaling-factor 0.1 :max-prefix 4}))
  ([s1 s2 {:keys [scaling-factor max-prefix]}]
   (let [s1 (str s1)
         s2 (str s2)
         j (jaro s1 s2)
         l (long (common-prefix-length s1 s2 max-prefix))]
     (+ j (* (double scaling-factor) l (- 1.0 j))))))

(defn- whole-token-in-longer-name-score
  "When shorter normalized name equals a full token of the longer one (e.g. mafra vs lucas mafra),
  Jaro-Winkler stays low; this score surfaces those pairs. Min 4 chars on the short token."
  [n1 n2]
  (when (and n1 n2 (not= n1 n2))
    (let [[short-n long-n] (if (<= (count n1) (count n2)) [n1 n2] [n2 n1])]
      (when (and (>= (count short-n) 4)
                 (not (str/includes? short-n " "))
                 (str/includes? long-n " "))
        (let [tokens (set (str/split long-n #"\s+"))]
          (when (contains? tokens short-n)
            0.95))))))

(defn- token-sorted-name-key
  "Sort whitespace-separated tokens for order-independent comparison."
  [n]
  (when n
    (->> (str/split n #"\s+")
         (remove str/blank?)
         sort
         (str/join " "))))

(defn- token-sort-similarity
  "Jaro–Winkler on token-sorted forms (e.g. lucas mafra vs mafra lucas)."
  [n1 n2]
  (when (and n1 n2)
    (let [a (token-sorted-name-key n1)
          b (token-sorted-name-key n2)]
      (when (and (seq a) (seq b))
        (jaro-winkler-similarity a b)))))

(defn effective-name-similarity
  "Similarity for duplicate pairing: max of Jaro–Winkler, token-sort, and whole-token-in-longer rule."
  [n1 n2]
  (max (jaro-winkler-similarity n1 n2)
       (or (whole-token-in-longer-name-score n1 n2) 0.0)
       (or (token-sort-similarity n1 n2) 0.0)))

(defn find-duplicate-pairs
  "Given distinct players as {:_id :name}, return seq of [id-a id-b similarity]
   for pairs with normalized-name similarity >= threshold."
  [players threshold]
  (let [normed (for [p players
                     :let [id (:_id p)
                           nm (:name p)
                           n (normalize-for-match nm)]
                     :when (and id n (not (str/blank? n)))]
                 {:id id :norm n :raw-name nm})]
    (for [i (range (count normed))
          j (range (inc i) (count normed))
          :let [a (nth normed i)
                b (nth normed j)
                sim (effective-name-similarity (:norm a) (:norm b))]
          :when (>= sim threshold)]
      [(:id a) (:id b) sim])))

(defn duplicate-report-for-players
  "Build {:player-id ... :name ... :candidates [{:id :name :similarity}]} for players with fuzzy duplicates.
   :similarity is 0.0–1.0 (rounded to 4 decimals)."
  [players threshold]
  (let [by-id (into {} (map (juxt :_id identity) players))
        pairs (find-duplicate-pairs players threshold)
        adj (reduce (fn [m [ida idb sim]]
                      (-> m
                          (update ida (fnil assoc {}) idb sim)
                          (update idb (fnil assoc {}) ida sim)))
                    {}
                    pairs)]
    (->> adj
         (keep (fn [[pid neighbors]]
                 (let [p (get by-id pid)]
                   (when (and p (seq neighbors))
                     {:player-id (str pid)
                      :name (:name p)
                      :candidates
                      (->> neighbors
                           (sort-by (comp - second))
                           (mapv (fn [[oid sim]]
                                   (let [op (get by-id oid)]
                                     {:id (str oid)
                                      :name (:name op)
                                      :similarity (/ (Math/round (* sim 10000.0)) 10000.0)}))))}))))
         vec)))
