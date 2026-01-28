(ns galaticos.components.matches
  "Match list and form components"
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]))

(defn match-list []
  (let [{:keys [matches matches-loading? matches-error]} @state/app-state
        delete-match! (fn [match-id]
                        (when (js/confirm "Tem certeza que deseja deletar esta partida?")
                          (api/delete-match match-id
                                           (fn [_result]
                                             (effects/ensure-matches! {:force? true}))
                                           (fn [err]
                                             (state/set-error! (str "Erro ao deletar partida: " err))))))]
    [:div
     [:h2 "Partidas"]
     [:div {:style {:margin-bottom "12px" :display "flex" :gap "8px"}}
      [common/button "Nova Partida" #(rfe/push-state :match-new)]
      [common/button "Atualizar" #(effects/ensure-matches! {:force? true})]]
     [:br] [:br]
     (cond
       matches-error [common/error-message matches-error]
       matches-loading? [:p "Carregando partidas..."]
       (seq matches) [common/table
                      ["Data" "Adversário" "Local" "Resultado" "Ações"]
                      (map (fn [match]
                             (let [match-id (:_id match)]
                               [(.toLocaleDateString (js/Date. (:date match)))
                                (:opponent match)
                                (:venue match)
                                (str (:result match))
                                [:div {:style {:display "flex" :gap "4px"}}
                                 [common/button "Editar"
                                  #(rfe/push-state :match-edit {:id match-id})
                                  :style {:padding "4px 8px" :font-size "12px"}]
                                 [common/button "Deletar"
                                  #(delete-match! match-id)
                                  :style {:padding "4px 8px" :font-size "12px" :background-color "#fee"}]]]))
                           (take 20 matches))
                      :sortable? true]
       :else [:p "Nenhuma partida encontrada"])]))

(defn match-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        match-loading? (r/atom is-edit?)
        form-data (r/atom {:championship-id ""
                           :date ""
                           :opponent ""
                           :venue ""
                           :result ""
                           :player-statistics [{:player-id "" :goals "" :assists "" :minutes-played ""}]})
        submitting? (r/atom false)
        form-error (r/atom nil)
        parse-int (fn [v]
                    (let [trimmed (str/trim (or v ""))]
                      (when (not-empty trimmed)
                        (js/parseInt trimmed 10))))
        valid-form? (fn []
                     (cond
                       (str/blank? (:championship-id @form-data)) "Selecione um campeonato"
                       (str/blank? (:date @form-data)) "Informe a data"
                       (empty? (:player-statistics @form-data)) "Inclua estatísticas de pelo menos um jogador"
                       (some #(clojure.string/blank? (:player-id %)) (:player-statistics @form-data)) "ID do jogador é obrigatório"
                       :else nil))
        prepare-payload (fn []
                          (-> @form-data
                              (update :player-statistics
                                      (fn [stats]
                                        (mapv (fn [stat]
                                                (cond-> stat
                                                  true (update :player-id clojure.string/trim)
                                                  true (update :goals parse-int)
                                                  true (update :assists parse-int)
                                                  true (update :minutes-played parse-int)))
                                              stats)))))
        load-match! (fn []
                     (when is-edit?
                       (reset! form-error nil)
                       (reset! match-loading? true)
                       (api/get-match id
                                     (fn [result]
                                       (reset! form-data {:championship-id (if (:championship-id result) (str (:championship-id result)) "")
                                                          :date (or (:date result) "")
                                                          :opponent (or (:opponent result) "")
                                                          :venue (or (:venue result) "")
                                                          :result (or (:result result) "")
                                                          :player-statistics (if (seq (:player-statistics result))
                                                                              (mapv (fn [stat]
                                                                                     {:player-id (if (:player-id stat) (str (:player-id stat)) "")
                                                                                      :goals (if (:goals stat) (str (:goals stat)) "")
                                                                                      :assists (if (:assists stat) (str (:assists stat)) "")
                                                                                      :minutes-played (if (:minutes-played stat) (str (:minutes-played stat)) "")})
                                                                                   (:player-statistics result))
                                                                              [{:player-id "" :goals "" :assists "" :minutes-played ""}])})
                                       (reset! match-loading? false))
                                     (fn [err]
                                       (reset! form-error (str "Erro ao carregar partida: " err))
                                       (reset! match-loading? false)))))]
    (r/create-class
     {:component-did-mount load-match!
      :reagent-render
      (fn []
        (let [championships (:championships @state/app-state)]
          [:div
           [:h2 (if is-edit? "Editar Partida" "Nova Partida")]
           (if @match-loading?
             [:p "Carregando..."]
             [:form {:on-submit (fn [e]
                                  (.preventDefault e)
                                  (reset! form-error nil)
                                  (if-let [err (valid-form?)]
                                    (reset! form-error err)
                                    (do
                                      (reset! submitting? true)
                                      (let [payload (prepare-payload)
                                            on-success (fn [_result]
                                                        (reset! submitting? false)
                                                        (effects/ensure-matches! {:force? true})
                                                        (rfe/push-state :matches))
                                            on-error (fn [error]
                                                      (reset! submitting? false)
                                                      (reset! form-error (str "Erro ao " (if is-edit? "atualizar" "criar") " partida: " error)))]
                                        (if is-edit?
                                          (api/update-match id payload on-success on-error)
                                          (api/create-match payload on-success on-error))))))}
              [common/select-field
               "Campeonato"
               (:championship-id @form-data)
               (map (fn [ch] [(:_id ch) (:name ch)]) championships)
               #(swap! form-data assoc :championship-id %)]
              [common/input-field "Data" (:date @form-data) #(swap! form-data assoc :date %) :type "date"]
              [common/input-field "Adversário" (:opponent @form-data) #(swap! form-data assoc :opponent %)]
              [common/input-field "Local" (:venue @form-data) #(swap! form-data assoc :venue %)]
              [common/input-field "Resultado" (:result @form-data) #(swap! form-data assoc :result %)]
              [:h4 "Estatísticas dos Jogadores"]
              (doall
               (for [[idx stat] (map-indexed vector (:player-statistics @form-data))]
                 ^{:key idx}
                 [:div {:style {:border "1px solid #eee" :padding "10px" :margin-bottom "10px"}}
                  [common/input-field "ID do Jogador" (:player-id stat)
                   #(swap! form-data assoc-in [:player-statistics idx :player-id] %)]
                  [common/input-field "Gols" (:goals stat)
                   #(swap! form-data assoc-in [:player-statistics idx :goals] %)
                   :type "number"]
                  [common/input-field "Assistências" (:assists stat)
                   #(swap! form-data assoc-in [:player-statistics idx :assists] %)
                   :type "number"]
                  [common/input-field "Minutos jogados" (:minutes-played stat)
                   #(swap! form-data assoc-in [:player-statistics idx :minutes-played] %)
                   :type "number"]
                  [common/button "Remover"
                   #(swap! form-data update :player-statistics
                           (fn [stats]
                             (vec (concat (subvec stats 0 idx) (subvec stats (inc idx))))))
                   :style {:background-color "#fee"}]]))
              [common/button "Adicionar estatística"
               #(swap! form-data update :player-statistics conj {:player-id "" :goals "" :assists "" :minutes-played ""})
               :style {:margin-top "8px"}]
              (when @form-error
                [common/error-message @form-error])
              [:div {:style {:margin-top "12px" :display "flex" :gap "8px"}}
               [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar Partida"))
                nil
                :type "submit"
                :disabled @submitting?
                :style {:background-color "#4CAF50" :color "white"}]
               [common/button "Cancelar" #(rfe/push-state :matches)]]])])})))
