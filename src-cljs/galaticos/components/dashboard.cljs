(ns galaticos.components.dashboard
  "Dashboard component with statistics"
  (:require [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]))

(defn dashboard
  "Dashboard component - render-only; data fetched via route watcher"
  []
  (let [{:keys [dashboard-stats dashboard-loading? dashboard-error]} @state/app-state]
    [:div
     [:h2 "Dashboard"]
     [:div {:style {:margin-bottom "12px"}}
      [common/button "Atualizar" #(effects/ensure-dashboard! {:force? true})]]
     (cond
       dashboard-error [common/error-message dashboard-error]
       dashboard-loading? [:p "Carregando estatísticas..."]
       dashboard-stats [:div
                        [:h3 "Resumo de Campeonatos"]
                        (if (seq (:championships dashboard-stats))
                          [common/table
                           ["Nome" "Formato" "Partidas" "Jogadores" "Gols Totais" "Gols por Partida"]
                           (map (fn [ch]
                                  [(:championship-name ch)
                                   (:championship-format ch)
                                   (:matches-count ch)
                                   (:players-count ch)
                                   (:total-goals ch)
                                   (if (> (:matches-count ch) 0)
                                     (.toFixed (/ (:total-goals ch) (:matches-count ch)) 2)
                                     "0")])
                                (:championships dashboard-stats))
                           :sortable? true]
                          [:p "Nenhum campeonato encontrado"])
                        [:h3 "Top Artilheiros"]
                        (if (seq (:top-goals dashboard-stats))
                          [common/table
                           ["Nome" "Gols" "Partidas" "Gols por Partida"]
                           (map (fn [player]
                                  [(:name player)
                                   (get-in player [:aggregated-stats :total :goals] 0)
                                   (get-in player [:aggregated-stats :total :games] 0)
                                   (let [games (get-in player [:aggregated-stats :total :games] 0)
                                         goals (get-in player [:aggregated-stats :total :goals] 0)]
                                     (if (> games 0)
                                       (.toFixed (/ goals games) 2)
                                       "0"))])
                                (take 10 (:top-goals dashboard-stats)))
                           :sortable? true]
                          [:p "Nenhum jogador encontrado"])
                        [:h3 "Top Assistências"]
                        (if (seq (:top-assists dashboard-stats))
                          [common/table
                           ["Nome" "Assistências" "Partidas"]
                           (map (fn [player]
                                  [(:name player)
                                   (get-in player [:aggregated-stats :total :assists] 0)
                                   (get-in player [:aggregated-stats :total :games] 0)])
                                (take 10 (:top-assists dashboard-stats)))
                           :sortable? true]
                          [:p "Nenhum jogador encontrado"])
                        [:h3 "Top Partidas"]
                        (if (seq (:top-matches dashboard-stats))
                          [common/table
                           ["Nome" "Partidas" "Gols" "Gols por Partida"]
                           (map (fn [player]
                                  [(:name player)
                                   (get-in player [:aggregated-stats :total :games] 0)
                                   (get-in player [:aggregated-stats :total :goals] 0)
                                   (let [games (get-in player [:aggregated-stats :total :games] 0)
                                         goals (get-in player [:aggregated-stats :total :goals] 0)]
                                     (if (> games 0)
                                       (.toFixed (/ goals games) 2)
                                       "0"))])
                                (take 10 (:top-matches dashboard-stats)))
                           :sortable? true]
                          [:p "Nenhum jogador encontrado"])
                        [:h3 "Top Títulos"]
                        (if (seq (:top-titles dashboard-stats))
                          [common/table
                           ["Nome" "Títulos" "Partidas"]
                           (map (fn [player]
                                  [(:name player)
                                   (get-in player [:aggregated-stats :total :titles] 0)
                                   (get-in player [:aggregated-stats :total :games] 0)])
                                (take 10 (:top-titles dashboard-stats)))
                           :sortable? true]
                          [:p "Nenhum jogador encontrado"])]
       :else [:p "Nenhum dado de dashboard disponível"])]))

