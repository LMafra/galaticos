(ns galaticos.analytics.readiness
  "Criteria for enabling experimental predictive analytics (pure checks).")

(def ^:private default-min-games 5)
(def ^:private default-min-buckets 4)
(def ^:private default-min-distinct-years 2)
(def ^:private default-max-queue-size 100)

(defn- bucket-year [bucket]
  (get-in bucket [:_id :year]))

(defn- distinct-years [evolution]
  (->> evolution
       (keep bucket-year)
       distinct
       count))

(defn- reconciliation-seen?
  [job-meta]
  (boolean (and job-meta
                  (or (:last-incremental job-meta) (:last-full job-meta)))))

(defn- executor-healthy?
  [executor-info max-queue]
  (let [q (long (or (:queue-size executor-info) 0))]
    (<= q max-queue)))

(defn evaluate
  "Return `{:ok boolean :reason keyword-or-nil :checks map}`.
  Inputs:
  - `:total` — aggregated-stats total map
  - `:evolution` — player-performance-evolution rows (optional)
  - `:job-meta` — player_stats_job_meta doc (optional; enables reconcile checks)
  - `:executor-info` — queue snapshot from player-stats-jobs (optional)
  Opts: `:min-games`, `:min-buckets`, `:min-distinct-years`, `:max-queue-size`,
  `:require-reconciliation?` (default true when `:job-meta` is provided)"
  [{:keys [total evolution job-meta executor-info]} & [opts]]
  (let [min-games (or (:min-games opts) default-min-games)
        min-buckets (or (:min-buckets opts) default-min-buckets)
        min-years (or (:min-distinct-years opts) default-min-distinct-years)
        max-queue (or (:max-queue-size opts) default-max-queue-size)
        require-recon? (get opts :require-reconciliation? (some? job-meta))
        games (long (or (:games total) 0))
        buckets (count (or evolution []))
        years (distinct-years evolution)
        checks (cond-> {:min-games? (>= games min-games)
                        :min-buckets? (>= buckets min-buckets)
                        :min-years? (>= years min-years)}
                 require-recon?
                 (assoc :reconciliation-seen? (reconciliation-seen? job-meta))
                 (and require-recon? executor-info)
                 (assoc :executor-healthy? (executor-healthy? executor-info max-queue)))]
    (if (every? val checks)
      {:ok true :checks checks}
      {:ok false
       :reason (cond
                 (not (:min-games? checks)) :insufficient-games
                 (not (:min-buckets? checks)) :insufficient-history
                 (not (:min-years? checks)) :insufficient-seasons
                 (and require-recon? (not (:reconciliation-seen? checks))) :reconciliation-pending
                 (and (contains? checks :executor-healthy?)
                      (not (:executor-healthy? checks))) :job-queue-saturated
                 :else :unknown)
       :checks checks})))

(def experiment-disclaimer
  "Métricas preditivas são experimentais; não usar para decisões críticas sem validação manual.")

(defn readiness-disclaimer
  [{:keys [ok reason]}]
  (when-not ok
    (case reason
      :insufficient-games "Histórico insuficiente: mínimo de partidas não atingido para projeções."
      :insufficient-history "Histórico temporal insuficiente para tendência confiável."
      :insufficient-seasons "Dados em temporadas distintas insuficientes para camada preditiva."
      :reconciliation-pending "Reconciliação de agregados ainda não registrada; projeções indisponíveis."
      :job-queue-saturated "Fila de jobs de agregados saturada; tente novamente após reconciliação."
      "Camada preditiva indisponível para este jogador.")))

(defn disclaimers-for
  [readiness-result]
  (cond-> [experiment-disclaimer]
    (not (:ok readiness-result)) (conj (readiness-disclaimer readiness-result))))
