(ns galaticos.components.aggregations
  "Advanced statistics and aggregations page"
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [galaticos.api :as api]
   [galaticos.state :as state]
   [galaticos.components.common :as common]))

(defn- avg-goals
  [total matches]
  (if (and matches (pos? matches))
    (.toFixed (/ total matches) 2)
    "0"))

;; ---------------------------------------------------------------------------
;; Top players tab
;; ---------------------------------------------------------------------------

(defn- top-players-tab []
  (let [metric          (r/atom "goals")
        limit           (r/atom "10")
        championship-id (r/atom "")
        data            (r/atom nil)
        loading?        (r/atom false)
        error           (r/atom nil)
        fetch!          (fn []
                          (reset! error nil)
                          (reset! loading? true)
                          (api/get-top-players
                           (cond-> {:metric @metric
                                    :limit  @limit}
                             (not (str/blank? @championship-id))
                             (assoc :championship-id @championship-id))
                           (fn [result]
                             (reset! data result)
                             (reset! loading? false))
                           (fn [err]
                             (reset! error (str "Erro: " err))
                             (reset! loading? false))))]
    (fn []
      (let [championships  (:championships @state/app-state)
            champ-options  (into
                            [["" "Todos os campeonatos"]]
                            (map (fn [ch] [(str (:_id ch)) (:name ch)]))
                            (or championships []))
            metric-label   (case @metric
                             "goals"   "Gols"
                             "assists" "Assistências"
                             "games"   "Partidas"
                             "titles"  "Títulos"
                             "Gols")
            rows           (when (seq @data)
                             (mapv (fn [p]
                                     [(or (:name p) "-")
                                      (get-in p [:aggregated-stats :total (keyword @metric)] 0)
                                      (get-in p [:aggregated-stats :total :games] 0)
                                      (avg-goals (get-in p [:aggregated-stats :total :goals] 0)
                                                 (get-in p [:aggregated-stats :total :games] 0))])
                                   @data))]
        [:div {:class "space-y-4"}
         [:div {:class "flex flex-wrap items-end gap-3"}
          [common/select-field
           "Métrica" @metric
           [["goals" "Gols"]
            ["assists" "Assistências"]
            ["games" "Partidas"]
            ["titles" "Títulos"]]
           #(do (reset! metric %) (reset! data nil))
           :container-class "min-w-[140px]"]
          [common/select-field
           "Campeonato" @championship-id champ-options
           #(do (reset! championship-id %) (reset! data nil))
           :container-class "min-w-[200px]"]
          [common/select-field
           "Limite" @limit
           [["5" "5"]
            ["10" "10"]
            ["20" "20"]
            ["50" "50"]]
           #(do (reset! limit %) (reset! data nil))
           :container-class "min-w-[100px]"]
          [common/button "Buscar" fetch! :variant :primary]]

         (when @error
           [common/error-message @error])

         (cond
           @loading?
           [common/loading-spinner]

           (seq rows)
           [common/card
            [:h3 {:class "app-section-title"}
             (str "Top Jogadores por " metric-label)]
            [common/table
             ["Nome" (str metric-label) "Partidas" "Gols/Partida"]
             rows
             :sortable? true
             :dense? true]]

           @data
           [common/card
            [:p {:class "app-muted"}
             "Nenhum jogador encontrado."]])]))))

;; ---------------------------------------------------------------------------
;; Championship comparison tab
;; ---------------------------------------------------------------------------

(defn- comparison-tab []
  (let [data     (r/atom nil)
        loading? (r/atom false)
        error    (r/atom nil)
        fetched? (r/atom false)]
    (fn []
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
           (reset! error (str "Erro: " err))
           (reset! loading? false))))

      [:div {:class "space-y-4"}
       (when @error
         [common/error-message @error])

       (cond
         @loading?
         [common/loading-spinner]

         (seq @data)
         [common/card
          [:h3 {:class "app-section-title"} "Comparação de Campeonatos"]
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
                 @data)
           :sortable? true
           :dense? true]]

         @data
         [common/card
          [:p {:class "app-muted"}
           "Nenhum campeonato com dados encontrado."]])])))

;; ---------------------------------------------------------------------------
;; By-championship tab
;; ---------------------------------------------------------------------------

(defn- by-championship-tab []
  (let [championship-id (r/atom "")
        player-stats    (r/atom nil)
        position-stats  (r/atom nil)
        loading?        (r/atom false)
        error           (r/atom nil)
        on-error        (fn [err]
                          (reset! error (str "Erro: " err))
                          (reset! loading? false))
        fetch!          (fn []
                          (if (str/blank? @championship-id)
                            (do
                              (reset! player-stats nil)
                              (reset! position-stats nil)
                              (reset! error "Selecione um campeonato."))
                            (do
                              (reset! error nil)
                              (reset! loading? true)
                              (api/get-player-stats-by-championship
                               @championship-id
                               (fn [players]
                                 (reset! player-stats players)
                                 (api/get-avg-goals-by-position
                                  @championship-id
                                  (fn [positions]
                                    (reset! position-stats positions)
                                    (reset! loading? false))
                                  (fn [_]
                                    (reset! position-stats [])
                                    (reset! loading? false))))
                               on-error))))]
    (fn []
      (let [championships (:championships @state/app-state)
            champ-options (into
                           [["" "Selecione um campeonato"]]
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
                                  @player-stats))
            position-rows (when (seq @position-stats)
                            (mapv (fn [row]
                                    [(or (:position row) "-")
                                     (if (number? (:avg-goals row))
                                       (.toFixed (:avg-goals row) 2)
                                       "-")
                                     (:total-goals row)
                                     (:unique-games row)])
                                  @position-stats))]
        [:div {:class "space-y-4"}
         [:div {:class "flex flex-wrap items-end gap-3"}
          [common/select-field
           "Campeonato" @championship-id champ-options
           #(do (reset! championship-id %)
                (reset! player-stats nil)
                (reset! position-stats nil)
                (reset! error nil))
           :container-class "min-w-[240px]"]
          [common/button "Buscar" fetch! :variant :primary]]
         (when @error
           [common/error-message @error])
         (cond
           @loading?
           [common/loading-spinner]

           (or (seq player-rows) (seq position-rows))
           [:div {:class "space-y-6"}
            (when (seq player-rows)
              [common/card
               [:h3 {:class "app-section-title"} "Estatísticas por Jogador"]
               [common/table
                ["Jogador" "Posição" "Partidas" "Gols" "Assistências" "Gols/Partida"]
                player-rows
                :sortable? true
                :dense? true]])
            (when (seq position-rows)
              [common/card
               [:h3 {:class "app-section-title"} "Média de gols por posição"]
               [common/table
                ["Posição" "Média gols" "Total gols" "Partidas"]
                position-rows
                :sortable? true
                :dense? true]])]

           (not (str/blank? @championship-id))
           [common/card
            [:p {:class "app-muted"}
             "Nenhum dado encontrado para o campeonato selecionado."]]

           :else
           [common/card
            [:p {:class "app-muted"}
             "Selecione um campeonato e clique em Buscar."]])]))))

;; ---------------------------------------------------------------------------
;; Page container
;; ---------------------------------------------------------------------------

(defn aggregations-page []
  (let [active-tab (r/atom :top)]
    (fn []
      [:div {:class "space-y-6"}
       [:div
        [:p {:class "text-sm text-slate-500"} "Agregações"]
        [:h2 {:class "text-2xl font-semibold text-slate-900"} "Estatísticas"]]
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
         :top            [top-players-tab]
         :comparison     [comparison-tab]
         :by-championship [by-championship-tab]
         [:div nil])])))