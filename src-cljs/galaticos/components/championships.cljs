(ns galaticos.components.championships
  "Championship list and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]
            [clojure.string :as str]))

(defn championship-list []
  (let [{:keys [championships championships-loading? championships-error]} @state/app-state]
    [:div
     [:h2 "Campeonatos"]
     [:div {:style {:margin-bottom "12px" :display "flex" :gap "8px"}}
      [common/button "Novo Campeonato" #(rfe/push-state :championship-new)]
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
                                           (if-let [id (:_id ch)]
                                             (rfe/push-state :championship-detail {:id id})
                                             (state/set-error! "ID do campeonato ausente; não foi possível abrir detalhes.")))
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
                           [:div {:style {:margin-bottom "16px" :display "flex" :gap "8px"}}
                            [common/button "Editar" #(rfe/push-state :championship-edit {:id id})]
                            [common/button "Deletar"
                             (fn []
                               (when (js/confirm "Tem certeza que deseja deletar este campeonato?")
                                 (api/delete-championship id
                                                          (fn [_result]
                                                            (effects/ensure-championships! {:force? true})
                                                            (rfe/push-state :championships))
                                                          (fn [err]
                                                            (reset! error (str "Erro ao deletar campeonato: " err))))))
                             :style {:background-color "#fee"}]]
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

(defn championship-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        championship-loading? (r/atom is-edit?)
        form-data (r/atom {:name ""
                          :season ""
                          :status ""
                          :format ""
                          :start-date ""
                          :end-date ""
                          :location ""
                          :notes ""
                          :titles-count ""})
        submitting? (r/atom false)
        form-error (r/atom nil)
        valid-form? (fn []
                     (cond
                       (str/blank? (:name @form-data)) "Nome é obrigatório"
                       (str/blank? (:season @form-data)) "Temporada é obrigatória"
                       (and (not (str/blank? (:titles-count @form-data)))
                            (js/isNaN (js/parseInt (:titles-count @form-data) 10))) "Títulos deve ser um número"
                       :else nil))
        prepare-payload (fn []
                         (let [base {:name (str/trim (:name @form-data))
                                    :season (str/trim (:season @form-data))}
                               optional (fn [k]
                                         (when-let [v (get @form-data k)]
                                           (when-not (str/blank? v)
                                             {k (str/trim v)})))]
                           (merge base
                                  (optional :status)
                                  (optional :format)
                                  (optional :start-date)
                                  (optional :end-date)
                                  (optional :location)
                                  (optional :notes)
                                  (when-let [tc (:titles-count @form-data)]
                                    (when-not (str/blank? tc)
                                      {:titles-count (js/parseInt tc 10)})))))
        load-championship! (fn []
                            (when is-edit?
                              (reset! form-error nil)
                              (reset! championship-loading? true)
                              (api/get-championship id
                                                   (fn [result]
                                                     (reset! form-data {:name (or (:name result) "")
                                                                       :season (or (:season result) "")
                                                                       :status (or (:status result) "")
                                                                       :format (or (:format result) "")
                                                                       :start-date (or (:start-date result) "")
                                                                       :end-date (or (:end-date result) "")
                                                                       :location (or (:location result) "")
                                                                       :notes (or (:notes result) "")
                                                                       :titles-count (if (:titles-count result) (str (:titles-count result)) "")})
                                                     (reset! championship-loading? false))
                                                   (fn [err]
                                                     (reset! form-error (str "Erro ao carregar campeonato: " err))
                                                     (reset! championship-loading? false)))))]
    (r/create-class
     {:component-did-mount load-championship!
      :reagent-render
      (fn []
        [:div
         [:h2 (if is-edit? "Editar Campeonato" "Novo Campeonato")]
         (if @championship-loading?
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
                                                     (effects/ensure-championships! {:force? true})
                                                     (rfe/push-state :championships))
                                         on-error (fn [error]
                                                   (reset! submitting? false)
                                                   (reset! form-error (str "Erro ao " (if is-edit? "atualizar" "criar") " campeonato: " error)))]
                                     (if is-edit?
                                       (api/update-championship id payload on-success on-error)
                                       (api/create-championship payload on-success on-error))))))}
            [common/input-field "Nome *" (:name @form-data) #(swap! form-data assoc :name %) :placeholder "Nome do campeonato"]
            [common/input-field "Temporada *" (:season @form-data) #(swap! form-data assoc :season %) :placeholder "Ex: 2024"]
            [common/input-field "Títulos *" (:titles-count @form-data) #(swap! form-data assoc :titles-count %) :type "number" :placeholder "0"]
            [common/select-field "Status" (:status @form-data)
             [["" "Selecione um status"]
              ["active" "Ativo"]
              ["inactive" "Inativo"]
              ["completed" "Concluído"]
              ["cancelled" "Cancelado"]]
             #(swap! form-data assoc :status %)]
            [common/input-field "Formato" (:format @form-data) #(swap! form-data assoc :format %) :placeholder "Ex: Liga, Copa, etc."]
            [common/input-field "Data de Início" (:start-date @form-data) #(swap! form-data assoc :start-date %) :type "date"]
            [common/input-field "Data de Término" (:end-date @form-data) #(swap! form-data assoc :end-date %) :type "date"]
            [common/input-field "Local" (:location @form-data) #(swap! form-data assoc :location %) :placeholder "Local do campeonato"]
            [common/input-field "Notas" (:notes @form-data) #(swap! form-data assoc :notes %) :placeholder "Observações adicionais"]
            (when @form-error
              [common/error-message @form-error])
            [:div {:style {:margin-top "12px" :display "flex" :gap "8px"}}
             [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar"))
              nil
              :type "submit"
              :disabled @submitting?
              :style {:background-color "#4CAF50" :color "white"}]
             [common/button "Cancelar" #(rfe/push-state :championships)]]])])})))

