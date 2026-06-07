(ns galaticos.components.teams
  "Team list, form, and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.components.player-picker :as player-picker]
            [galaticos.effects :as effects]
            [galaticos.delete-undo :as delete-undo]
            [galaticos.ui-copy :as ui-copy]
            ["lucide-react" :refer [Shield UserPlus CalendarDays Users]]))

(def ^:private team-category-options
  [["" "Selecione a categoria"]
   ["adulto" "Adulto"]
   ["sub-20" "Sub-20"]
   ["sub-17" "Sub-17"]
   ["feminino" "Feminino"]
   ["veteranos" "Veteranos"]])

(def ^:private team-category-labels
  (into {} team-category-options))

(defn- normalize-id [v]
  (player-picker/normalize-id v))

(defn- list-query-params [search category]
  (cond-> {}
    (not (str/blank? search)) (assoc :q search)
    (not (str/blank? category)) (assoc :category category)))

(defn- sync-list-route! [search category]
  (rfe/push-state :teams {} (list-query-params search category)))

(defn- filters-active? [search category]
  (or (not (str/blank? search))
      (not (str/blank? category))))

(defn- filter-teams-client [teams {:keys [q category]}]
  (let [q-lc (when-not (str/blank? q) (str/lower-case q))
        cat (str/trim (str category))]
    (cond->> teams
      q-lc (filter (fn [t]
                     (let [n (str/lower-case (str (:name t)))
                           abbr (str/lower-case (str (:abbreviation t)))]
                       (or (str/includes? n q-lc)
                           (str/includes? abbr q-lc)))))
      (not (str/blank? cat)) (filter #(= cat (:category %))))))

(defn- team-player-count [team]
  (count (or (:active-player-ids team) [])))

(defn- match-date-ms [match]
  (when-let [d (:date match)]
    (.getTime (js/Date. d))))

(defn- upcoming-match-for-team [team-id matches]
  (let [tid (str team-id)
        now-ms (.getTime (js/Date.))]
    (first
     (sort-by match-date-ms
              (filter (fn [m]
                        (let [ms (match-date-ms m)]
                          (and ms (>= ms now-ms)
                               (= tid (str (:home-team-id m))))))
                      (or matches []))))))

(defn- team-name-taken? [name teams current-id]
  (let [n (str/trim (str name))
        id (some-> current-id str)]
    (when-not (str/blank? n)
      (some (fn [t]
              (let [tid (normalize-id (or (:_id t) (:id t)))]
                (and (not= id tid)
                     (= (str/lower-case n)
                        (str/lower-case (str (:name t)))))))
            teams))))

(defn- team-card [team matches authenticated?]
  (let [id (normalize-id (or (:_id team) (:id team)))
        players-n (team-player-count team)
        upcoming (upcoming-match-for-team id matches)
        category-label (get team-category-labels (:category team) (:category team))]
    [:div {:class "app-card p-4 transition hover:shadow-md cursor-pointer"
           :on-click #(when id (rfe/push-state :team-detail {:id id}))}
     [:div {:class "flex items-start gap-3"}
      [:div {:class "flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-brand-maroon/10 text-brand-maroon"}
       (if-let [logo (:logo-url team)]
         [:img {:src logo :alt (:name team) :class "h-full w-full object-cover"}]
         [:> Shield {:size 28}])]
      [:div {:class "min-w-0 flex-1"}
       [:p {:class "text-lg font-semibold text-slate-900 dark:text-slate-100 truncate"} (:name team)]
       (when-let [abbr (:abbreviation team)]
         [:p {:class "text-xs font-medium uppercase tracking-wide text-slate-500"} abbr])
       [:div {:class "mt-2 flex flex-wrap items-center gap-2"}
        [:span {:class "inline-flex items-center gap-1 text-sm text-slate-600 dark:text-slate-300"}
         [:> Users {:size 14 :aria-hidden true}]
         [:span {:class "tabular-nums font-medium"} players-n]
         [:span "jogadores"]]
        (when category-label
          [common/badge category-label :variant :info])]
       (when upcoming
         [:p {:class "mt-2 inline-flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400"}
          [:> CalendarDays {:size 12 :aria-hidden true}]
          (str "Próximo: " (common/format-match-calendar-date (:date upcoming))
               (when-let [opp (:opponent upcoming)]
                 (str " vs " opp)))])]]
     (when authenticated?
       [:div {:class "mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3 dark:border-slate-800"}
        [common/button "Ver elenco"
         #(when id (rfe/push-state :team-detail {:id id}))
         :variant :outline
         :class "text-sm"]
        [common/button "Editar"
         (fn [e]
           (.stopPropagation e)
           (when id (rfe/push-state :team-edit {:id id})))
         :variant :ghost
         :class "text-sm"]])]))

(defn team-list []
  (let [search (r/atom "")
        category (r/atom "")
        route-qp-track (r/atom nil)
        apply-query-params!
        (fn [qp]
          (when qp
            (reset! search (str (or (:q qp) "")))
            (reset! category (str (or (:category qp) "")))))
        clear-filters!
        (fn []
          (reset! search "")
          (reset! category "")
          (sync-list-route! "" ""))]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (apply-query-params! (get-in @state/app-state [:route-match :query-params]))
        (reset! route-qp-track (get-in @state/app-state [:route-match :query-params])))
      :component-did-update
      (fn [_]
        (let [qp (get-in @state/app-state [:route-match :query-params])]
          (when-not (= @route-qp-track qp)
            (reset! route-qp-track qp)
            (apply-query-params! qp))))
      :reagent-render
      (fn [_]
        (let [{:keys [authenticated teams teams-loading? matches]} @state/app-state
              filtered (filter-teams-client (or teams []) {:q @search :category @category})
              has-filters? (filters-active? @search @category)]
          [:div {:class "space-y-6"}
           [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
            [:div
             [:p {:class "text-sm text-slate-500"} "Gestão de equipes"]
             [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} "Times"]]
            (when authenticated
              [:div {:class "flex flex-wrap gap-2"}
               [common/button "Novo Time" #(rfe/push-state :team-new) :variant :primary]])]

           [common/card
            [:div {:class "flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-800/50 sm:flex-row sm:flex-wrap sm:items-end"}
             [:div {:class "flex min-w-0 flex-1 flex-col gap-2 sm:max-w-md"}
              [:label {:for "teams-list-search"
                       :class "text-xs font-medium text-slate-500 dark:text-slate-400"}
               "Buscar"]
              [:div {:class "relative"}
               [:input {:type "search"
                        :id "teams-list-search"
                        :value @search
                        :placeholder "Buscar time..."
                        :on-change (fn [e]
                                     (let [v (-> e .-target .-value)]
                                       (reset! search v)
                                       (sync-list-route! @search @category)))
                        :class "w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-brand-maroon focus:outline-none focus:ring-2 focus:ring-brand-maroon/20 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 py-2 pl-3 pr-24"}]
               (when teams-loading?
                 [:span {:class "pointer-events-none absolute inset-y-0 right-2 flex items-center text-xs text-slate-500 dark:text-slate-400"
                         :aria-live "polite"}
                  "A filtrar…"])]]
             [common/select-field "Categoria" @category team-category-options
              (fn [v]
                (reset! category v)
                (sync-list-route! @search @category))
              :container-class "min-w-[180px]"]
             [:div {:class "flex flex-wrap items-center gap-2 sm:ml-auto"}
              [:p {:class "text-sm tabular-nums text-slate-600 dark:text-slate-300"
                   :aria-live "polite"}
               (str (count filtered) " encontrado(s)")]
              [common/button "Limpar" clear-filters!
               :variant :ghost
               :disabled (not has-filters?)
               :class "text-sm"]]]

            (cond
              teams-loading? [common/skeleton-table ["Nome" "Jogadores" "Categoria"] :rows 6]
              (seq filtered)
              [:div {:class "mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"}
               (for [team filtered]
                 ^{:key (str (or (:_id team) (:id team) (:name team)))}
                 [team-card team matches authenticated])]
              has-filters?
              [:div {:class "mt-4 space-y-3"}
               [:p {:class "app-muted px-1"} "Nenhum time encontrado com estes filtros."]
               [common/button "Limpar filtros" clear-filters! :variant :outline]]
              :else
              [:div {:class "mt-4 space-y-4"}
               [:p {:class "text-slate-600 dark:text-slate-300"}
                "A sua organização ainda não tem times cadastrados. O cadastro de um time é o primeiro passo para organizar atletas e registar partidas."]
               (when authenticated
                 [common/button "Criar primeiro time" #(rfe/push-state :team-new) :variant :primary])])]]))})))

(defn team-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        team-loading? (r/atom is-edit?)
        form-data (r/atom {:name ""
                           :abbreviation ""
                           :category ""
                           :city ""
                           :coach ""
                           :stadium ""
                           :founded-year ""
                           :logo-url ""
                           :notes ""})
        submitting? (r/atom false)
        form-error (r/atom nil)
        field-errors (r/atom {})
        conflict (r/atom nil)
        advanced-open? (r/atom false)
        teams-catalog (r/atom [])
        team-field-keys #{:name :abbreviation :category :city :coach :stadium
                          :founded-year :logo-url :notes}
        valid-form? (fn []
                      (let [errs (cond-> {}
                                   (str/blank? (:name @form-data)) (assoc :name "Nome é obrigatório")
                                   (str/blank? (:abbreviation @form-data)) (assoc :abbreviation "Sigla é obrigatória")
                                   (str/blank? (:category @form-data)) (assoc :category "Categoria é obrigatória"))]
                        (when (seq errs) errs)))
        check-name-unique! (fn []
                             (when-not (str/blank? (:name @form-data))
                               (if (team-name-taken? (:name @form-data) @teams-catalog id)
                                 (swap! field-errors assoc :name "Já existe um time com este nome.")
                                 (swap! field-errors dissoc :name))))
        prepare-payload (fn []
                          (let [base {:name (str/trim (:name @form-data))
                                      :abbreviation (str/trim (:abbreviation @form-data))
                                      :category (str/trim (:category @form-data))}
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
                     (api/get-teams
                      (fn [result]
                        (let [normalized (cond
                                           (vector? result) result
                                           (sequential? result) (vec result)
                                           (map? result) [result]
                                           :else [])]
                          (reset! teams-catalog normalized)))
                      (fn [_ _] (reset! teams-catalog [])))
                     (when is-edit?
                       (reset! form-error nil)
                       (reset! team-loading? true)
                       (api/get-team id
                                     (fn [result]
                                       (reset! form-data {:name (or (:name result) "")
                                                          :abbreviation (or (:abbreviation result) "")
                                                          :category (or (:category result) "")
                                                          :city (or (:city result) "")
                                                          :coach (or (:coach result) "")
                                                          :stadium (or (:stadium result) "")
                                                          :founded-year (if (:founded-year result) (str (:founded-year result)) "")
                                                          :logo-url (or (:logo-url result) "")
                                                          :notes (or (:notes result) "")})
                                       (reset! team-loading? false))
                                     (fn [err]
                                       (let [msg (str "Erro ao carregar time: " err)]
                                         (reset! form-error msg)
                                         (state/toast-error! msg))
                                       (reset! team-loading? false)))))]
    (r/create-class
     {:component-did-mount load-team!
      :reagent-render
      (fn []
        [:div {:class "space-y-6"}
         [common/breadcrumb [{:label "Times" :route :teams}
                            {:label (if is-edit? "Editar time" "Novo time")}]]
         [:div
          [:p {:class "text-sm text-slate-500"} "Cadastro"]
          [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} (if is-edit? "Editar Time" "Novo Time")]]
         (if @team-loading?
           [common/loading-spinner]
           [:form {:class "space-y-6"
                   :on-submit (fn [e]
                                (.preventDefault e)
                                (reset! form-error nil)
                                (reset! conflict nil)
                                (if-let [errs (valid-form?)]
                                  (do
                                    (reset! field-errors errs)
                                    (state/toast-field-errors! errs))
                                  (if (team-name-taken? (:name @form-data) @teams-catalog id)
                                    (do
                                      (reset! field-errors {:name "Já existe um time com este nome."})
                                      (state/toast-field-errors! {:name "Já existe um time com este nome."}))
                                    (do
                                      (reset! submitting? true)
                                      (let [payload (prepare-payload)
                                            on-success (fn [_result]
                                                        (reset! submitting? false)
                                                        (effects/ensure-teams! {:force? true})
                                                        (rfe/push-state :teams))
                                            on-error (fn [error resp]
                                                      (reset! submitting? false)
                                                      (common/apply-form-api-error!
                                                       {:message error
                                                        :response resp
                                                        :field-keys team-field-keys
                                                        :field-errors field-errors
                                                        :form-error form-error
                                                        :conflict conflict})
                                                      (when (and (not= 409 (:status resp))
                                                                 (empty? @field-errors))
                                                        (state/toast-error! (or @form-error error))))]
                                        (if is-edit?
                                          (api/update-team id payload on-success on-error)
                                          (api/create-team payload on-success on-error)))))))}
            (when @conflict
              [common/form-conflict-alert (:message @conflict)])
            (when (seq @field-errors)
              [common/form-error-summary @field-errors])
            (when (and @form-error (empty? @field-errors))
              [common/alert @form-error :variant :error])
            [common/card
             [:h3 {:class "app-section-title"} "Dados essenciais"]
             [:p {:class "mt-1 text-xs text-slate-500"} "Nome, sigla e categoria bastam para começar."]
             [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
              [common/input-field "Nome" (:name @form-data) #(swap! form-data assoc :name %)
               :placeholder "Nome do time" :required? true :error (:name @field-errors)
               :on-blur check-name-unique!]
              [common/input-field "Sigla" (:abbreviation @form-data) #(swap! form-data assoc :abbreviation %)
               :placeholder "Ex: GAL" :required? true :error (:abbreviation @field-errors)]
              [common/select-field "Categoria" (:category @form-data) team-category-options
               #(swap! form-data assoc :category %)
               :required? true :error (:category @field-errors) :container-class "md:col-span-2"]]
             [:button {:type "button"
                       :class "mt-4 text-sm font-medium text-brand-maroon hover:underline"
                       :aria-expanded @advanced-open?
                       :on-click #(swap! advanced-open? not)}
              (if @advanced-open? "Ocultar informações avançadas" "Informações avançadas")]
             (when @advanced-open?
               [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
                [common/input-field "Cidade" (:city @form-data) #(swap! form-data assoc :city %)]
                [common/input-field "Treinador" (:coach @form-data) #(swap! form-data assoc :coach %)]
                [common/input-field "Estádio" (:stadium @form-data) #(swap! form-data assoc :stadium %)]
                [common/input-field "Ano Fundação" (:founded-year @form-data) #(swap! form-data assoc :founded-year %) :type "number"]
                [common/input-field "URL do Logo" (:logo-url @form-data) #(swap! form-data assoc :logo-url %) :type "url" :container-class "md:col-span-2"]
                [common/input-field "Notas" (:notes @form-data) #(swap! form-data assoc :notes %) :placeholder "Observações" :container-class "md:col-span-2"]])]
            [:div {:class "flex flex-wrap gap-2"}
             [common/submit-button (if is-edit? "Atualizar" "Criar")
              nil
              :saving? @submitting?]
             [common/button "Cancelar" #(rfe/push-state :teams) :variant :outline]]])])})))

(defn- player-detail-route [player team-id team-name]
  (let [pid (normalize-id (or (:_id player) (:id player)))]
    (when pid
      (if team-id
        (rfe/push-state :player-detail {:id pid}
                        {:team-id team-id :team-name team-name})
        (rfe/push-state :player-detail {:id pid})))))

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
                                (state/set-page-context! {:badge (:name result) :title "Time"})
                                (reset! loading? false)
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
                                                       (let [msg (str "Erro ao carregar jogadores: " err)]
                                                         (reset! error msg)
                                                         (state/toast-error! msg))))
                                    (api/get-players {}
                                                     (fn [all]
                                                       (reset! all-players (api/coerce-player-list all)))
                                                     (fn [err _resp]
                                                       (let [msg (str "Erro ao carregar jogadores: " err)]
                                                         (reset! error msg)
                                                         (state/toast-error! msg)))))))
                              (fn [err resp]
                                (reset! loading? false)
                                (if (and resp (= 404 (:status resp)))
                                  (do (reset! not-found? true)
                                      (reset! error "Time não encontrado."))
                                  (let [msg (str "Erro ao carregar time: " err)]
                                    (reset! not-found? false)
                                    (reset! error msg)
                                    (state/toast-error! msg))))))
        delete-team! (fn []
                       (delete-undo/schedule!
                        {:message ui-copy/team-removed
                         :on-remove #(rfe/push-state :teams)
                         :on-rollback #(rfe/push-state :team-detail {:id id})
                         :on-commit (fn [on-success on-error]
                                      (reset! deleting? true)
                                      (api/delete-team id
                                                       (fn [result]
                                                         (reset! deleting? false)
                                                         (on-success result)
                                                         (effects/ensure-teams! {:force? true}))
                                                       (fn [err]
                                                         (reset! deleting? false)
                                                         (on-error err))))}))
        add-player! (fn [player-id]
                      (api/add-player-to-team id player-id
                                              (fn [_result]
                                                (load!))
                                              (fn [err]
                                                (let [msg (str "Erro ao adicionar jogador: " err)]
                                                  (reset! error msg)
                                                  (state/toast-error! msg)))))
        remove-player! (fn [player-id]
                         (api/remove-player-from-team id player-id
                                                      (fn [_result]
                                                        (load!))
                                                      (fn [err]
                                                        (let [msg (str "Erro ao remover jogador: " err)]
                                                          (reset! error msg)
                                                          (state/toast-error! msg)))))]
    (r/create-class
     {:component-did-mount load!
      :component-will-unmount #(state/clear-page-context!)
      :reagent-render
      (fn []
        [:div {:class "space-y-6"}
         (cond
           @error (if @not-found?
                    [common/not-found-resource @error #(rfe/push-state :teams)]
                    [:div
                     [common/button "Tentar novamente" load! :variant :outline]])
           @loading? [common/loading-spinner]
           @team (let [{:keys [authenticated matches]} @state/app-state
                       team-name (:name @team)
                       category-label (get team-category-labels (:category @team) nil)
                       upcoming (upcoming-match-for-team id matches)]
                   [:<>
                    [common/breadcrumb [{:label "Times" :route :teams}
                                       {:label team-name}]]
                    [:div {:class "flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"}
                     [:div {:class "flex items-center gap-3"}
                      [:div {:class "flex h-14 w-14 items-center justify-center overflow-hidden rounded-xl bg-brand-maroon/10 text-brand-maroon"}
                       (if-let [logo (:logo-url @team)]
                         [:img {:src logo :alt team-name :class "h-full w-full object-cover"}]
                         [:> Shield {:size 24}])]
                      [:div
                       [:p {:class "text-sm text-slate-500"} "Time"]
                       [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} team-name]
                       (when (:abbreviation @team)
                         [:p {:class "text-xs uppercase tracking-wide text-slate-500"} (:abbreviation @team)])]]
                     (when authenticated
                       [:div {:class "flex flex-wrap gap-2"}
                        [common/button "Editar" #(rfe/push-state :team-edit {:id id}) :variant :outline]
                        [common/button "Deletar" delete-team!
                         :disabled @deleting?
                         :variant :danger]])]

                    [:div {:class "grid gap-6 lg:grid-cols-[minmax(240px,280px)_1fr]"}
                     [common/card
                      [:h3 {:class "app-section-title"} "Informações"]
                      [:div {:class "mt-3 space-y-2 text-sm text-slate-600 dark:text-slate-300"}
                       (when category-label
                         [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Categoria: "] category-label])
                       [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Jogadores: "]
                        [:span {:class "tabular-nums"} (team-player-count @team)]]
                       (when (:city @team) [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Cidade: "] (:city @team)])
                       (when (:coach @team) [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Treinador: "] (:coach @team)])
                       (when (:stadium @team) [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Estádio: "] (:stadium @team)])
                       (when (:founded-year @team) [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Ano fundação: "] (:founded-year @team)])
                       (when upcoming
                         [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Próximo jogo: "]
                          (str (common/format-match-calendar-date (:date upcoming))
                               (when-let [opp (:opponent upcoming)]
                                 (str " vs " opp)))])
                       (when (:logo-url @team)
                         [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Logo: "]
                          [:a {:href (:logo-url @team) :target "_blank" :class "text-brand-maroon hover:underline"} "Ver logo"]])
                       (when (:notes @team)
                         [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Notas: "] (:notes @team)])]]

                     [:div {:class "space-y-4"}
                      [common/card
                       [:h3 {:class "app-section-title"} "Elenco do time"]
                       (if (seq @players)
                         [common/table
                          ["Nome" "Posição" "Partidas" "Ações"]
                          (map (fn [player]
                                 (let [pid (normalize-id (or (:_id player) (:id player)))]
                                   [[:button {:type "button"
                                              :class "text-left font-medium text-brand-maroon hover:underline"
                                              :on-click #(player-detail-route player id team-name)}
                                     (:name player)]
                                    (:position player)
                                    [:span {:class "tabular-nums"}
                                     (get-in player [:aggregated-stats :total :games] 0)]
                                    (when authenticated
                                      [:button {:class "rounded-lg border border-rose-200 px-2 py-1 text-xs text-rose-600 hover:bg-rose-50 dark:border-rose-800 dark:hover:bg-rose-950/40"
                                                :on-click #(remove-player! pid)}
                                       "Remover"])]))
                               @players)
                          :sortable? true
                          :dense? true
                          :numeric-columns #{2}]
                         [:p {:class "app-muted"} "Nenhum jogador no time. Adicione atletas abaixo."])]

                      (when authenticated
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
                           [:p {:class "app-muted"} "Carregando jogadores..."])])]]])
           :else [:p {:class "app-muted"} "Time não encontrado"])])})))
