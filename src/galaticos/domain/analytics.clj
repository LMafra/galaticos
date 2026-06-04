(ns galaticos.domain.analytics
  "Pure analytics rollups, merge, and derived metric formulas.
  
  ## Modelo híbrido (carreira completa)
  
  O `total` é a soma de todas as linhas `by-championship` (todas as temporadas). Cada linha
  combina até três origens, sem duplicar partidas já contadas na planilha:
  
  1. **Só planilha** — `:pre-match-stats` com totais da tabela; sem partidas no Mongo para
     aquele `championship-id|season` → exibido = planilha.
  2. **Planilha + partidas importadas** — planilha em `:pre-match-stats`; rollup das partidas
     seed com overlap congelado em `:baseline-match-rollup` → exibido = planilha + (rollup − frozen).
  3. **Só partidas** (antigas ou criadas na UI) — sem linha na planilha para aquela temporada;
     `:pre-match-stats` zerado → exibido = rollup de todas as partidas daquele par campeonato|season.
  
  Fórmula por linha:
  
      exibido = pre-match-stats + (rollup_partidas − baseline-match-rollup)
  
  Títulos: só da planilha. O pipeline incremental/agregado considera **todas** as partidas do jogador.
  
  Ver: docs/reference/domain/matches-seasons-hybrid-stats.md"
  (:require [clojure.string :as str]))

(defn- safe-int
  [value]
  (cond
    (number? value) (int value)
    (nil? value) 0
    :else (try
            (int value)
            (catch Exception _ 0))))

