(ns galaticos.components.teams
  "Team list, form, and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.components.player-picker :as player-picker]
            [galaticos.effects :as effects]
            [clojure.string :as str]
            ["lucide-react" :refer [Shield UserPlus]]))

(defn team-list []
  (let [{:keys [teams teams-loading? teams-error]} @state/app-state
        load-teams! (fn []
                     (effects/ensure-teams! {:force? true}))]
    [:div {:class "space-y-6"}
     [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
      [:div
       [:p {:class "text-sm text-slate-500"} "Gestão de equipes"]
       [:h2 {:class "text-2xl font-semibold text-slate-900"} "Times"]]
      [:div {:class "flex flex-wrap gap-2"}
       [common/button "Novo Time" #(rfe/push-state :team-new) :variant :primary]
       [common/button "Atualizar" load-teams! :variant :outline]]]
     [common/card
      (cond
        teams-error [common/error-message teams-error]
        teams-loading? [common/loading-spinner]
        (seq teams) [common/table
                     ["Nome" "Cidade" "Treinador" "Estádio" "Ano Fundação" "Jogadores"]
                     (map (fn [team]
                            [(:name team)
                             (or (:city team) "-")
                             (or (:coach team) "-")
                             (or (:stadium team) "-")
                             (or (:founded-year team) "-")
                             [common/badge (count (or (:active-player-ids team) [])) :variant :info]])
                          teams)
                     :on-row-click (fn [team]
                                     (if-let [id (player-picker/normalize-id (or (:_id team) (:id team)))]
                                       (rfe/push-state :team-detail {:id id})
                                       (state/set-error! "ID do time ausente; não foi possível abrir detalhes.")))
                     :row-data teams
                     :sortable? true]
        :else [:p {:class "app-muted"} "Nenhum time encontrado"])]]))

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
        field-errors (r/atom {})
        valid-form? (fn []
                      (let [errs (cond-> {}
                                   (str/blank? (:name @form-data)) (assoc :name "Nome é obrigatório"))]
                        (when (seq errs) errs)))
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
        [:div {:class "space-y-6"}
         [:div
          [:p {:class "text-sm text-slate-500"} "Cadastro"]
          [:h2 {:class "text-2xl font-semibold text-slate-900"} (if is-edit? "Editar Time" "Novo Time")]]
         (if @team-loading?
           [common/loading-spinner]
           [:form {:class "space-y-6"
                   :on-submit (fn [e]
                                (.preventDefault e)
                                (reset! form-error nil)
                                (reset! field-errors {})
                                (if-let [errs (valid-form?)]
                                  (reset! field-errors errs)
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
            [common/card
             [:h3 {:class "app-section-title"} "Informações do time"]
             [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
              [common/input-field "Nome" (:name @form-data) #(swap! form-data assoc :name %) :placeholder "Nome do time" :required? true :error (:name @field-errors)]
              [common/input-field "Cidade" (:city @form-data) #(swap! form-data assoc :city %)]
              [common/input-field "Treinador" (:coach @form-data) #(swap! form-data assoc :coach %)]
              [common/input-field "Estádio" (:stadium @form-data) #(swap! form-data assoc :stadium %)]
              [common/input-field "Ano Fundação" (:founded-year @form-data) #(swap! form-data assoc :founded-year %) :type "number"]
              [common/input-field "URL do Logo" (:logo-url @form-data) #(swap! form-data assoc :logo-url %) :type "url"]]]
            [common/card
             [:h3 {:class "app-section-title"} "Notas"]
             [common/input-field "Observações" (:notes @form-data) #(swap! form-data assoc :notes %) :placeholder "Observações adicionais"]]
            (when @form-error
              [common/error-message @form-error])
            [:div {:class "flex flex-wrap gap-2"}
             [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar"))
              nil
              :type "submit"
              :disabled @submitting?
              :variant :primary]
             [common/button "Cancelar" #(rfe/push-state :teams) :variant :outline]]])])})))

(defn team-detail [params]
  (let [team (r/atom nil)
        players (r/atom [])
        all-players (r/atom [])
        loading? (r/atom true)
        error (r/atom nil)
        not-found? (r/atom false)
        deleting? (r/atom false)
        id (:id params)
        load! (fn []
                (reset! error nil)
                (reset! not-found? false)
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
                                                       (let [catalog (api/coerce-player-list all)]
                                                         (reset! all-players catalog)
                                                         (reset! players (filter (fn [p]
                                                                                   (some #(= (player-picker/normalize-id (or (:_id p) (:id p))) (str %)) player-ids))
                                                                                 catalog))))
                                                     (fn [err _resp]
                                                       (reset! error (str "Erro ao carregar jogadores: " err))))
                                    (api/get-players {}
                                                     (fn [all]
                                                       (reset! all-players (api/coerce-player-list all)))
                                                     (fn [err _resp]
                                                       (reset! error (str "Erro ao carregar jogadores: " err)))))))
                              (fn [err resp]
                                (reset! loading? false)
                                (if (and resp (= 404 (:status resp)))
                                  (do (reset! not-found? true)
                                      (reset! error "Time não encontrado."))
                                  (do (reset! not-found? false)
                                      (reset! error (str "Erro ao carregar time: " err)))))))
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
        [:div {:class "space-y-6"}
         (cond
           @error (if @not-found?
                    [common/not-found-resource @error #(rfe/push-state :teams)]
                    [:div
                     [common/error-message @error]
                     [common/button "Tentar novamente" load! :variant :outline]])
           @loading? [common/loading-spinner]
           @team [:<>
                  [:div {:class "flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"}
                   [:div {:class "flex items-center gap-3"}
                    [:div {:class "rounded-xl bg-brand-maroon/10 p-3 text-brand-maroon"}
                     [:> Shield {:size 20}]]
                    [:div
                     [:p {:class "text-sm text-slate-500"} "Time"]
                     [:h2 {:class "text-2xl font-semibold text-slate-900"} (:name @team)]]]
                   [:div {:class "flex flex-wrap gap-2"}
                    [common/button "Editar" #(rfe/push-state :team-edit {:id id}) :variant :outline]
                    [common/button "Deletar" delete-team!
                     :disabled @deleting?
                     :variant :danger]]]

                  [:div {:class "grid gap-4 md:grid-cols-2"}
                   [common/card
                    [:h3 {:class "app-section-title"} "Informações"]
                    [:div {:class "mt-3 space-y-2 text-sm text-slate-600"}
                     (when (:city @team) [:p [:span {:class "font-medium text-slate-800"} "Cidade: "] (:city @team)])
                     (when (:coach @team) [:p [:span {:class "font-medium text-slate-800"} "Treinador: "] (:coach @team)])
                     (when (:stadium @team) [:p [:span {:class "font-medium text-slate-800"} "Estádio: "] (:stadium @team)])
                     (when (:founded-year @team) [:p [:span {:class "font-medium text-slate-800"} "Ano Fundação: "] (:founded-year @team)])
                     (when (:logo-url @team) [:p [:span {:class "font-medium text-slate-800"} "Logo: "] [:a {:href (:logo-url @team) :target "_blank" :class "text-brand-maroon hover:underline"} "Ver logo"]])
                     (when (:notes @team) [:p [:span {:class "font-medium text-slate-800"} "Notas: "] (:notes @team)])]]
                   [common/card
                    [:h3 {:class "app-section-title"} "Jogadores do time"]
                    (if (seq @players)
                      [common/table
                       ["Nome" "Posição" "Ações"]
                       (map (fn [player]
                              [(:name player)
                               (:position player)
                               [:button {:class "rounded-lg border border-rose-200 px-2 py-1 text-xs text-rose-600 hover:bg-rose-50"
                                         :on-click #(remove-player! (player-picker/normalize-id (or (:_id player) (:id player))))}
                                "Remover"]])
                            @players)
                       :sortable? true
                       :dense? true]
                      [:p {:class "app-muted"} "Nenhum jogador no time"])]]

                  [common/card
                   [:div {:class "flex items-center justify-between"}
                    [:h3 {:class "app-section-title"} "Adicionar jogadores"]
                    [:> UserPlus {:size 18 :class "text-slate-400"}]]
                   (if (seq @all-players)
                     (let [team-exclude-ids (into #{} (map #(player-picker/player-id %) @players))]
                       [player-picker/player-search-add-panel
                        {:label nil
                         :players @all-players
                         :exclude-ids team-exclude-ids
                         :action-label "Adicionar"
                         :on-pick-player
                         (fn [player]
                           (when-let [pid (player-picker/player-id player)]
                             (add-player! pid)))
                         :on-quick-create
                         (fn [name ok err]
                           (api/create-player
                            {:name name :position player-picker/quick-create-position}
                            (fn [created]
                              (if-let [pid (player-picker/player-id created)]
                                (do
                                  (swap! all-players conj created)
                                  (api/add-player-to-team
                                   id pid
                                   (fn [_result]
                                     (load!)
                                     (ok created))
                                   (fn [e]
                                     (err (str "Erro ao adicionar novo jogador ao time: " e)))))
                                (err "Jogador criado sem ID retornado.")))
                            (fn [e]
                              (err (str "Erro ao criar jogador: " e)))))}])
                     [:p {:class "app-muted"} "Carregando jogadores..."])]] 
           :else [:p {:class "app-muted"} "Time não encontrado"])])})))
