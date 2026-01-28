(ns galaticos.components.teams
  "Team list, form, and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]
            [clojure.string :as str]))

(defn- normalize-id [v]
  (cond
    (string? v) v
    (map? v) (or (get v "$oid") (get v :$oid))
    :else nil))

(defn team-list []
  (let [{:keys [teams teams-loading? teams-error]} @state/app-state
        load-teams! (fn []
                     (effects/ensure-teams! {:force? true}))]
    [:div
     [:h2 "Times"]
     [:div {:style {:margin-bottom "12px" :display "flex" :gap "8px"}}
      [common/button "Novo Time" #(rfe/push-state :team-new)]
      [common/button "Atualizar" load-teams!]]
     (cond
       teams-error [common/error-message teams-error]
       teams-loading? [:p "Carregando times..."]
       (seq teams) [common/table
                    ["Nome" "Cidade" "Treinador" "Estádio" "Ano Fundação" "Jogadores"]
                    (map (fn [team]
                           [(:name team)
                            (or (:city team) "-")
                            (or (:coach team) "-")
                            (or (:stadium team) "-")
                            (or (:founded-year team) "-")
                            (count (or (:active-player-ids team) []))])
                         teams)
                    :on-row-click (fn [team]
                                   (if-let [id (normalize-id (or (:_id team) (:id team)))]
                                     (rfe/push-state :team-detail {:id id})
                                     (state/set-error! "ID do time ausente; não foi possível abrir detalhes.")))
                    :row-data teams
                    :sortable? true]
       :else [:p "Nenhum time encontrado"])]))

(defn team-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        team-loading? (r/atom is-edit?)
        form-data (r/atom {:name ""
                          :city ""
                          :coach ""
                          :stadium ""
                          :founded-year ""
                          :logo-url ""
                          :notes ""})
        submitting? (r/atom false)
        form-error (r/atom nil)
        valid-form? (fn []
                     (cond
                       (str/blank? (:name @form-data)) "Nome é obrigatório"
                       :else nil))
        prepare-payload (fn []
                         (let [base {:name (str/trim (:name @form-data))}
                               optional (fn [k]
                                         (when-let [v (get @form-data k)]
                                           (when-not (str/blank? v)
                                             {k (str/trim v)})))]
                           (merge base
                                  (optional :city)
                                  (optional :coach)
                                  (optional :stadium)
                                  (when-let [fy (:founded-year @form-data)]
                                    (when-not (str/blank? fy)
                                      {:founded-year (js/parseInt fy 10)}))
                                  (optional :logo-url)
                                  (optional :notes))))
        load-team! (fn []
                    (when is-edit?
                      (reset! form-error nil)
                      (reset! team-loading? true)
               (api/get-team id
                            (fn [result]
                                     (reset! form-data {:name (or (:name result) "")
                                                        :city (or (:city result) "")
                                                        :coach (or (:coach result) "")
                                                        :stadium (or (:stadium result) "")
                                                        :founded-year (if (:founded-year result) (str (:founded-year result)) "")
                                                        :logo-url (or (:logo-url result) "")
                                                        :notes (or (:notes result) "")})
                                     (reset! team-loading? false))
                                   (fn [err]
                                     (reset! form-error (str "Erro ao carregar time: " err))
                                     (reset! team-loading? false)))))]
    (r/create-class
     {:component-did-mount load-team!
      :reagent-render
      (fn []
        [:div
         [:h2 (if is-edit? "Editar Time" "Novo Time")]
         (if @team-loading?
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
                                                     (effects/ensure-teams! {:force? true})
                                                     (rfe/push-state :teams))
                                         on-error (fn [error]
                                                   (reset! submitting? false)
                                                   (reset! form-error (str "Erro ao " (if is-edit? "atualizar" "criar") " time: " error)))]
                                     (if is-edit?
                                       (api/update-team id payload on-success on-error)
                                       (api/create-team payload on-success on-error))))))}
            [common/input-field "Nome *" (:name @form-data) #(swap! form-data assoc :name %) :placeholder "Nome do time"]
            [common/input-field "Cidade" (:city @form-data) #(swap! form-data assoc :city %)]
            [common/input-field "Treinador" (:coach @form-data) #(swap! form-data assoc :coach %)]
            [common/input-field "Estádio" (:stadium @form-data) #(swap! form-data assoc :stadium %)]
            [common/input-field "Ano Fundação" (:founded-year @form-data) #(swap! form-data assoc :founded-year %) :type "number"]
            [common/input-field "URL do Logo" (:logo-url @form-data) #(swap! form-data assoc :logo-url %) :type "url"]
            [common/input-field "Notas" (:notes @form-data) #(swap! form-data assoc :notes %) :placeholder "Observações adicionais"]
            (when @form-error
              [common/error-message @form-error])
            [:div {:style {:margin-top "12px" :display "flex" :gap "8px"}}
             [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar"))
              nil
              :type "submit"
              :disabled @submitting?
              :style {:background-color "#4CAF50" :color "white"}]
             [common/button "Cancelar" #(rfe/push-state :teams)]]])])})))

(defn team-detail [params]
  (let [team (r/atom nil)
        players (r/atom [])
        all-players (r/atom [])
        loading? (r/atom true)
        error (r/atom nil)
        deleting? (r/atom false)
        id (:id params)
        load! (fn []
               (reset! error nil)
               (reset! loading? true)
               (api/get-team id
                            (fn [result]
                              (reset! team result)
                              (reset! loading? false)
                              ;; Load players in this team
                              (let [player-ids (or (:active-player-ids result) [])]
                                (if (seq player-ids)
                                  (api/get-players {}
                                                  (fn [all]
                                                    (reset! all-players all)
                                                    (reset! players (filter (fn [p]
                                                                            (some #(= (normalize-id (or (:_id p) (:id p))) (str %)) player-ids))
                                                                          all)))
                                                  (fn [err] (reset! error (str "Erro ao carregar jogadores: " err)))))
                                  (do
                                    (api/get-players {}
                                                    (fn [all] (reset! all-players all))
                                                    (fn [err] (reset! error (str "Erro ao carregar jogadores: " err))))))))
                            (fn [err]
                              (reset! error (str "Erro ao carregar time: " err))
                              (reset! loading? false))))
        delete-team! (fn []
                      (when (js/confirm "Tem certeza que deseja deletar este time?")
                        (reset! deleting? true)
                        (api/delete-team id
                                       (fn [_result]
                                         (reset! deleting? false)
                                         (effects/ensure-teams! {:force? true})
                                         (rfe/push-state :teams))
                                       (fn [err]
                                         (reset! deleting? false)
                                         (reset! error (str "Erro ao deletar time: " err))))))
        add-player! (fn [player-id]
                     (api/add-player-to-team id player-id
                                            (fn [_result]
                                              (load!))
                                            (fn [err]
                                              (reset! error (str "Erro ao adicionar jogador: " err)))))
        remove-player! (fn [player-id]
                        (api/remove-player-from-team id player-id
                                                    (fn [_result]
                                                      (load!))
                                                    (fn [err]
                                                      (reset! error (str "Erro ao remover jogador: " err)))))]
    (r/create-class
     {:component-did-mount load!
      :reagent-render
      (fn []
        [:div
         [:h2 "Detalhes do Time"]
         (cond
           @error [:div
                  [common/error-message @error]
                  [common/button "Tentar novamente" load!]]
           @loading? [:p "Carregando..."]
           @team [:div
                 [:div {:style {:margin-bottom "16px" :display "flex" :gap "8px"}}
                  [common/button "Editar" #(rfe/push-state :team-edit {:id id})]
                  [common/button "Deletar" delete-team!
                   :disabled @deleting?
                   :style {:background-color "#fee"}]]
                 [:h3 (:name @team)]
                 (when (:city @team) [:p "Cidade: " (:city @team)])
                 (when (:coach @team) [:p "Treinador: " (:coach @team)])
                 (when (:stadium @team) [:p "Estádio: " (:stadium @team)])
                 (when (:founded-year @team) [:p "Ano Fundação: " (:founded-year @team)])
                 (when (:logo-url @team) [:p "Logo: " [:a {:href (:logo-url @team) :target "_blank"} (:logo-url @team)]])
                 (when (:notes @team) [:p "Notas: " (:notes @team)])
                 [:h4 "Jogadores do Time"]
                 (if (seq @players)
                   [common/table
                    ["Nome" "Posição" "Ações"]
                    (map (fn [player]
                           [(:name player)
                            (:position player)
                            [common/button "Remover"
                             #(remove-player! (normalize-id (or (:_id player) (:id player))))
                             :style {:padding "4px 8px" :font-size "12px" :background-color "#fee"]]])
                         @players)
                    :sortable? true]
                   [:p "Nenhum jogador no time"])
                 [:h4 "Adicionar Jogadores"]
                 (if (seq @all-players)
                   (let [team-player-ids (set (map #(normalize-id (or (:_id %) (:id %))) @players))
                         available-players (filter (fn [p]
                                                     (not (contains? team-player-ids (normalize-id (or (:_id p) (:id p))))))
                                                   @all-players)]
                     (if (seq available-players)
                       [common/table
                        ["Nome" "Posição" "Ações"]
                        (map (fn [player]
                               [(:name player)
                                (:position player)
                                [common/button "Adicionar"
                                 #(add-player! (normalize-id (or (:_id player) (:id player))))
                                 :style {:padding "4px 8px" :font-size "12px" :background-color "#4CAF50" :color "white"}]])
                             available-players)
                        :sortable? true]
                       [:p "Todos os jogadores já estão no time"]))
                   [:p "Carregando jogadores..."])]
            :else [:p "Time não encontrado"])])})))