(defn- sum-stat
  [entries stat-key]
  (reduce + 0 (map #(safe-int (get % stat-key)) entries)))

(defn goal-contribution
  [{:keys [goals assists] :or {goals 0 assists 0}}]
  (+ (safe-int goals) (safe-int assists)))

(defn goal-contribution-per-game
  [{:keys [goals assists games] :or {goals 0 assists 0 games 0}}]
  (let [g (safe-int games)]
    (if (pos? g)
      (/ (goal-contribution {:goals goals :assists assists}) (double g))
      0)))

(defn discipline-index
  [{:keys [yellow-cards red-cards games]
    :or {yellow-cards 0 red-cards 0 games 0}}]
  (let [g (safe-int games)]
    (if (pos? g)
      (/ (+ (safe-int yellow-cards) (* 3 (safe-int red-cards))) (double g))
      0)))

(defn minutes-quality-ok?
  "True when minutes-played data looks present (avg >= 1 min/game)."
  [{:keys [games minutes-played] :or {games 0 minutes-played 0}}]
  (let [g (safe-int games)
        m (safe-int minutes-played)]
    (and (pos? g) (pos? m) (>= m g))))

(defn minutes-per-goal
  [{:keys [goals minutes-played] :or {goals 0 minutes-played 0}}]
  (let [g (safe-int goals)]
    (if (and (pos? g) (minutes-quality-ok? {:games g :minutes-played minutes-played}))
      (/ (safe-int minutes-played) (double g))
      nil)))

(defn enrich-derived-metrics
  "Attach derived metric keys to a stats map (does not mutate input)."
  [stats]
  (assoc stats
         :goal-contribution (goal-contribution stats)
         :goal-contribution-per-game (goal-contribution-per-game stats)
         :discipline-index (discipline-index stats)
         :minutes-per-goal (minutes-per-goal stats)))

(defn summarize-player-stats
  "Roll up player-statistics entries (same player, any scope) into one map with derived metrics."
  [match-stats]
  (let [rollup (reduce
                (fn [acc ms]
                  (-> acc
                      (update :goals + (safe-int (:goals ms)))
                      (update :assists + (safe-int (:assists ms)))
                      (update :yellow-cards + (safe-int (:yellow-cards ms)))
                      (update :red-cards + (safe-int (:red-cards ms)))
                      (update :minutes-played + (safe-int (:minutes-played ms)))
                      (update :games inc)))
                {:goals 0 :assists 0 :yellow-cards 0 :red-cards 0 :minutes-played 0 :games 0}
                match-stats)]
    (enrich-derived-metrics rollup)))

(defn- card-minute-fields
  [entry]
  {:yellow-cards (safe-int (:yellow-cards entry))
   :red-cards (safe-int (:red-cards entry))
   :minutes-played (safe-int (:minutes-played entry))})

(defn- agg-entity-id-str
  [x]
  (when x (str x)))

(defn- championship-id= [a b]
  (= (agg-entity-id-str a) (agg-entity-id-str b)))

(defn- zero-baseline-stats
  []
  {:games 0 :goals 0 :assists 0})

(defn- stat-triple
  [entry]
  {:games (safe-int (:games entry))
   :goals (safe-int (:goals entry))
   :assists (safe-int (:assists entry))})

(defn- subtract-stat-triples
  [current match]
  {:games (max 0 (- (safe-int (:games current)) (safe-int (:games match))))
   :goals (max 0 (- (safe-int (:goals current)) (safe-int (:goals match))))
   :assists (max 0 (- (safe-int (:assists current)) (safe-int (:assists match))))})

(defn- add-stat-triples
  [baseline delta]
  {:games (+ (safe-int (:games baseline)) (safe-int (:games delta)))
   :goals (+ (safe-int (:goals baseline)) (safe-int (:goals delta)))
   :assists (+ (safe-int (:assists baseline)) (safe-int (:assists delta)))})

(defn- pre-match-stats-meaningful?
  "True when :pre-match-stats carries spreadsheet baseline (not the zero placeholder on match-only rows)."
  [entry]
  (when-let [pm (:pre-match-stats entry)]
    (some pos? (map safe-int (vals pm)))))

(defn- infer-baseline-match-rollup
  [existing-entry match-entry]
  (when (and existing-entry match-entry (pre-match-stats-meaningful? existing-entry))
    (let [pre (stat-triple (:pre-match-stats existing-entry))
          disp (stat-triple existing-entry)
          match (stat-triple match-entry)
          inflation (subtract-stat-triples disp pre)]
      (cond
        (>= (:games inflation) (:games match))
        match
        (>= (:goals inflation) (:goals match))
        match
        :else
        (subtract-stat-triples match inflation)))))

(defn- resolve-baseline-match-rollup
  [existing-entry match-entry]
  (or (:baseline-match-rollup existing-entry)
      (infer-baseline-match-rollup existing-entry match-entry)
      (zero-baseline-stats)))

(defn- sum-match-derived-triple
  [match-derived]
  {:games (sum-stat match-derived :games)
   :goals (sum-stat match-derived :goals)
   :assists (sum-stat match-derived :assists)})

(defn- display-likely-includes-match-rollups?
  "True when cached display already embeds some or all of the match rollup (overlap).
  Used to avoid exibido = display + rollup when :pre-match-stats is absent.
  Requires a substantial display baseline so small rows + new rollups still add normally."
  [display-triple match-triple]
  (let [dg (safe-int (:games display-triple))
        mg (safe-int (:games match-triple))
        dgoals (safe-int (:goals display-triple))
        mgoals (safe-int (:goals match-triple))]
    (or
     ;; Many games: rollup covers a large share of display games
     (and (pos? dg) (>= mg 10) (>= dg mg) (> (/ (double mg) (double dg)) 0.4))
     ;; Rollup goals meet display and rollup games fit inside display games (import in seed)
     (and (pos? dgoals) (>= dgoals 5) (pos? mgoals) (>= mgoals dgoals) (<= mg dg))
     ;; Large table-style display; rollup goals are a material fraction (e.g. Jow: 36/87)
     (and (pos? dgoals) (>= dgoals 10) (pos? mgoals) (pos? mg) (<= mg dg)
          (>= (/ (double mgoals) (double dgoals)) 0.35)))))

(defn- baseline-from-entry
  ([entry]
   (baseline-from-entry entry nil))
  ([entry match-entry]
   (if (pre-match-stats-meaningful? entry)
     (stat-triple (:pre-match-stats entry))
     (let [current (stat-triple entry)]
       (if-not (or (pos? (:games current)) (pos? (:goals current)) (pos? (:assists current)))
         (zero-baseline-stats)
         (if (and match-entry (display-likely-includes-match-rollups? current (stat-triple match-entry)))
           (subtract-stat-triples current (stat-triple match-entry))
           current))))))

(defn- season-key-suffix
  [season]
  (when (some? season)
    (let [s (str/trim (str season))]
      (when-not (str/blank? s) s))))

(defn- match-only-row?
  "Row driven only by match rollups: explicit zero :pre-match-stats from match-only-entry.
  Legacy/planilha rows without :pre-match-stats use the hybrid formula branch."
  [entry]
  (and entry
       (contains? entry :pre-match-stats)
       (not (pre-match-stats-meaningful? entry))))

(defn- merge-championship-entry
  [existing-entry match-entry]
  (let [current-triple (stat-triple existing-entry)
        match-triple (stat-triple match-entry)
        season (or (:season existing-entry) (:season match-entry))
        cards (card-minute-fields match-entry)
        {baseline :pre-match-stats
         frozen :baseline-match-rollup
         display :display}
        (if (match-only-row? existing-entry)
          ;; Só partidas: exibido = rollup atual; frozen acompanha o rollup (sem planilha).
          {:pre-match-stats (zero-baseline-stats)
           :baseline-match-rollup match-triple
           :display match-triple}
          (let [overlap-without-baseline? (and (not (pre-match-stats-meaningful? existing-entry))
                                               (display-likely-includes-match-rollups?
                                                current-triple
                                                match-triple))
                baseline (if overlap-without-baseline?
                           current-triple
                           (baseline-from-entry existing-entry match-entry))
                frozen (or (:baseline-match-rollup existing-entry)
                           (when overlap-without-baseline?
                             match-triple)
                           (resolve-baseline-match-rollup existing-entry match-entry)
                           (zero-baseline-stats))
                delta (subtract-stat-triples match-triple frozen)
                display (add-stat-triples baseline delta)]
            {:pre-match-stats baseline
             :baseline-match-rollup frozen
             :display display}))
        base (merge {:championship-id (:championship-id existing-entry)
                     :championship-name (or (:championship-name match-entry)
                                            (:championship-name existing-entry)
                                            "")
                     :pre-match-stats baseline
                     :baseline-match-rollup frozen
                     :games (:games display)
                     :goals (:goals display)
                     :assists (:assists display)
                     :titles (safe-int (:titles existing-entry))}
                    cards)]
    (cond-> base
      (some? season) (assoc :season season))))

(defn- baseline-only-entry
  [entry]
  (let [baseline (baseline-from-entry entry)
        base (merge (assoc entry
                         :pre-match-stats baseline
                         :games (:games baseline)
                         :goals (:goals baseline)
                         :assists (:assists baseline)
                         :titles (safe-int (:titles entry)))
                    (card-minute-fields entry))]
    (if (some? (:season entry))
      base
      (dissoc base :season))))

(defn- match-only-entry
  [entry]
  (let [baseline (zero-baseline-stats)
        match-triple (stat-triple entry)
        frozen match-triple
        display match-triple
        cards (card-minute-fields entry)]
    (cond-> (merge {:championship-id (:championship-id entry)
                    :championship-name (or (:championship-name entry) "")
                    :pre-match-stats baseline
                    :baseline-match-rollup frozen
                    :games (:games display)
                    :goals (:goals display)
                    :assists (:assists display)
                    :titles 0}
                   cards)
      (some? (season-key-suffix (:season entry)))
      (assoc :season (:season entry)))))

(defn- capture-pre-match-total
  [existing existing-by match-derived]
  (or (:pre-match-total existing)
      (when (empty? existing-by)
        (let [total (or (:total existing) {})
              total-triple {:games (safe-int (:games total))
                            :goals (safe-int (:goals total))
                            :assists (safe-int (:assists total))}
              match-triple (sum-match-derived-triple match-derived)]
          (when (some pos? [(safe-int (:games total))
                            (safe-int (:goals total))
                            (safe-int (:assists total))
                            (safe-int (:titles total))])
            (let [baseline (if (display-likely-includes-match-rollups? total-triple match-triple)
                             (subtract-stat-triples total-triple match-triple)
                             total-triple)]
              {:games (safe-int (:games baseline))
               :goals (safe-int (:goals baseline))
               :assists (safe-int (:assists baseline))
               :titles (safe-int (:titles total))}))))))

(defn player-has-baseline-stats?
  "Verifica se o jogador tem estatísticas de baseline (planilha/seed).
  
  Usado para decidir se deve preservar stats (baseline) ou zerar (sem baseline)
  quando não há rollup de partidas. Retorna true se houver :pre-match-total
  ou :pre-match-stats em alguma linha de by-championship."
  [stats]
  (let [s (or stats {})]
    (or (when-let [pmt (:pre-match-total s)]
          (some pos? (map safe-int (vals pmt))))
        (some (fn [row]
                (or (when-let [pm (:pre-match-stats row)]
                      (some pos? (map safe-int (vals pm))))
                    (some pos? (map safe-int (select-keys row [:games :goals :assists :titles])))))
              (or (:by-championship s) [])))))

(defn- match-aggregate-key
  [championship-id season]
  (str (agg-entity-id-str championship-id) "|" (or (season-key-suffix season) "")))

(defn- sum-stat-entries
  [a b]
  (let [ta (stat-triple a)
        tb (stat-triple b)]
    (cond-> (merge {:championship-id (or (:championship-id a) (:championship-id b))
                    :championship-name (or (:championship-name a) (:championship-name b) "")
                    :games (+ (:games ta) (:games tb))
                    :goals (+ (:goals ta) (:goals tb))
                    :assists (+ (:assists ta) (:assists tb))
                    :yellow-cards (+ (safe-int (:yellow-cards a)) (safe-int (:yellow-cards b)))
                    :red-cards (+ (safe-int (:red-cards a)) (safe-int (:red-cards b)))
                    :minutes-played (+ (safe-int (:minutes-played a)) (safe-int (:minutes-played b)))}
                   (select-keys a [:season]))
      (or (:season a) (:season b)) (assoc :season (or (:season a) (:season b))))))

(defn- fanout-unscoped-rollups-into-match-map
  "Distribui rollups sem :season para a linha de campeonato correspondente.
  
  Quando um rollup tem championship-id mas :season nil/blank e o campeonato
  tem exatamente uma linha com season no baseline, funde o rollup nessa linha.
  
  Se houver múltiplas seasons no baseline, o rollup permanece como linha órfã."
  [match-map match-derived existing-by]
  (reduce
   (fn [mm entry]
     (if-not (and (:championship-id entry)
                  (str/blank? (season-key-suffix (:season entry))))
       mm
       (let [cid (:championship-id entry)
             seasons (->> existing-by
                          (filter #(championship-id= cid (:championship-id %)))
                          (map :season)
                          (keep season-key-suffix)
                          distinct
                          sort
                          vec)]
         (cond
           (not= 1 (count seasons)) mm
           :else
           (let [sole (first seasons)
                 scoped-k (match-aggregate-key cid sole)
                 unscoped-k (match-aggregate-key cid nil)]
             (if (contains? mm scoped-k)
               (-> mm
                   (assoc scoped-k (sum-stat-entries (get mm scoped-k) entry))
                   (dissoc unscoped-k))
               (-> mm
                   (assoc scoped-k entry)
                   (dissoc unscoped-k))))))))
   match-map
   match-derived))

(defn- drop-unscoped-when-scoped-present
  [match-map]
  (let [entries (vec match-map)]
    (reduce
     (fn [mm [k _v]]
       (if-not (str/ends-with? k "|")
         mm
         (let [cid-prefix (subs k 0 (dec (count k)))
               prefix-bar (str cid-prefix "|")
               has-scoped (some (fn [[k2 _v2]]
                                  (and (str/starts-with? k2 prefix-bar)
                                       (> (count k2) (count prefix-bar))))
                                entries)]
           (if has-scoped (dissoc mm k) mm))))
     match-map
     entries)))

(defn merge-aggregated-stats
  "Mescla estatísticas existentes (baseline) com rollup derivado de partidas.
  
  Implementa a fórmula híbrida:
    exibido = pre-match-stats + (rollup_partidas − baseline-match-rollup)
  
  Comportamentos principais:
  - Campeonatos com rollup: soma baseline + delta (rollup - baseline-match-rollup)
  - Campeonatos sem rollup: preserva baseline (não zera)
  - Títulos: sempre do baseline, nunca das partidas
  - Fan-out: rollup sem :season funde na única linha do campeonato (se houver apenas uma)
  
  Opções (opts):
  - :drop-stale-without-match-rollups? (default false) - zera rows sem rollup (só reconcile)
  
  Testes obrigatórios: test/galaticos/db/aggregations_test.clj (baseline, fan-out, idempotência)"
  ([existing match-derived]
   (merge-aggregated-stats existing match-derived {}))
  ([existing match-derived opts]
   (let [drop-stale-without-match? (:drop-stale-without-match-rollups? opts false)
         existing (or existing {})
         existing-by (vec (or (:by-championship existing) []))
         pre-match-total (capture-pre-match-total existing existing-by match-derived)
         match-map (-> (into {}
                             (keep (fn [entry]
                                     (when-let [cid (:championship-id entry)]
                                       [(match-aggregate-key cid (:season entry)) entry])))
                             match-derived)
                       (fanout-unscoped-rollups-into-match-map match-derived existing-by)
                       (drop-unscoped-when-scoped-present))
         existing-keys (into #{}
                             (map (fn [e]
                                    (match-aggregate-key (:championship-id e) (:season e))))
                             existing-by)
         merged-existing (mapv (fn [entry]
                                 (let [k (match-aggregate-key (:championship-id entry) (:season entry))]
                                   (if (contains? match-map k)
                                     (merge-championship-entry entry (get match-map k))
                                     (if drop-stale-without-match?
                                       (let [t (update entry :titles safe-int)]
                                         (assoc t
                                                :games 0 :goals 0 :assists 0
                                                :yellow-cards 0 :red-cards 0 :minutes-played 0))
                                       (baseline-only-entry entry)))))
                               existing-by)
         match-only (for [entry match-derived
                          :let [k (match-aggregate-key (:championship-id entry) (:season entry))]
                          :when (and (not (contains? existing-keys k))
                                     (contains? match-map k))]
                      (match-only-entry entry))
         merged-by (vec (concat merged-existing match-only))
         total-from-rows {:games (sum-stat merged-by :games)
                          :goals (sum-stat merged-by :goals)
                          :assists (sum-stat merged-by :assists)
                          :titles (sum-stat merged-by :titles)
                          :yellow-cards (sum-stat merged-by :yellow-cards)
                          :red-cards (sum-stat merged-by :red-cards)
                          :minutes-played (sum-stat merged-by :minutes-played)}]
     (cond-> {:total (if pre-match-total
                       {:games (+ (:games total-from-rows) (safe-int (:games pre-match-total)))
                        :goals (+ (:goals total-from-rows) (safe-int (:goals pre-match-total)))
                        :assists (+ (:assists total-from-rows) (safe-int (:assists pre-match-total)))
                        :titles (+ (:titles total-from-rows) (safe-int (:titles pre-match-total)))
                        :yellow-cards (:yellow-cards total-from-rows)
                        :red-cards (:red-cards total-from-rows)
                        :minutes-played (:minutes-played total-from-rows)}
                       (select-keys total-from-rows
                                    [:games :goals :assists :titles
                                     :yellow-cards :red-cards :minutes-played]))
              :by-championship merged-by}
       pre-match-total (assoc :pre-match-total pre-match-total)))))

(defn- row-pre-match-triple
  "Baseline triple for combine: explicit :pre-match-stats or displayed row stats."
  [row]
  (if-let [pm (:pre-match-stats row)]
    (stat-triple pm)
    (stat-triple row)))

(defn- combine-pre-match-stats-field
  [existing row]
  (add-stat-triples (row-pre-match-triple existing) (row-pre-match-triple row)))

(defn- combine-players-by-championship-additive
  [players]
  (let [merged-map
        (reduce
         (fn [acc p]
           (reduce
            (fn [acc row]
              (if-let [cid (:championship-id row)]
                (let [k (match-aggregate-key cid (:season row))
                      g (safe-int (:games row))
                      gl (safe-int (:goals row))
                      a (safe-int (:assists row))
                      ti (safe-int (:titles row))
                      yc (safe-int (:yellow-cards row))
                      rc (safe-int (:red-cards row))
                      mp (safe-int (:minutes-played row))]
                  (update acc k
                          (fn [existing]
                            (if existing
                              (-> existing
                                  (update :games + g)
                                  (update :goals + gl)
                                  (update :assists + a)
                                  (update :titles + ti)
                                  (update :yellow-cards + yc)
                                  (update :red-cards + rc)
                                  (update :minutes-played + mp)
                                  (update :championship-name
                                          (fn [nm]
                                            (if (and (string? nm) (not (str/blank? nm)))
                                              nm
                                              (or (:championship-name row) ""))))
                                  (assoc :pre-match-stats (combine-pre-match-stats-field existing row)))
                              (let [pre (row-pre-match-triple row)]
                                (cond-> {:championship-id cid
                                         :championship-name (or (:championship-name row) "")
                                         :pre-match-stats {:games (:games pre)
                                                           :goals (:goals pre)
                                                           :assists (:assists pre)}
                                         :games g :goals gl :assists a :titles ti
                                         :yellow-cards yc :red-cards rc :minutes-played mp}
                                  (some? (season-key-suffix (:season row)))
                                  (assoc :season (:season row))))))))
                acc))
            acc
            (or (:by-championship (:aggregated-stats p)) [])))
         {}
         players)
        rows (->> (vals merged-map)
                  (sort-by (fn [r] [(str (agg-entity-id-str (:championship-id r)))
                                   (str (or (:season r) ""))])))]
    (vec rows)))

(defn combine-players-aggregated-stats
  "Fold multiple players' `:aggregated-stats` caches into one by summing per championship+season."
  [players]
  (when (seq players)
    (let [by (combine-players-by-championship-additive players)]
      {:total {:games (sum-stat by :games)
               :goals (sum-stat by :goals)
               :assists (sum-stat by :assists)
               :titles (sum-stat by :titles)
               :yellow-cards (sum-stat by :yellow-cards)
               :red-cards (sum-stat by :red-cards)
               :minutes-played (sum-stat by :minutes-played)}
       :by-championship by})))

(defn rollup-match-derived-for-player
  "Build match-derived by-championship rows from in-memory match docs for one player-id.
  Each match doc should include :championship-id, optional :season, and :player-statistics."
  [matches player-id]
  (let [pid-str (str player-id)
        grouped
        (reduce
         (fn [acc match-doc]
           (if-let [cid (:championship-id match-doc)]
             (let [stats (filter #(= pid-str (str (:player-id %)))
                                 (:player-statistics match-doc))]
               (if (seq stats)
                 (let [k [(str cid) (or (season-key-suffix (:season match-doc)) "")]
                       row (summarize-player-stats stats)
                       merge-row (fn [existing]
                                   (if existing
                                     {:championship-id cid
                                      :championship-name (or (:championship-name match-doc) "")
                                      :season (:season match-doc)
                                      :games (+ (:games existing) (:games row))
                                      :goals (+ (:goals existing) (:goals row))
                                      :assists (+ (:assists existing) (:assists row))
                                      :yellow-cards (+ (:yellow-cards existing) (:yellow-cards row))
                                      :red-cards (+ (:red-cards existing) (:red-cards row))
                                      :minutes-played (+ (:minutes-played existing) (:minutes-played row))}
                                     (assoc row
                                            :championship-id cid
                                            :championship-name (or (:championship-name match-doc) "")
                                            :season (:season match-doc))))]
                   (update acc k merge-row))
                 acc))
             acc))
         {}
         matches)]
    (vec (vals grouped))))
