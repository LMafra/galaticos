(ns galaticos.components.championships
  "Championship list and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]))

(defn championship-list []
  (let [{:keys [championships championships-loading? championships-error]} @state/app-state]
    [:div
     [:h2 "Campeonatos"]
     [:div {:style {:margin-bottom "12px"}}
      [common/button "Atualizar" #(effects/ensure-championships! {:force? true})]]
     (cond
       championships-error [common/error-message championships-error]
       championships-loading? [:p "Carregando campeonatos..."]
       (seq championships) [common/table
                            ["Nome" "Temporada" "Formato" "Status" "Títulos"]
                            (map (fn [ch]
                                   [(:name ch)
                                    (:season ch)
                                    (:format ch)
                                    (:status ch)
                                    (or (:titles-count ch) 0)])
                                 championships)
                            :on-row-click (fn [ch]
                                            (rfe/push-state :championship-detail {:id (:_id ch)}))
                            :row-data championships
                            :sortable? true]
       :else [:p "Nenhum campeonato encontrado"])]))

(defn championship-detail [params]
  (let [championship (r/atom nil)
        matches (r/atom [])
        loading? (r/atom true)
        error (r/atom nil)
        id (:id params)
        load! (fn []
                (reset! error nil)
                (reset! loading? true)
                (api/get-championship id
                                      (fn [result]
                                        (reset! championship result)
                                        (reset! loading? false))
                                      (fn [err]
                                        (reset! error (str "Erro ao carregar campeonato: " err))
                                        (reset! loading? false)))
                (api/get-matches {:championship-id id}
                                 (fn [result]
                                   (reset! matches result))
                                 (fn [err]
                                   (reset! error (str "Erro ao carregar partidas: " err)))))]
    (r/create-class
      {:component-did-mount
       (fn [] (load!))
       :reagent-render
       (fn []
         [:div
          [:h2 "Detalhes do Campeonato"]
          (cond
            @error [:div
                    [common/error-message @error]
                    [common/button "Tentar novamente" load!]]
            @loading? [:p "Carregando..."]
            @championship [:div
                           [:h3 (:name @championship)]
                           [:p "Temporada: " (:season @championship)]
                           [:p "Formato: " (:format @championship)]
                           [:p "Status: " (:status @championship)]
                           [:p "Títulos: " (or (:titles-count @championship) 0)]
                           [:h4 "Partidas"]
                           (if (seq @matches)
                             [common/table
                              ["Data" "Adversário" "Local" "Resultado"]
                              (map (fn [match]
                                     [(.toLocaleDateString (js/Date. (:date match)))
                                      (:opponent match)
                                      (:venue match)
                                      (str (:result match))])
                                   @matches)
                              :sortable? true]
                             [:p "Nenhuma partida encontrada"])]
            :else [:p "Campeonato não encontrado"])])})))

