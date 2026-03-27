(ns galaticos.components.dashboard
  "Dashboard component with statistics"
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [reitit.frontend.easy :as rfe]
   [galaticos.api :as api]
   [galaticos.state :as state]
   [galaticos.components.common :as common]
   [galaticos.components.charts :as charts]
   [galaticos.effects :as effects]
  ["lucide-react" :refer [Users Trophy Target CalendarDays Building2]]))

(defn- avg-goals
  [total matches]
  (if (pos? matches)
    (.toFixed (/ total matches) 2)
    "0"))

(def ^:private refresh-interval-ms 60000)

(defn- championship-key
  [championship]
  (str (:championship-id championship)))

(defn- top5-card
  [title headers rows]
  [common/card
   [:h3 {:class "app-section-title"} title]
   (if (seq rows)
    [common/table headers rows :dense? true :show-search? false]
     [:p {:class "app-muted"} "Nenhum jogador encontrado"])])

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

(defn dashboard
  "Dashboard component - render-only; data fetched via route watcher. Refreshes every 60s when tab is visible."
  []
  (let [selected-champ   (r/atom "all")
        player-search-q  (r/atom "")
        refresh-timer-id (r/atom nil)]
    (r/create-class
      {:component-did-mount
       (fn [_]
         (reset! refresh-timer-id
                 (js/setInterval
                   (fn []
                     (when (= (.-visibilityState js/document) "visible")
                       (effects/ensure-dashboard! {:force? true})))
                   refresh-interval-ms)))

       :component-will-unmount
       (fn [_]
         (when-let [id @refresh-timer-id]
           (js/clearInterval id)
           (reset! refresh-timer-id nil)))

       :reagent-render
       (fn []
         (let [{:keys [dashboard-stats dashboard-loading? dashboard-error]}
               @state/app-state
               championships (:championships dashboard-stats)
               teams-count (:teams-count dashboard-stats)
               filtered-championships
               (if (and championships (not= @selected-champ "all"))
                 (filter #(= (championship-key %) @selected-champ) championships)
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
                                         :value (js/parseFloat
                                                  (avg-goals (:total-goals ch)
                                                             (:matches-count ch)))})
                                      (or filtered-championships []))
               champ-options (into
                               [["all" "Todos os campeonatos"]]
                               (map (fn [ch]
                                      [(championship-key ch) (:championship-name ch)]))
                               (or championships []))]
           [:div {:class "space-y-6"}
            [:div {:class "flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"}
             [:div
              [:p {:class "text-sm text-slate-500"} "Visão geral"]
              [:h2 {:class "text-2xl font-semibold text-slate-900"} "Dashboard"]]
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
                       :class "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm focus:border-brand-maroon focus:outline-none focus:ring-2 focus:ring-brand-maroon/20 sm:w-64"}]
              [common/select-field
               "Campeonato" @selected-champ champ-options #(reset! selected-champ %)
               :container-class "min-w-[220px]"]
              [common/button "Buscar"
               (fn []
                 (let [q (str/trim (or @player-search-q ""))]
                   (when-not (str/blank? q)
                     (rfe/push-state :players {} {:q q}))))
               :variant :outline]
              [common/button "Exportar CSV"
               #(api/download-csv! "/api/exports/dashboard.csv"
                                   "galaticos-dashboard.csv"
                                   (fn [] nil)
                                   (fn [err] (js/alert err)))
               :variant :outline]]]

            (cond
              dashboard-error [common/error-message dashboard-error]
              dashboard-loading? [common/loading-spinner]
              dashboard-stats
              [:div {:class "space-y-6"}
               [:div {:class "grid gap-4 md:grid-cols-2 xl:grid-cols-5"}
                ^{:key "players-card"}
                [:button {:type "button"
                          :class "text-left"
                          :on-click #(rfe/push-state :players)}
                 [common/stat-card "Jogadores" total-players :icon [:> Users {:size 18}]]]
                ^{:key "matches-card"}
                [:button {:type "button"
                          :class "text-left"
                          :on-click #(rfe/push-state :matches)}
                 [common/stat-card "Partidas" total-matches :icon [:> CalendarDays {:size 18}]]]
                ^{:key "goals-card"}
                [:button {:type "button"
                          :class "text-left"
                          :on-click #(rfe/push-state :players)}
                 [common/stat-card "Gols" total-goals :icon [:> Target {:size 18}]]]
                ^{:key "championships-card"}
                [:button {:type "button"
                          :class "text-left"
                          :on-click #(rfe/push-state :championships)}
                 [common/stat-card "Campeonatos"
                  (count (or championships []))
                  :icon [:> Trophy {:size 18}]]]
                ^{:key "teams-card"}
                [:button {:type "button"
                          :class "text-left"
                          :on-click #(rfe/push-state :teams)}
                 [common/stat-card "Times" (or teams-count 0) :icon [:> Building2 {:size 18}]]]] 

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
                     :show-search? false]
                    [:p {:class "app-muted"} "Nenhum campeonato encontrado"])]]
                [:div {:class "space-y-4"}
                 [common/card
                  [:h3 {:class "app-section-title"} "Top Artilheiros"]
                  (if (seq chart-goals)
                    [charts/bar-chart
                     {:data chart-goals
                      :x-key "name"
                      :y-key "value"
                      :fill "#820000"}]
                    [:p {:class "app-muted"} "Sem dados para exibir"])]
                 [common/card
                  [:h3 {:class "app-section-title"} "Performance por Campeonato"]
                  (if (seq chart-performance)
                    [charts/line-chart
                     {:data chart-performance
                      :x-key "name"
                      :y-key "value"
                      :stroke "#3B82F6"}]
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
                      (take 5 top-titles))]]]
              :else
              [:p {:class "app-muted"}
               "Nenhum dado de dashboard disponível"])]))})))