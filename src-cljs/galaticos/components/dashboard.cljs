(ns galaticos.components.dashboard
  "Dashboard component with statistics"
  (:require [reagent.core :as r]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.components.charts :as charts]
            [galaticos.effects :as effects]
            ["lucide-react" :refer [Users Trophy Target CalendarDays]]))

(defn- avg-goals [total matches]
  (if (pos? matches)
    (.toFixed (/ total matches) 2)
    "0"))

(defn dashboard
  "Dashboard component - render-only; data fetched via route watcher"
  []
  (let [selected-champ (r/atom "all")
        active-tab (r/atom :goals)]
    (fn []
      (let [{:keys [dashboard-stats dashboard-loading? dashboard-error]} @state/app-state
            championships (:championships dashboard-stats)
            filtered-championships (if (and championships (not= @selected-champ "all"))
                                     (filter #(= (:championship-name %) @selected-champ) championships)
                                     championships)
            total-matches (reduce + 0 (map :matches-count (or championships [])))
            total-players (reduce + 0 (map :players-count (or championships [])))
            total-goals (reduce + 0 (map :total-goals (or championships [])))
            top-goals (:top-goals dashboard-stats)
            top-assists (:top-assists dashboard-stats)
            top-matches (:top-matches dashboard-stats)
            top-titles (:top-titles dashboard-stats)
            chart-goals (map (fn [player]
                               {:name (:name player)
                                :value (get-in player [:aggregated-stats :total :goals] 0)})
                             (take 5 top-goals))
            chart-performance (map (fn [ch]
                                     {:name (:championship-name ch)
                                      :value (js/parseFloat (avg-goals (:total-goals ch) (:matches-count ch)))})
                                   (or filtered-championships []))
            champ-options (cons ["all" "Todos os campeonatos"]
                                (map (fn [ch] [(:championship-name ch) (:championship-name ch)])
                                     (or championships [])))]
        [:div {:class "space-y-6"}
         [:div {:class "flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"}
          [:div
           [:p {:class "text-sm text-slate-500"} "Visão geral"]
           [:h2 {:class "text-2xl font-semibold text-slate-900"} "Dashboard"]]
          [:div {:class "flex flex-col gap-2 sm:flex-row sm:items-center"}
           [common/select-field "Campeonato" @selected-champ champ-options #(reset! selected-champ %)
            :container-class "min-w-[220px]"]
           [common/button "Atualizar" #(effects/ensure-dashboard! {:force? true}) :variant :primary]]]

        (cond
          dashboard-error [common/error-message dashboard-error]
          dashboard-loading? [common/loading-spinner]
          dashboard-stats
          [:div {:class "space-y-6"}
           [:div {:class "grid gap-4 md:grid-cols-2 xl:grid-cols-4"}
            [common/stat-card "Jogadores" total-players :icon [:> Users {:size 18}]]
            [common/stat-card "Partidas" total-matches :icon [:> CalendarDays {:size 18}]]
            [common/stat-card "Gols" total-goals :icon [:> Target {:size 18}]]
            [common/stat-card "Campeonatos" (count (or championships [])) :icon [:> Trophy {:size 18}]]]

           [:div {:class "grid gap-4 xl:grid-cols-3"}
            [:div {:class "xl:col-span-2"}
             [common/card
              [:div {:class "flex items-center justify-between"}
               [:h3 {:class "app-section-title"} "Resumo de Campeonatos"]
               [:p {:class "app-muted"} "Média de gols por partida"]]
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
                 :dense? true]
               [:p {:class "app-muted"} "Nenhum campeonato encontrado"])]]
            [:div {:class "space-y-4"}
             [common/card
              [:h3 {:class "app-section-title"} "Top Artilheiros"]
              (if (seq chart-goals)
                [charts/bar-chart {:data chart-goals :x-key "name" :y-key "value" :fill "#820000"}]
                [:p {:class "app-muted"} "Sem dados para exibir"])]
             [common/card
              [:h3 {:class "app-section-title"} "Performance por Campeonato"]
               (if (seq chart-performance)
                 [charts/line-chart {:data chart-performance :x-key "name" :y-key "value" :stroke "#3B82F6"}]
                 [:p {:class "app-muted"} "Sem dados para exibir"])]]]

           [common/card
            [:div {:class "flex flex-wrap items-center justify-between gap-3"}
             [:h3 {:class "app-section-title"} "Destaques por Jogador"]
             [:div {:class "flex flex-wrap gap-2"}
              [common/button "Gols" #(reset! active-tab :goals) :variant (if (= @active-tab :goals) :primary :outline)]
              [common/button "Assistências" #(reset! active-tab :assists) :variant (if (= @active-tab :assists) :primary :outline)]
              [common/button "Partidas" #(reset! active-tab :matches) :variant (if (= @active-tab :matches) :primary :outline)]
              [common/button "Títulos" #(reset! active-tab :titles) :variant (if (= @active-tab :titles) :primary :outline)]]]
            (case @active-tab
              :goals (if (seq top-goals)
                       [common/table
                        ["Nome" "Gols" "Partidas" "Gols/Partida"]
                        (map (fn [player]
                               [(:name player)
                                (get-in player [:aggregated-stats :total :goals] 0)
                                (get-in player [:aggregated-stats :total :games] 0)
                                (avg-goals (get-in player [:aggregated-stats :total :goals] 0)
                                           (get-in player [:aggregated-stats :total :games] 0))])
                             (take 10 top-goals))
                        :sortable? true
                        :dense? true]
                       [:p {:class "app-muted"} "Nenhum jogador encontrado"])
              :assists (if (seq top-assists)
                         [common/table
                          ["Nome" "Assistências" "Partidas"]
                          (map (fn [player]
                                 [(:name player)
                                  (get-in player [:aggregated-stats :total :assists] 0)
                                  (get-in player [:aggregated-stats :total :games] 0)])
                               (take 10 top-assists))
                          :sortable? true
                          :dense? true]
                         [:p {:class "app-muted"} "Nenhum jogador encontrado"])
              :matches (if (seq top-matches)
                         [common/table
                          ["Nome" "Partidas" "Gols" "Gols/Partida"]
                          (map (fn [player]
                                 [(:name player)
                                  (get-in player [:aggregated-stats :total :games] 0)
                                  (get-in player [:aggregated-stats :total :goals] 0)
                                  (avg-goals (get-in player [:aggregated-stats :total :goals] 0)
                                             (get-in player [:aggregated-stats :total :games] 0))])
                               (take 10 top-matches))
                          :sortable? true
                          :dense? true]
                         [:p {:class "app-muted"} "Nenhum jogador encontrado"])
              :titles (if (seq top-titles)
                        [common/table
                         ["Nome" "Títulos" "Partidas"]
                         (map (fn [player]
                                [(:name player)
                                 (get-in player [:aggregated-stats :total :titles] 0)
                                 (get-in player [:aggregated-stats :total :games] 0)])
                              (take 10 top-titles))
                         :sortable? true
                         :dense? true]
                        [:p {:class "app-muted"} "Nenhum jogador encontrado"])
              [:p {:class "app-muted"} "Selecione um filtro"])]]
          :else [:p {:class "app-muted"} "Nenhum dado de dashboard disponível"])]))))

