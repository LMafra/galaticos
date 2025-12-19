(ns galaticos.components.players
  "Player list and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]))

(defn player-list []
  (let [{:keys [players players-loading? players-error]} @state/app-state]
    [:div
     [:h2 "Jogadores"]
     [:div {:style {:margin-bottom "12px"}}
      [common/button "Atualizar" #(effects/ensure-players! {:force? true})]]
     (cond
       players-error [common/error-message players-error]
       players-loading? [:p "Carregando jogadores..."]
       (seq players) [common/table
                      ["Nome" "Apelido" "Posição" "Partidas" "Gols" "Assistências"]
                      (map (fn [player]
                             [(:name player)
                              (:nickname player)
                              (:position player)
                              (get-in player [:aggregated-stats :total :games] 0)
                              (get-in player [:aggregated-stats :total :goals] 0)
                              (get-in player [:aggregated-stats :total :assists] 0)])
                           players)
                      :on-row-click (fn [player]
                                      (rfe/push-state :player-detail {:id (:_id player)}))
                      :row-data players
                      :sortable? true]
       :else [:p "Nenhum jogador encontrado"])]))

(defn player-detail [params]
  (let [player (r/atom nil)
        loading? (r/atom true)
        error (r/atom nil)
        id (:id params)
        load-player! (fn []
                       (reset! error nil)
                       (reset! loading? true)
                       (api/get-player id
                                       (fn [result]
                                         (reset! player result)
                                         (reset! loading? false))
                                       (fn [err]
                                         (reset! error (str "Erro ao carregar jogador: " err))
                                         (reset! loading? false))))] 
    (r/create-class
      {:component-did-mount load-player!
       :reagent-render
       (fn []
         [:div
          [:h2 "Detalhes do Jogador"]
          (cond
            @error [:div
                    [common/error-message @error]
                    [common/button "Tentar novamente" load-player!]]
            @loading? [:p "Carregando..."]
            @player [:div
                     [:h3 (:name @player)]
                     [:p "Apelido: " (:nickname @player)]
                     [:p "Posição: " (:position @player)]
                     [:h4 "Estatísticas Gerais"]
                     [:ul
                      [:li "Partidas: " (get-in @player [:aggregated-stats :total :games] 0)]
                      [:li "Gols: " (get-in @player [:aggregated-stats :total :goals] 0)]
                      [:li "Assistências: " (get-in @player [:aggregated-stats :total :assists] 0)]]
                     [:h4 "Estatísticas por Campeonato"]
                     (if (seq (get-in @player [:aggregated-stats :by-championship]))
                       [common/table
                        ["Campeonato" "Partidas" "Gols" "Assistências"]
                        (map (fn [ch-stats]
                               [(:championship-name ch-stats)
                                (:games ch-stats)
                                (:goals ch-stats)
                                (:assists ch-stats)])
                             (get-in @player [:aggregated-stats :by-championship]))
                        :sortable? true]
                       [:p "Nenhuma estatística por campeonato"])]
            :else [:p "Jogador não encontrado"])])})))

