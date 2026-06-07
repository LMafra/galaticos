(ns galaticos.components.dashboard
  "Dashboard component with statistics"
  (:require
   [clojure.string :as str]
   [goog.object :as gobj]
   [reagent.core :as r]
   [reitit.frontend.easy :as rfe]
   [galaticos.api :as api]
   [galaticos.state :as state]
   [galaticos.components.common :as common]
   [galaticos.ui-copy :as ui-copy]
   [galaticos.components.charts :as charts]
  ["lucide-react" :refer [Users Target CalendarDays CalendarRange Building2 AlertTriangle
                          ClipboardList]]))

(defn- avg-goals
  [total matches]
  (if (pos? matches)
    (.toFixed (/ total matches) 2)
    "0"))

(defn- dashboard-stat-num
  "API `data` may be a CLJS map (keyword/string keys) or a plain JS object from JSON."
  [m k-kw]
  (let [k-str (name k-kw)
        v (cond
            (nil? m) nil
            (map? m) (or (get m k-kw) (get m k-str))
            (object? m) (gobj/get m k-str)
            :else nil)]
    (if (number? v) v 0)))

(defn- championship-key
  [championship]
  (str (:championship-id championship)))

(defn- top5-card
  [title headers rows]
  (let [numeric-cols (into #{} (range 1 (count headers)))]
    [common/card
     [:h3 {:class "app-section-title"} title]
     (if (seq rows)
       [common/table headers rows
        :dense? true
        :show-search? false
        :numeric-columns numeric-cols]
       [:p {:class "app-muted"} "Nenhum jogador encontrado"])]))

(defn- normalize-id [v]
  (cond
    (string? v) v
    (map? v) (or (get v "$oid") (get v :$oid))
    :else nil))

(defn- player-name-link
  [player]
  (let [id (normalize-id (or (:_id player) (:id player)))]
    [:span
     {:class "cursor-pointer text-brand-maroon hover:underline"
      :on-click #(when id (rfe/push-state :player-detail {:id id}))}
     (:name player)]))

(defn- derived-metric-value
  [player k]
  (or (get-in player [:derived k])
      (get-in player [:derived (name k)])
      0))

(defn- format-derived [k v]
  (cond
    (nil? v) "-"
    (= k :minutes-per-goal) (.toFixed (double v) 1)
    (#{:goal-contribution-per-game :discipline-index} k) (.toFixed (double v) 2)
    :else (str v)))

(defn- dashboard-deferred-block
  "Charts + top tables: mounted after first paint to reduce TBT."
  [{:keys [filtered-championships chart-goals chart-performance
           top-goals top-assists top-matches top-titles
           top-goal-contribution top-discipline-index]}]
  [:div {:class "space-y-6"}
   [:div {:class "grid gap-4 xl:grid-cols-3"}
    [:div {:class "xl:col-span-2"}
     [common/card
      [:h3 {:class "app-section-title"} "Resumo de Campeonatos"]
      (if (seq filtered-championships)
        [common/table
         ["Campeonato" "Formato" "Partidas" "Jogadores" "Gols" "Gols/Partida"]
         (map (fn [ch]
                [(:championship-name ch)
                 (:championship-format ch)
                 (:matches-count ch)
                 (:players-count ch)
                 (:total-goals ch)
                 (avg-goals (:total-goals ch) (:matches-count ch))])
              filtered-championships)
         :sortable? true
         :dense? true
         :show-search? false
         :numeric-columns #{2 3 4 5}]
        [:p {:class "app-muted"} "Nenhum campeonato encontrado"])]]
    [:div {:class "space-y-4"}
     [common/card
      [:h3 {:class "app-section-title"} "Top Artilheiros"]
      (if (seq chart-goals)
        [charts/bar-chart
         {:data chart-goals
          :x-key "name"
          :y-key "value"
          :fill "#820000"
          :label "Gols"}]
        [:p {:class "app-muted"} "Sem dados para exibir"])]
     [common/card
      [:h3 {:class "app-section-title"} "Performance por Campeonato"]
      (if (seq chart-performance)
        [charts/line-chart
         {:data chart-performance
          :x-key "name"
          :y-key "value"
          :stroke "#3B82F6"
          :label "Gols/partida"}]
        [:p {:class "app-muted"} "Sem dados para exibir"])]]]

   [:div {:class "grid gap-4 md:grid-cols-2"}
    [top5-card
     "Top 5 - Gols"
     ["Nome" "Gols" "Partidas" "Gols/Partida"]
     (map (fn [player]
            [(player-name-link player)
             (get-in player [:aggregated-stats :total :goals] 0)
             (get-in player [:aggregated-stats :total :games] 0)
             (avg-goals (get-in player [:aggregated-stats :total :goals] 0)
                        (get-in player [:aggregated-stats :total :games] 0))])
          (take 5 top-goals))]
    [top5-card
     "Top 5 - Assistências"
     ["Nome" "Assistências" "Partidas"]
     (map (fn [player]
            [(player-name-link player)
             (get-in player [:aggregated-stats :total :assists] 0)
             (get-in player [:aggregated-stats :total :games] 0)])
          (take 5 top-assists))]
    [top5-card
     "Top 5 - Partidas"
     ["Nome" "Partidas" "Gols" "Gols/Partida"]
     (map (fn [player]
            [(player-name-link player)
             (get-in player [:aggregated-stats :total :games] 0)
             (get-in player [:aggregated-stats :total :goals] 0)
             (avg-goals (get-in player [:aggregated-stats :total :goals] 0)
                        (get-in player [:aggregated-stats :total :games] 0))])
          (take 5 top-matches))]
    [top5-card
     "Top 5 - Títulos"
     ["Nome" "Títulos" "Partidas"]
     (map (fn [player]
            [(player-name-link player)
             (get-in player [:aggregated-stats :total :titles] 0)
             (get-in player [:aggregated-stats :total :games] 0)])
          (take 5 top-titles))]]

   [:div {:class "space-y-2"}
    [:h3 {:class "app-section-title"} "Métricas derivadas"]
    [:p {:class "text-xs text-slate-600 dark:text-slate-400"}
     "Contribuição ofensiva e disciplina calculadas a partir dos totais agregados (ver catálogo de métricas)."]]
   [:div {:class "grid gap-4 md:grid-cols-2"}
    [top5-card
     "Top 5 - Contribuição de gol"
     ["Nome" "Contrib. gol" "Partidas"]
     (map (fn [player]
            [(player-name-link player)
             (format-derived :goal-contribution (derived-metric-value player :goal-contribution))
             (get-in player [:aggregated-stats :total :games] 0)])
          (take 5 (or top-goal-contribution [])))]
    [top5-card
     "Top 5 - Índice de disciplina"
     ["Nome" "Disciplina" "Partidas"]
     (map (fn [player]
            [(player-name-link player)
             (format-derived :discipline-index (derived-metric-value player :discipline-index))
             (get-in player [:aggregated-stats :total :games] 0)])
          (take 5 (or top-discipline-index [])))]]])

(defn- next-upcoming-match [matches]
  (first
   (sort-by #(.getTime (js/Date. (:date %)))
            (filter (fn [m]
                      (let [ms (when-let [d (:date m)] (.getTime (js/Date. d)))]
                        (and ms (>= ms (.getTime (js/Date.))))))
                    (or matches [])))))

(defn- discipline-alert-count [top-discipline-index]
  (count (filter (fn [p]
                   (> (or (get-in p [:derived :discipline-index])
                          (get-in p [:derived "discipline-index"])
                          0)
                      2.0))
                 (or top-discipline-index []))))

(defn- dashboard-alerts-panel
  [{:keys [matches seasons-count top-discipline-index authenticated?]}]
  (let [upcoming (next-upcoming-match matches)
        discipline-n (discipline-alert-count top-discipline-index)]
    (when (or upcoming (pos? discipline-n) (and authenticated? (pos? seasons-count)))
      [common/card
       [:h3 {:class "app-section-title"} "Alertas"]
       [:ul {:class "mt-3 space-y-2 text-sm"}
        (when upcoming
          ^{:key "upcoming"}
          [:li {:class "flex items-start gap-2 text-slate-700 dark:text-slate-200"}
           [:> CalendarDays {:size 16 :class "shrink-0 text-brand-maroon" :aria-hidden true}]
           [:span
            (str "Próxima partida: " (common/format-match-calendar-date (:date upcoming))
                 (when-let [opp (:opponent upcoming)] (str " vs " opp)))]])
        (when (pos? discipline-n)
          ^{:key "discipline"}
          [:li {:class "flex flex-wrap items-center gap-2 text-amber-800 dark:text-amber-200"}
           [:> AlertTriangle {:size 16 :class "shrink-0" :aria-hidden true}]
           [:span (str discipline-n " jogador"
                       (when (> discipline-n 1) "es")
                       " com índice de disciplina elevado.")]
           [:button {:type "button"
                     :class "font-medium text-brand-maroon hover:underline"
                     :on-click #(rfe/push-state :stats)}
            "Ver estatísticas"]])
        (when (and authenticated? (pos? seasons-count))
          ^{:key "seasons"}
          [:li {:class "flex items-start gap-2 text-slate-600 dark:text-slate-300"}
           [:> ClipboardList {:size 16 :class "shrink-0" :aria-hidden true}]
           [:span (str seasons-count " temporada"
                       (when (> seasons-count 1) "s")
                       " registada"
                       (when (> seasons-count 1) "s")
                       " — confira inscrições nos campeonatos.")]])]])))

(defn- dashboard-shortcuts [authenticated? upcoming]
  (when authenticated?
    [:div {:class "flex flex-wrap gap-2"}
     (if upcoming
       [common/button
        (str "Próximo jogo"
             (when-let [opp (:opponent upcoming)] (str ": " opp)))
        #(if-let [mid (some-> (:_id upcoming) str not-empty)]
           (rfe/push-state :match-detail {:id mid})
           (rfe/push-state :matches))
        :variant :outline
        :class "text-sm"]
       [common/button "Partidas" #(rfe/push-state :matches) :variant :outline :class "text-sm"])
     [common/button "Plantel" #(rfe/push-state :players) :variant :outline :class "text-sm"]
     [common/button "Desgaste (min/gol)"
      #(rfe/push-state :stats)
      :variant :outline
      :class "text-sm"]]))

(defn- goals-trend-delta [championships]
  (when (>= (count championships) 2)
    (let [sorted (sort-by :matches-count championships)
          a (first sorted)
          b (last sorted)
          avg-a (if (pos? (:matches-count a))
                  (/ (:total-goals a) (:matches-count a))
                  0)
          avg-b (if (pos? (:matches-count b))
                  (/ (:total-goals b) (:matches-count b))
                  0)]
      (when (and (pos? avg-a) (pos? avg-b))
        (let [pct (* 100 (/ (- avg-b avg-a) avg-a))]
          (str (if (pos? pct) "▲ " "▼ ")
               (.toFixed (js/Math.abs pct) 0) "% gols/partida"))))))

(defn- dashboard-empty-state [authenticated?]
  (if authenticated?
    [common/card
     [:p {:class "text-slate-600 dark:text-slate-300"}
      ui-copy/empty-dashboard-auth]
     [:div {:class "mt-4 flex flex-wrap gap-2"}
      [common/button "Novo campeonato" #(rfe/push-state :championship-new) :variant :primary]
      [common/button "Novo jogador" #(rfe/push-state :player-new) :variant :outline]]]
    [:p {:class "app-muted"}
     ui-copy/empty-dashboard-guest]))

(defn dashboard
  "Dashboard component - render-only; data fetched via route watcher."
  []
  (let [selected-champ   (r/atom "all")
        player-search-q  (r/atom "")
        show-deferred?   (r/atom false)
        csv-exporting?   (r/atom false)]
    (r/create-class
      {:component-did-mount
       (fn [_]
         ;; Defer heavy tables/charts until after first paint (lower TBT).
         (js/requestAnimationFrame
           (fn []
             (js/requestAnimationFrame
               (fn [] (reset! show-deferred? true))))))

       :reagent-render
       (fn []
         (let [{:keys [authenticated dashboard-stats dashboard-loading? matches]}
               @state/app-state
               championships (:championships dashboard-stats)
               teams-count (dashboard-stat-num dashboard-stats :teams-count)
               players-total (dashboard-stat-num dashboard-stats :players-total)
               player-goals-total (dashboard-stat-num dashboard-stats :player-goals-total)
               seasons-count (dashboard-stat-num dashboard-stats :seasons-count)
               filtered-championships
               (if (and championships (not= @selected-champ "all"))
                 (filter #(= (championship-key %) @selected-champ) championships)
                 championships)
               total-matches (reduce + 0 (map :matches-count (or championships [])))
               top-goals (:top-goals dashboard-stats)
               top-assists (:top-assists dashboard-stats)
               top-matches (:top-matches dashboard-stats)
               top-titles (:top-titles dashboard-stats)
               derived-tops @state/dashboard-derived-reaction
               top-goal-contribution (:top-goal-contribution derived-tops)
               top-discipline-index (:top-discipline-index derived-tops)
               chart-goals (map (fn [player]
                                  {:name (:name player)
                                   :value (get-in player [:aggregated-stats :total :goals] 0)})
                                (take 5 top-goals))
               chart-performance (map (fn [ch]
                                        {:name (:championship-name ch)
                                         :value (js/parseFloat
                                                  (avg-goals (:total-goals ch)
                                                             (:matches-count ch)))})
                                      (or filtered-championships []))
               champ-options (into
                               [["all" "Todos os campeonatos"]]
                               (map (fn [ch]
                                      [(championship-key ch) (:championship-name ch)]))
                               (or championships []))
               upcoming-match (next-upcoming-match matches)
               goals-delta (goals-trend-delta championships)
               dashboard-body (cond
                                dashboard-stats
                                [:div {:class "space-y-6"}
                                 [dashboard-shortcuts authenticated upcoming-match]
                                 [:div {:class "grid gap-4 md:grid-cols-2 xl:grid-cols-5"}
                                  ^{:key "players-card"}
                                  [:button {:type "button"
                                            :class "text-left"
                                            :on-click #(rfe/push-state :players)}
                                   [common/stat-card "Jogadores" players-total :icon [:> Users {:size 18}]]]
                                  ^{:key "matches-card"}
                                  [:button {:type "button"
                                            :class "text-left"
                                            :on-click #(rfe/push-state :matches)}
                                   [common/stat-card "Partidas" total-matches :icon [:> CalendarDays {:size 18}]]]
                                  ^{:key "goals-card"}
                                  [:button {:type "button"
                                            :class "text-left"
                                            :on-click #(rfe/push-state :players)}
                                   [common/stat-card "Gols" player-goals-total
                                    :icon [:> Target {:size 18}]
                                    :delta goals-delta]]
                                  ^{:key "seasons-card"}
                                  [:button {:type "button"
                                            :class "text-left"
                                            :on-click #(rfe/push-state :championships)}
                                   [common/stat-card "Temporadas"
                                    seasons-count
                                    :icon [:> CalendarRange {:size 18}]]]
                                  (when authenticated
                                    ^{:key "teams-card"}
                                    [:button {:type "button"
                                              :class "text-left"
                                              :on-click #(rfe/push-state :teams)}
                                     [common/stat-card "Times" (or teams-count 0) :icon [:> Building2 {:size 18}]]])]

                                 [:div {:class "grid gap-4 xl:grid-cols-3"}
                                  [:div {:class "xl:col-span-2 space-y-4"}
                                   (if @show-deferred?
                                     [dashboard-deferred-block
                                      {:filtered-championships filtered-championships
                                       :chart-goals chart-goals
                                       :chart-performance chart-performance
                                       :top-goals top-goals
                                       :top-assists top-assists
                                       :top-matches top-matches
                                       :top-titles top-titles
                                       :top-goal-contribution top-goal-contribution
                                       :top-discipline-index top-discipline-index}]
                                     [:div {:class "min-h-[20rem] rounded-xl border border-dashed border-slate-200 bg-slate-50/50 dark:border-slate-700 dark:bg-slate-800/30 flex items-center justify-center"}
                                      [common/loading-spinner]])]
                                  [:div {:class "space-y-4"}
                                   [dashboard-alerts-panel
                                    {:matches matches
                                     :seasons-count seasons-count
                                     :top-discipline-index top-discipline-index
                                     :authenticated? authenticated}]]]]
                                :else
                                (dashboard-empty-state authenticated))]
           [:div {:class "space-y-6"}
            (when dashboard-loading?
              [:div {:class "rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900/90"}
               [common/delayed-loading-panel true
                [:div {:class "space-y-3"}
                 [common/skeleton-line :class "h-8 w-full"]
                 [common/skeleton-line :class "h-32 w-full"]]
                [common/loading-spinner]]])
            (when-not authenticated
              [common/card
               [:p {:class "text-sm text-slate-600 dark:text-slate-300"}
                "Modo visitante — estatísticas em leitura. Faça login para cadastrar campeonatos, jogadores e partidas."]
               [:div {:class "mt-3"}
                [common/button "Entrar" #(rfe/push-state :login) :variant :primary]]])

            [:div {:class "flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"}
             [:div
              [:p {:class "text-sm text-slate-500"} "Visão geral"]
              [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} "Dashboard"]]
             (when authenticated
               [:div {:class "flex flex-col gap-2 sm:flex-row sm:items-center sm:flex-wrap"}
                [:input {:type "text"
                         :value @player-search-q
                         :placeholder "Buscar jogador..."
                         :on-change #(reset! player-search-q (-> % .-target .-value))
                         :on-key-down (fn [e]
                                        (when (= "Enter" (.-key e))
                                          (let [q (str/trim (or @player-search-q ""))]
                                            (when-not (str/blank? q)
                                              (rfe/push-state :players {} {:q q})))))
                         :class "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm focus:border-brand-maroon focus:outline-none focus:ring-2 focus:ring-brand-maroon/20 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 sm:w-64"}]
                [common/select-field
                 "Campeonato" @selected-champ champ-options #(reset! selected-champ %)
                 :container-class "min-w-[220px]"]
                [common/button "Buscar"
                 (fn []
                   (let [q (str/trim (or @player-search-q ""))]
                     (when-not (str/blank? q)
                       (rfe/push-state :players {} {:q q}))))
                 :variant :outline]
                [common/button
                 (if @csv-exporting? "A exportar…" "Exportar CSV")
                 #(when-not @csv-exporting?
                    (reset! csv-exporting? true)
                    (api/download-csv! "/api/exports/dashboard.csv?include-derived=true"
                                       "galaticos-dashboard.csv"
                                       (fn []
                                         (reset! csv-exporting? false)
                                         (state/toast-success! "Relatório do dashboard exportado com sucesso."))
                                       (fn [err]
                                         (reset! csv-exporting? false)
                                         (state/toast-error! err))))
                 :variant :outline
                 :disabled @csv-exporting?]])]

            (when-not dashboard-loading? dashboard-body)]))})))
