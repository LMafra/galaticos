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
  (let [{:keys [matches matches-loading? matches-error]} @state/app-state]
    [:div
     [:h2 "Partidas"]
     [common/button "Nova Partida" #(rfe/push-state :match-new)]
     [:div {:style {:margin "12px 0"}}
      [common/button "Atualizar" #(effects/ensure-matches! {:force? true})]]
     [:br] [:br]
     (cond
       matches-error [common/error-message matches-error]
       matches-loading? [:p "Carregando partidas..."]
       (seq matches) [common/table
                      ["Data" "Adversário" "Local" "Resultado"]
                      (map (fn [match]
                             [(.toLocaleDateString (js/Date. (:date match)))
                              (:opponent match)
                              (:venue match)
                              (str (:result match))])
                           (take 20 matches))
                      :sortable? true]
       :else [:p "Nenhuma partida encontrada"])]))

(defn match-form []
  (let [form-data (r/atom {:championship-id ""
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
        valid-form?
        (fn []
          (cond
            (str/blank? (:championship-id @form-data)) "Selecione um campeonato"
            (str/blank? (:date @form-data)) "Informe a data"
            (empty? (:player-statistics @form-data)) "Inclua estatísticas de pelo menos um jogador"
            (some #(clojure.string/blank? (:player-id %)) (:player-statistics @form-data)) "ID do jogador é obrigatório"
            :else nil))
        prepare-payload
        (fn []
          (-> @form-data
              (update :player-statistics
                      (fn [stats]
                        (mapv (fn [stat]
                                (cond-> stat
                                  true (update :player-id clojure.string/trim)
                                  true (update :goals parse-int)
                                  true (update :assists parse-int)
                                  true (update :minutes-played parse-int)))
                              stats)))))]
    (fn []
      (let [championships (:championships @state/app-state)]
        [:div
         [:h2 "Nova Partida"]
         [:form {:on-submit (fn [e]
                              (.preventDefault e)
                              (reset! form-error nil)
                              (if-let [err (valid-form?)]
                                (reset! form-error err)
                                (do
                                  (reset! submitting? true)
                                  (api/create-match (prepare-payload)
                                                    (fn [_result]
                                                      (reset! submitting? false)
                                                      (rfe/push-state :matches))
                                                    (fn [error]
                                                      (reset! submitting? false)
                                                      (reset! form-error (str "Erro ao criar partida: " error)))))))}
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
          [common/button (if @submitting? "Enviando..." "Criar Partida")
           nil
           :style {:margin-top "12px"}
           :class "primary"
           :disabled @submitting?]]]))))

