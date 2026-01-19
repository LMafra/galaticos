(ns galaticos.components.players
  "Player list and detail components"
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

(defn player-list []
  (let [{:keys [players players-loading? players-error]} @state/app-state]
    [:div
     [:h2 "Jogadores"]
     [:div {:style {:margin-bottom "12px" :display "flex" :gap "8px"}}
      [common/button "Novo Jogador" #(rfe/push-state :player-new)]
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
                                                     (if-let [id (normalize-id (or (:_id player) (:id player)))]
                                                       (rfe/push-state :player-detail {:id id})
                                                       (state/set-error! "ID do jogador ausente; não foi possível abrir detalhes.")))
                      :row-data players
                      :sortable? true]
       :else [:p "Nenhum jogador encontrado"])]))

(defn player-detail [params]
  (let [player (r/atom nil)
        loading? (r/atom true)
        error (r/atom nil)
        deleting? (r/atom false)
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
                                         (reset! loading? false))))
        delete-player! (fn []
                         (when (js/confirm "Tem certeza que deseja deletar este jogador?")
                           (reset! deleting? true)
                           (api/delete-player id
                                             (fn [_result]
                                               (reset! deleting? false)
                                               (rfe/push-state :players))
                                             (fn [err]
                                               (reset! deleting? false)
                                               (reset! error (str "Erro ao deletar jogador: " err))))))] 
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
                     [:div {:style {:margin-bottom "16px" :display "flex" :gap "8px"}}
                      [common/button "Editar" #(rfe/push-state :player-edit {:id id})]
                      [common/button "Deletar" delete-player!
                       :disabled @deleting?
                       :style {:background-color "#fee"}]]
                     [:h3 (:name @player)]
                     [:p "Apelido: " (or (:nickname @player) "-")]
                     [:p "Posição: " (:position @player)]
                     (when (:team-id @player) [:p "Time ID: " (:team-id @player)])
                     (when (:birth-date @player) [:p "Data de Nascimento: " (:birth-date @player)])
                     (when (:nationality @player) [:p "Nacionalidade: " (:nationality @player)])
                     (when (:height @player) [:p "Altura: " (:height @player)])
                     (when (:weight @player) [:p "Peso: " (:weight @player)])
                     (when (:preferred-foot @player) [:p "Pé Preferido: " (:preferred-foot @player)])
                     (when (:shirt-number @player) [:p "Número da Camisa: " (:shirt-number @player)])
                     (when (:email @player) [:p "Email: " (:email @player)])
                     (when (:phone @player) [:p "Telefone: " (:phone @player)])
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

(defn player-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        teams (r/atom [])
        teams-loading? (r/atom true)
        player-loading? (r/atom is-edit?)
        form-data (r/atom {:name ""
                          :position ""
                          :nickname ""
                          :team-id ""
                          :birth-date ""
                          :nationality ""
                          :height ""
                          :weight ""
                          :preferred-foot ""
                          :shirt-number ""
                          :email ""
                          :phone ""
                          :photo-url ""
                          :notes ""})
        submitting? (r/atom false)
        form-error (r/atom nil)
        valid-form? (fn []
                      (cond
                        (str/blank? (:name @form-data)) "Nome é obrigatório"
                        (str/blank? (:position @form-data)) "Posição é obrigatória"
                        :else nil))
        prepare-payload (fn []
                         (let [base {:name (str/trim (:name @form-data))
                                    :position (str/trim (:position @form-data))}
                               optional (fn [k]
                                         (when-let [v (get @form-data k)]
                                           (when-not (str/blank? v)
                                             {k (str/trim v)})))]
                           (merge base
                                  (optional :nickname)
                                  (when-not (str/blank? (:team-id @form-data))
                                    {:team-id (str/trim (:team-id @form-data))})
                                  (optional :birth-date)
                                  (optional :nationality)
                                  (when-let [h (:height @form-data)]
                                    (when-not (str/blank? h)
                                      {:height (js/parseFloat h)}))
                                  (when-let [w (:weight @form-data)]
                                    (when-not (str/blank? w)
                                      {:weight (js/parseFloat w)}))
                                  (optional :preferred-foot)
                                  (when-let [sn (:shirt-number @form-data)]
                                    (when-not (str/blank? sn)
                                      {:shirt-number (js/parseInt sn 10)}))
                                  (optional :email)
                                  (optional :phone)
                                  (optional :photo-url)
                                  (optional :notes))))
        load-data! (fn []
                    (api/get-teams
                     (fn [result]
                       (let [normalized (cond
                                          (vector? result) result
                                          (sequential? result) (vec result)
                                          (map? result) [result]
                                          (nil? result) []
                                          :else [])]
                         (reset! teams normalized))
                       (reset! teams-loading? false))
                     (fn [err]
                       (reset! form-error (str "Erro ao carregar times: " err))
                       (reset! teams-loading? false)))
                    (when is-edit?
                      (api/get-player id
                                     (fn [result]
                                       (reset! form-data {:name (or (:name result) "")
                                                          :position (or (:position result) "")
                                                          :nickname (or (:nickname result) "")
                                                          :team-id (if (:team-id result) (str (:team-id result)) "")
                                                          :birth-date (or (:birth-date result) "")
                                                          :nationality (or (:nationality result) "")
                                                          :height (if (:height result) (str (:height result)) "")
                                                          :weight (if (:weight result) (str (:weight result)) "")
                                                          :preferred-foot (or (:preferred-foot result) "")
                                                          :shirt-number (if (:shirt-number result) (str (:shirt-number result)) "")
                                                          :email (or (:email result) "")
                                                          :phone (or (:phone result) "")
                                                          :photo-url (or (:photo-url result) "")
                                                          :notes (or (:notes result) "")})
                                       (reset! player-loading? false))
                                     (fn [err]
                                       (reset! form-error (str "Erro ao carregar jogador: " err))
                                       (reset! player-loading? false)))))]
    (r/create-class
     {:component-did-mount load-data!
      :reagent-render
      (fn []
        [:div
         [:h2 (if is-edit? "Editar Jogador" "Novo Jogador")]
         (if (or @player-loading? @teams-loading?)
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
                                                     (effects/ensure-players! {:force? true})
                                                     (rfe/push-state :players))
                                         on-error (fn [error]
                                                   (reset! submitting? false)
                                                   (reset! form-error (str "Erro ao " (if is-edit? "atualizar" "criar") " jogador: " error)))]
                                     (if is-edit?
                                       (api/update-player id payload on-success on-error)
                                       (api/create-player payload on-success on-error))))))}
            [common/input-field "Nome *" (:name @form-data) #(swap! form-data assoc :name %) :placeholder "Nome completo"]
            [common/input-field "Apelido" (:nickname @form-data) #(swap! form-data assoc :nickname %)]
            [common/input-field "Posição *" (:position @form-data) #(swap! form-data assoc :position %) :placeholder "Ex: Atacante, Meia, Zagueiro"]
            (let [teams-seq @teams
                  options (cons ["" "Selecione um time"]
                                (map (fn [team]
                                       [(str (:_id team)) (:name team)])
                                     teams-seq))]
              [common/select-field
               "Time"
               (:team-id @form-data)
               options
               #(swap! form-data assoc :team-id %)])
            [common/input-field "Data de Nascimento" (:birth-date @form-data) #(swap! form-data assoc :birth-date %) :type "date"]
            [common/input-field "Nacionalidade" (:nationality @form-data) #(swap! form-data assoc :nationality %)]
            [common/input-field "Altura (cm)" (:height @form-data) #(swap! form-data assoc :height %) :type "number"]
            [common/input-field "Peso (kg)" (:weight @form-data) #(swap! form-data assoc :weight %) :type "number"]
            [common/input-field "Pé Preferido" (:preferred-foot @form-data) #(swap! form-data assoc :preferred-foot %) :placeholder "Ex: Esquerdo, Direito"]
            [common/input-field "Número da Camisa" (:shirt-number @form-data) #(swap! form-data assoc :shirt-number %) :type "number"]
            [common/input-field "Email" (:email @form-data) #(swap! form-data assoc :email %) :type "email"]
            [common/input-field "Telefone" (:phone @form-data) #(swap! form-data assoc :phone %) :type "tel"]
            [common/input-field "URL da Foto" (:photo-url @form-data) #(swap! form-data assoc :photo-url %) :type "url"]
            [common/input-field "Notas" (:notes @form-data) #(swap! form-data assoc :notes %) :placeholder "Observações adicionais"]
            (when @form-error
              [common/error-message @form-error])
            [:div {:style {:margin-top "12px" :display "flex" :gap "8px"}}
             [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar"))
              nil
              :type "submit"
              :disabled @submitting?
              :style {:background-color "#4CAF50" :color "white"}]
             [common/button "Cancelar" #(rfe/push-state :players)]]])])})))

