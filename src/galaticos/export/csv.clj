(ns galaticos.export.csv
  "CSV builders for dashboard and championship exports."
  (:require [clojure.data.csv :as data-csv]
            [clojure.string :as str]
            [galaticos.analytics.derived-metrics :as derived]
            [galaticos.db.aggregations :as agg]
            [galaticos.db.championships :as championships-db]
            [galaticos.db.matches :as matches-db]
            [galaticos.db.players :as players-db]))

(def ^:private team-name "GALÁTICOS")

(defn- as-int [v]
  (if (number? v) v 0))

(defn- as-str [v]
  (if (nil? v) "" (str v)))

(defn- write-csv [rows]
  (let [writer (java.io.StringWriter.)]
    (data-csv/write-csv writer rows)
    (.toString writer)))

(defn- top-entry [player metric]
  [(as-str (:name player))
   (as-str (get-in player [:aggregated-stats :total metric] 0))])

(defn- player-dashboard-row
  [p include-derived?]
  (let [total (get-in p [:aggregated-stats :total] {})
        base [(as-str (:name p))
              (as-str (get total :games 0))
              (as-str (get total :goals 0))
              (as-str (get total :assists 0))
              (as-str (get total :titles 0))]]
    (if include-derived?
      (let [d (derived/derived-from-stats total)]
        (into base [(as-str (:goal-contribution d))
                    (as-str (:goal-contribution-per-game d))
                    (as-str (:discipline-index d))]))
      base)))

(defn dashboard-csv
  ([] (dashboard-csv {}))
  ([{:keys [include-derived?] :or {include-derived? false}}]
   (let [players (players-db/find-active)
         tops {:games (map #(top-entry % :games) (agg/top-players-by-metric :games 20))
               :goals (map #(top-entry % :goals) (agg/top-players-by-metric :goals 20))
               :assists (map #(top-entry % :assists) (agg/top-players-by-metric :assists 20))
               :titles (map #(top-entry % :titles) (agg/top-players-by-metric :titles 20))}
         players-rows (map #(player-dashboard-row % include-derived?)
                           (sort-by (comp str/lower-case as-str :name) players))
         left-cols (if include-derived? 8 5)
         left-blank (vec (repeat left-cols ""))
         left-count (count players-rows)
         right-count (apply max 0 (map count (vals tops)))
         rows-count (max left-count right-count)
         padded-player-rows (concat players-rows (repeat (- rows-count left-count) left-blank))
         padded-games (concat (:games tops) (repeat (- rows-count (count (:games tops))) ["" ""]))
         padded-goals (concat (:goals tops) (repeat (- rows-count (count (:goals tops))) ["" ""]))
         padded-assists (concat (:assists tops) (repeat (- rows-count (count (:assists tops))) ["" ""]))
         padded-titles (concat (:titles tops) (repeat (- rows-count (count (:titles tops))) ["" ""]))
         header-1 (if include-derived?
                    ["ATLETA" "JOGOS" "GOLS" "ASSISTENCIAS" "TÍTULOS"
                     "CONTRIB GOL" "CONTRIB/JOGO" "DISCIPLINA"
                     "" "" "" "Top 20 Jogos" "" "" "Top 20 Gols" "" "" "Top 20 Assistências" "" "" "Top 20 Títulos" ""]
                    ["ATLETA" "JOGOS" "GOLS" "ASSISTENCIAS" "TÍTULOS"
                     "" "" "" "" "Top 20 Jogos" "" "" "Top 20 Gols" "" "" "Top 20 Assistências" "" "" "Top 20 Títulos" ""])
         header-2 (if include-derived?
                    (vec (concat (repeat left-cols "")
                                 ["" "" "" "Atleta" "Jogos" "" "Atleta" "Gols" "" "Atleta" "Assistências" "" "Atleta" "Títulos"]))
                    ["" "" "" "" ""
                     "" "" "" "" "Atleta" "Jogos" "" "Atleta" "Gols" "" "Atleta" "Assistências" "" "Atleta" "Títulos"])
         sep (if include-derived? ["" "" ""] ["" "" "" ""])
         rows (map (fn [left g go a t]
                     (into [] (concat left sep g [""] go [""] a [""] t)))
                   padded-player-rows padded-games padded-goals padded-assists padded-titles)]
     (write-csv (into [header-1 header-2] rows)))))

(defn- round-marker-rows [matches]
  (loop [remaining matches
         seen #{}
         out []]
    (if-let [m (first remaining)]
      (let [round (some-> (:round m) str str/trim)]
        (recur (rest remaining)
               (if (str/blank? round) seen (conj seen round))
               (cond-> out
                 (and (not (str/blank? round)) (not (contains? seen round)))
                 (conj [round "" ""]))))
      out)))

(defn- score-text [match]
  (let [result (:result match)
        our (or (when (map? result) (or (:our-score result) (get result "our-score")))
                (:home-score match))
        opp (or (when (map? result) (or (:opponent-score result) (get result "opponent-score")))
                (:away-score match))]
    (if (and (some? our) (some? opp))
      (str our " x " opp)
      "")))

(defn- match-row [match]
  [team-name (score-text match) (as-str (:opponent match))])

(defn championship-csv
  [championship-id]
  (let [championship (championships-db/find-by-id championship-id)]
    (when championship
      (let [stats (agg/player-stats-by-championship championship-id)
            players-by-id (into {}
                                (map (fn [p] [(str (:_id p)) p]))
                                (players-db/find-by-ids (map str (:enrolled-player-ids championship))))
            stats-by-id (into {}
                              (map (fn [s] [(str (:_id s)) s]))
                              stats)
            all-player-ids (distinct (concat (keys players-by-id) (keys stats-by-id)))
            winner-ids (set (map str (:winner-player-ids championship)))
            titles-award-count (as-int (:titles-award-count championship))
            players-rows (map (fn [player-id]
                                (let [player (get players-by-id player-id)
                                      stat (get stats-by-id player-id)
                                      name (or (:player-name stat) (:name player) "")
                                      titles (if (contains? winner-ids player-id) titles-award-count 0)]
                                  [(as-str name)
                                   (as-str (as-int (:games stat)))
                                   (as-str (as-int (:goals stat)))
                                   (as-str (as-int (:assists stat)))
                                   (as-str titles)]))
                              (sort-by (fn [player-id]
                                         (let [player (get players-by-id player-id)
                                               stat (get stats-by-id player-id)
                                               name (or (:player-name stat) (:name player) "")]
                                           (str/lower-case (as-str name))))
                                       all-player-ids))
            matches (matches-db/find-by-championship championship-id)
            round-rows (round-marker-rows matches)
            matches-rows (map match-row matches)
            combined-right-rows (concat round-rows matches-rows)
            max-rows (max (count players-rows) (count combined-right-rows))
            padded-players (concat players-rows (repeat (- max-rows (count players-rows)) ["" "" "" "" ""]))
            padded-right (concat combined-right-rows (repeat (- max-rows (count combined-right-rows)) ["" "" ""]))
            header-1 ["Atletas" "Jogos" "Gols" "Assistencias" "Títulos" "" "" "" "1a fase" ""]
            rows (map (fn [left right]
                        (into [] (concat left ["" "" ""] right)))
                      padded-players padded-right)]
        (write-csv (into [header-1] rows))))))
