(ns galaticos.components.aggregations
  "Advanced statistics and aggregations page"
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [galaticos.api :as api]
   [galaticos.state :as state]
   [galaticos.effects :as effects]
   [galaticos.components.common :as common]))

(def ^:private stats-filters-storage-key "galaticos.stats.global-filters")

(def ^:private derived-metrics
  #{"goal-contribution" "goal-contribution-per-game" "discipline-index" "minutes-per-goal"})

(defn- report-error!
  [atom-ref msg]
  (reset! atom-ref msg)
  (state/toast-error! msg))

(defn- avg-goals
  [total matches]
  (if (and matches (pos? matches))
    (.toFixed (/ total matches) 2)
    "0"))

(defn- derived-metric? [metric]
  (contains? derived-metrics metric))

(defn- player-metric-value
  [player metric]
  (let [kw (keyword metric)]
    (if (derived-metric? metric)
      (or (get-in player [:derived kw]) 0)
      (get-in player [:aggregated-stats :total kw] 0))))

(defn- format-metric-cell [metric v]
  (cond
    (and (derived-metric? metric) (nil? v)) "-"
    (number? v) (if (derived-metric? metric)
                  (if (= metric "minutes-per-goal")
                    (.toFixed (double v) 1)
                    (if (#{"goal-contribution-per-game" "discipline-index"} metric)
                      (.toFixed (double v) 2)
                      (str v)))
                  (str v))
    :else (str (or v "-"))))

(defn- read-global-filters! []
  (try
    (when-let [raw (.getItem js/sessionStorage stats-filters-storage-key)]
      (js->clj (.parse js/JSON raw) :keywordize-keys true))
    (catch :default _ {})))

(defn- write-global-filters! [championship-id team-id]
  (try
    (.setItem js/sessionStorage stats-filters-storage-key
              (.stringify js/JSON (clj->js {:championship-id championship-id
                                            :team-id team-id})))
    (catch :default _ nil)))

(defn- global-filters-active? [championship-id team-id]
  (or (not (str/blank? championship-id))
      (not (str/blank? team-id))))

(defn- filter-empty-hint [on-clear!]
  [:div {:class "space-y-3"}
   [:p {:class "app-muted"} "Nenhum resultado com estes filtros."]
   [common/button "Limpar filtros" on-clear! :variant :outline]])

(defn- global-filters-bar
  [championship-id team-id on-champ-change on-team-change on-clear clear-disabled?]
  (let [championships (:championships @state/app-state)
        teams (:teams @state/app-state)
        champ-options (into [["" "Todas as temporadas / campeonatos"]]
                            (map (fn [ch] [(str (:_id ch)) (:name ch)]))
                            (or championships []))
        team-options (into [["" "Todas as equipas"]]
                           (map (fn [t] [(str (:_id t)) (:name t)]))
                           (or teams []))]
    [common/card
     [:h3 {:class "app-section-title"} "Filtros globais"]
     [:p {:class "mt-1 text-xs text-slate-500 dark:text-slate-400"}
      "Temporada/campeonato e equipa aplicam-se a todas as secções abaixo."]
     [:div {:class "mt-4 flex flex-wrap items-end gap-3"}
      [common/select-field
       "Campeonato" @championship-id champ-options on-champ-change
       :container-class "min-w-[220px]"]
      [common/select-field
       "Equipa" @team-id team-options on-team-change
       :container-class "min-w-[200px]"]
      [common/button "Limpar filtros" on-clear
       :variant :ghost
       :disabled clear-disabled?
       :class "text-sm"]]]))

(defn- top-players-tab [global-championship-id-atom global-team-id-atom on-clear-global!]
  (let [metric          (r/atom "goals")
        limit           (r/atom "10")
        position-filter (r/atom "")
        data            (r/atom nil)
        loading?        (r/atom false)
        error           (r/atom nil)
        fetched?        (r/atom false)
        fetch!          (fn []
                          (reset! error nil)
                          (reset! loading? true)
                          (api/get-top-players
                           (cond-> {:metric @metric
                                    :limit  @limit}
                             (not (str/blank? @global-championship-id-atom))
                             (assoc :championship-id @global-championship-id-atom))
                           (fn [result]
                             (reset! data result)
                             (reset! loading? false))
                           (fn [err]
                             (report-error! error (str "Erro: " err))
                             (reset! loading? false))))]
    (fn []
      (when (and (not @fetched?)
                 (not @loading?)
                 (nil? @error))
        (reset! fetched? true)
        (fetch!))
      (let [global-championship-id @global-championship-id-atom
            global-team-id @global-team-id-atom
            metric-label   (case @metric
                             "goals" "Gols"
                             "assists" "Assistências"
                             "games" "Partidas"
                             "titles" "Títulos"
                             "goal-contribution" "Contrib. gol"
                             "goal-contribution-per-game" "Contrib./jogo"
                             "discipline-index" "Disciplina"
                             "minutes-per-goal" "Min/gol"
                             "Gols")
            position-options (into [["" "Todas as posições"]]
                                   (map (fn [pos] [pos pos])
                                        (sort (into #{} (keep :position @data)))))
            filtered-data  (cond->> (or @data [])
                              (not (str/blank? @position-filter))
                              (filter #(= @position-filter (:position %)))
                              (not (str/blank? global-team-id))
                              (filter #(= global-team-id (str (:team-id %)))))
            rows           (when (seq filtered-data)
                             (mapv (fn [p]
                                     (let [mv (player-metric-value p @metric)]
                                       [(or (:name p) "-")
                                        (format-metric-cell @metric mv)
                                        (or (:position p) "-")
                                        (get-in p [:aggregated-stats :total :games] 0)
                                        (avg-goals (get-in p [:aggregated-stats :total :goals] 0)
                                                   (get-in p [:aggregated-stats :total :games] 0))]))
                                   filtered-data))
            filters-active? (or (global-filters-active? global-championship-id global-team-id)
                                  (not (str/blank? @position-filter)))]
        [:div {:class "space-y-4"}
         [:div {:class "flex flex-wrap items-end gap-3"}
          [common/select-field
           "Métrica" @metric
           [["goals" "Gols"]
            ["assists" "Assistências"]
            ["games" "Partidas"]
            ["titles" "Títulos"]
            ["goal-contribution" "Contrib. gol (derivada)"]
            ["goal-contribution-per-game" "Contrib./jogo (derivada)"]
            ["discipline-index" "Disciplina (derivada)"]
            ["minutes-per-goal" "Min/gol (derivada)"]]
           #(do (reset! metric %) (reset! data nil))
           :container-class "min-w-[140px]"]
          [common/select-field
           "Posição (secção)" @position-filter position-options
           #(reset! position-filter %)
           :container-class "min-w-[160px]"]
          [common/select-field
           "Limite" @limit
           [["5" "5"]
            ["10" "10"]
            ["20" "20"]
            ["50" "50"]]
           #(do (reset! limit %) (reset! data nil))
           :container-class "min-w-[100px]"]
          [common/button "Buscar" fetch! :variant :primary]]

         [common/delayed-loading-panel @loading?
          [common/skeleton-table ["Nome" "Métrica" "Posição" "Partidas" "Gols/Partida"] :rows 6]
          (cond
            (seq rows)
            [common/card
             [:h3 {:class "app-section-title"}
              (str "Top Jogadores por " metric-label)]
             [common/table
              ["Nome" (str metric-label) "Posição" "Partidas" "Gols/Partida"]
              rows
              :sortable? true
              :dense? true
              :numeric-columns #{1 3 4}]]

            @data
            (if filters-active?
              [common/card [filter-empty-hint on-clear-global!]]
              [common/card [:p {:class "app-muted"} "Nenhum jogador encontrado."]])

            :else
            [common/card [:p {:class "app-muted"} "Clique em Buscar para carregar os dados."]])]]))))

(defn- comparison-tab [global-championship-id-atom _global-team-id-atom on-clear-global!]
  (let [data     (r/atom nil)
        loading? (r/atom false)
        error    (r/atom nil)
        fetched? (r/atom false)]
    (fn []
      (let [global-championship-id @global-championship-id-atom]
        (when (and (not @fetched?)
                   (not @loading?)
                   (nil? @error))
          (reset! fetched? true)
          (reset! loading? true)
          (api/get-championship-comparison
           (fn [result]
             (reset! data result)
             (reset! loading? false))
           (fn [err]
             (report-error! error (str "Erro: " err))
             (reset! loading? false))))

        (let [filtered (if (str/blank? global-championship-id)
                       @data
                       (filter #(= global-championship-id (str (:championship-id %))) @data))
            filters-active? (global-filters-active? global-championship-id "")]
        [:div {:class "space-y-4"}
         [common/delayed-loading-panel @loading?
          [common/skeleton-table ["Campeonato" "Partidas" "Jogadores" "Gols"] :rows 5]
          (cond
            (seq filtered)
            [common/card
             [:h3 {:class "app-section-title"} "Comparação de Campeonatos"]
             [:div {:class "max-h-[min(70vh,32rem)] overflow-y-auto"}
              [common/table
               ["Campeonato" "Formato" "Partidas" "Jogadores" "Gols" "Gols/Partida"]
               (mapv (fn [ch]
                       [(:championship-name ch)
                        (or (:championship-format ch) "-")
                        (:matches-count ch)
                        (:players-count ch)
                        (:total-goals ch)
                        (if (number? (:avg-goals-per-match ch))
                          (.toFixed (:avg-goals-per-match ch) 2)
                          (avg-goals (:total-goals ch) (:matches-count ch)))])
                     filtered)
               :sortable? true
               :dense? true
               :numeric-columns #{2 3 4 5}]]]

            @data
            (if filters-active?
              [common/card [filter-empty-hint on-clear-global!]]
              [common/card [:p {:class "app-muted"} "Nenhum campeonato com dados encontrado."]])

            :else nil)]])))))

(defn- by-championship-tab [global-championship-id-atom _global-team-id-atom on-clear-global!]
  (let [championship-id (r/atom "")
        player-stats    (r/atom nil)
        position-stats  (r/atom nil)
        position-filter (r/atom "")
        loading?        (r/atom false)
        error           (r/atom nil)
        on-error        (fn [err]
                          (report-error! error (str "Erro: " err))
                          (reset! loading? false))
        fetch!          (fn []
                          (let [cid (if (str/blank? @championship-id)
                                      @global-championship-id-atom
                                      @championship-id)]
                            (if (str/blank? cid)
                              (do
                                (reset! player-stats nil)
                                (reset! position-stats nil)
                                (report-error! error "Selecione um campeonato."))
                              (do
                                (reset! error nil)
                                (reset! loading? true)
                                (api/get-championship-tab-stats
                                 cid
                                 (fn [payload]
                                   (reset! player-stats (:player-stats payload))
                                   (reset! position-stats (:position-stats payload))
                                   (reset! loading? false))
                                 on-error)))))]
    (fn []
      (let [global-championship-id @global-championship-id-atom
            championships (:championships @state/app-state)
            effective-cid (if (str/blank? @championship-id) global-championship-id @championship-id)
            champ-options (into [["" "Usar filtro global ou escolher"]]
                                (map (fn [ch] [(str (:_id ch)) (:name ch)]))
                                (or championships []))
            player-rows   (when (seq @player-stats)
                            (mapv (fn [row]
                                    [(or (:player-name row) "-")
                                     (or (:position row) "-")
                                     (:games row)
                                     (:goals row)
                                     (:assists row)
                                     (if (number? (:goals-per-game row))
                                       (.toFixed (:goals-per-game row) 2)
                                       "-")])
                                  (cond->> @player-stats
                                    (not (str/blank? @position-filter))
                                    (filter #(= @position-filter (:position %))))))
            position-rows (when (seq @position-stats)
                            (mapv (fn [row]
                                    [(or (:position row) "-")
                                     (if (number? (:avg-goals row))
                                       (.toFixed (:avg-goals row) 2)
                                       "-")
                                     (:total-goals row)
                                     (:unique-games row)])
                                  @position-stats))
            filters-active? (or (global-filters-active? global-championship-id "")
                                (not (str/blank? @position-filter))
                                (not (str/blank? @championship-id)))]
        [:div {:class "space-y-4"}
         [:div {:class "flex flex-wrap items-end gap-3"}
          [common/select-field
           "Campeonato (secção)" @championship-id champ-options
           #(do (reset! championship-id %)
                (reset! player-stats nil)
                (reset! position-stats nil)
                (reset! error nil))
           :container-class "min-w-[240px]"]
          [common/select-field
           "Posição (faceta)" @position-filter
           (into [["" "Todas as posições"]]
                 (map (fn [pos] [pos pos])
                      (sort (into #{} (keep :position @player-stats)))))
           #(reset! position-filter %)
           :container-class "min-w-[160px]"]
          [common/button "Buscar" fetch! :variant :primary]]

         [common/delayed-loading-panel @loading?
          [:div {:class "space-y-3"}
           [common/skeleton-line :class "h-6 w-48"]
           [common/skeleton-line :class "h-32 w-full"]]
          (cond
            (or (seq player-rows) (seq position-rows))
            [:div {:class "space-y-6"}
             (when (seq player-rows)
               [common/card
                [:h3 {:class "app-section-title"} "Estatísticas por Jogador"]
                [common/table
                 ["Jogador" "Posição" "Partidas" "Gols" "Assistências" "Gols/Partida"]
                 player-rows
                 :sortable? true
                 :dense? true
                 :numeric-columns #{2 3 4 5}]])
             (when (seq position-rows)
               [common/card
                [:h3 {:class "app-section-title"} "Média de gols por posição"]
                [common/table
                 ["Posição" "Média gols" "Total gols" "Partidas"]
                 position-rows
                 :sortable? true
                 :dense? true
                 :numeric-columns #{1 2 3}]])]

            (not (str/blank? effective-cid))
            (if filters-active?
              [common/card [filter-empty-hint on-clear-global!]]
              [common/card [:p {:class "app-muted"}
                            "Nenhum dado encontrado para o campeonato selecionado."]])

            :else
            [common/card [:p {:class "app-muted"}
                          "Selecione um campeonato (global ou secção) e clique em Buscar."]])]]))))

(defn- reconcile-toolbar []
  (let [loading? (r/atom false)]
    (fn []
      (when (:authenticated @state/app-state)
        [:div {:class "rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/40"}
         [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"}
          [:div {:class "space-y-1"}
           [:p {:class "text-sm font-medium text-slate-800 dark:text-slate-200"}
            "Reconciliar estatísticas"]
           [:p {:class "text-xs text-slate-600 dark:text-slate-400"}
            "Recalcula os totais em ficha (gols, partidas, etc.) a partir das partidas guardadas. Use após corrigir ou apagar partidas."]]
          [common/button "Executar reconciliação"
           #(when-not @loading?
              (reset! loading? true)
              (api/reconcile-aggregated-stats!
               (fn [data]
                 (reset! loading? false)
                 (let [msg (or (:message data)
                               (when (some? (:updated data))
                                 (str "Reconciliação concluída. " (:updated data) " jogador(es) atualizado(s)."))
                               "Reconciliação concluída.")]
                   (state/toast-success! msg)))
               (fn [err _]
                 (reset! loading? false)
                 (state/toast-error! (str err)))))
           :variant :outline
           :disabled @loading?
           :aria-label "Recalcular estatísticas agregadas dos jogadores"]]]))))

(defn aggregations-page []
  (let [stored (read-global-filters!)
        global-championship-id (r/atom (str (or (:championship-id stored) "")))
        global-team-id (r/atom (str (or (:team-id stored) "")))
        active-tab (r/atom :top)
        persist-filters! (fn []
                         (write-global-filters! @global-championship-id @global-team-id))
        clear-global-filters! (fn []
                                (reset! global-championship-id "")
                                (reset! global-team-id "")
                                (persist-filters!))]
    (r/create-class
     {      :component-did-mount
      (fn []
        (effects/ensure-championships!)
        (effects/ensure-teams!)
        (persist-filters!))
      :reagent-render
      (fn []
        [:div {:class "space-y-6"}
         [:div
          [:p {:class "text-sm text-slate-500"} "Agregações"]
          [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} "Estatísticas"]]
         [reconcile-toolbar]
         [global-filters-bar global-championship-id global-team-id
          (fn [v]
            (reset! global-championship-id v)
            (persist-filters!))
          (fn [v]
            (reset! global-team-id v)
            (persist-filters!))
          clear-global-filters!
          (not (global-filters-active? @global-championship-id @global-team-id))]
         [:div {:class "flex flex-wrap gap-2"}
          [common/button "Top Jogadores"
           #(reset! active-tab :top)
           :variant (if (= @active-tab :top) :primary :outline)]
          [common/button "Comparação de Campeonatos"
           #(reset! active-tab :comparison)
           :variant (if (= @active-tab :comparison) :primary :outline)]
          [common/button "Por Campeonato"
           #(reset! active-tab :by-championship)
           :variant (if (= @active-tab :by-championship) :primary :outline)]]
         (case @active-tab
           :top [top-players-tab global-championship-id global-team-id clear-global-filters!]
           :comparison [comparison-tab global-championship-id global-team-id clear-global-filters!]
           :by-championship [by-championship-tab global-championship-id global-team-id clear-global-filters!]
           [:div nil])])})))
