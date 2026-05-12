(ns galaticos.components.matches
  "Match list and form components"
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]
            ["lucide-react" :refer [CalendarPlus Pencil Trash2 Trophy]]))

(defn- match-row
  [match delete-match! authenticated?]
  (let [match-id (:_id match)]
    [:div {:class "flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900/90 dark:hover:bg-slate-800 lg:flex-row lg:items-center lg:justify-between"
           :role "button"
           :tab-index 0
           :on-click #(rfe/push-state :match-detail {:id match-id})
           :on-key-down (fn [e]
                          (when (= "Enter" (.-key e))
                            (rfe/push-state :match-detail {:id match-id})))}
     [:div {:class "flex items-center gap-3"}
      [:div {:class "rounded-xl bg-brand-maroon/10 p-2 text-brand-maroon"}
       [:> CalendarPlus {:size 18}]]
      [:div
       [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} (:opponent match)]
       [:p {:class "text-xs text-slate-500 dark:text-slate-400"} (str (or (common/format-match-calendar-date (:date match)) "-") " • " (or (:venue match) "-"))]]]
     [:div {:class "flex items-center gap-3"}
      [common/badge (common/format-match-result (:result match)) :variant :info]
      (when authenticated?
        [:<>
         [:button {:class "rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-100 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
                   :on-click (fn [e]
                               (.stopPropagation e)
                               (rfe/push-state :match-edit {:id match-id}))}
          [:> Pencil {:size 16}]]
         [:button {:class "rounded-lg border border-slate-200 p-2 text-rose-600 hover:bg-rose-50 dark:border-slate-600 dark:hover:bg-rose-950/50"
                   :on-click (fn [e]
                               (.stopPropagation e)
                               (delete-match! match-id))}
          [:> Trash2 {:size 16}]]])]]))

(defn match-detail
  "Read-only match detail page (BRM-10)."
  [params]
  (let [id (:id params)
        match (r/atom nil)
        loading? (r/atom true)
        error (r/atom nil)
        load! (fn []
                (reset! error nil)
                (reset! loading? true)
                (api/get-match
                 id
                 (fn [result]
                   (reset! match result)
                   (reset! loading? false))
                 (fn [err]
                   (let [msg (str "Erro ao carregar partida: " err)]
                     (reset! error msg)
                     (state/toast-error! msg))
                   (reset! loading? false))))]
    (r/create-class
     {:component-did-mount (fn []
                             (effects/ensure-players!)
                             (effects/ensure-teams!)
                             (load!))
      :reagent-render
      (fn []
        (let [{:keys [authenticated championships players teams]} @state/app-state
              ch-id (some-> @match :championship-id str)
              ch-name (some->> championships (filter #(= (str (:_id %)) ch-id)) first :name)
              stats (or (:player-statistics @match) [])
              to-int (fn [v]
                       (let [parsed (js/parseInt (or (some-> v str) "0") 10)]
                         (if (js/isNaN parsed) 0 parsed)))
              total-goals (reduce + 0 (map #(to-int (:goals %)) stats))
              total-assists (reduce + 0 (map #(to-int (:assists %)) stats))
              player-name-by (into {} (map (fn [p] [(str (:_id p)) (:name p)]) (or players [])))
              team-name-by (into {} (map (fn [t] [(str (:_id t)) (:name t)]) (or teams [])))]
          [:div {:class "space-y-6"}
           [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"}
            [:div
             [:p {:class "text-sm text-slate-500"} "Partida"]
             [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} (or (:opponent @match) "Detalhe da partida")]]
            [:div {:class "flex flex-wrap gap-2"}
             [common/button "Voltar" #(rfe/push-state :matches) :variant :outline]
             (when authenticated
               [common/button "Editar" #(rfe/push-state :match-edit {:id id}) :variant :primary])]]

           (cond
             @error [common/button "Tentar novamente" load! :variant :outline]
             @loading? [common/loading-spinner]
             @match
             [:div {:class "grid gap-4 md:grid-cols-2"}
              [common/card
               [:h3 {:class "app-section-title"} "Resumo"]
               [:div {:class "mt-3 space-y-2 text-sm text-slate-600"}
                (when ch-name
                  [:p [:span {:class "font-medium text-slate-800"} "Campeonato: "] ch-name])
                [:p [:span {:class "font-medium text-slate-800"} "Data: "]
                 (or (common/format-match-calendar-date (:date @match)) "-")]
                [:p [:span {:class "font-medium text-slate-800"} "Local: "] (or (:venue @match) "-")]
                [:p [:span {:class "font-medium text-slate-800"} "Resultado: "]
                 (common/format-match-result (:result @match))]
                [:p [:span {:class "font-medium text-slate-800"} "Gols (jogadores): "] total-goals]
                [:p [:span {:class "font-medium text-slate-800"} "Assistências: "] total-assists]]]

              [common/card
               [:h3 {:class "app-section-title"} "Estatísticas dos jogadores"]
               (if (seq stats)
                 [common/table
                  ["Jogador" "Time" "Gols" "Assists" "Min"]
                  (map (fn [row]
                         (let [pid (some-> (:player-id row) str)
                               tid (some-> (:team-id row) str)]
                           [(or (:player-name row)
                                (get player-name-by pid)
                                (when (seq pid) "Jogador desconhecido")
                                "—")
                            (or (:team-name row)
                                (get team-name-by tid)
                                (when (seq tid) "Time desconhecido")
                                "—")
                            (to-int (:goals row))
                            (to-int (:assists row))
                            (to-int (:minutes-played row))]))
                       stats)
                  :dense? true
                  :show-search? false]
                 [:p {:class "app-muted"} "Sem estatísticas registradas."])]] 
             :else
             [:p {:class "app-muted"} "Partida não encontrada."])]))})))

(defn match-list []
  (let [search (r/atom "")]
    (fn []
      (let [{:keys [championships championships-loading? matches]} @state/app-state
            sorted-champs (sort-by (fn [ch] (str/lower-case (str (:name ch)))) championships)
            matches-by-champ (group-by (fn [m] (str (or (:championship-id m) ""))) matches)
            filtered-champs (if (str/blank? @search)
                              sorted-champs
                              (filter (fn [ch]
                                        (str/includes? (str/lower-case (str (:name ch)))
                                                       (str/lower-case @search)))
                                      sorted-champs))]
        [:div {:class "space-y-6"}
         [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
          [:div
           [:p {:class "text-sm text-slate-500"} "Calendário"]
           [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} "Partidas"]]]

         [common/card
          [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
           [:input {:type "text"
                    :value @search
                    :placeholder "Buscar campeonato..."
                    :on-change #(reset! search (-> % .-target .-value))
                    :class "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm focus:border-brand-maroon focus:outline-none focus:ring-2 focus:ring-brand-maroon/20 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 sm:w-64"}]
           [:div {:class "text-xs text-slate-500 dark:text-slate-400"} (str (count filtered-champs) " campeonato(s)")]]

          (cond
            championships-loading? [common/loading-spinner]
            (empty? filtered-champs)
            [:p {:class "app-muted mt-4"} "Nenhum campeonato encontrado."]
            :else
            [:div {:class "mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"}
             (for [ch filtered-champs]
               (let [cid (str (:_id ch))
                     match-count (count (get matches-by-champ cid []))]
                 ^{:key cid}
                 [:div {:class "cursor-pointer rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-brand-maroon hover:shadow-md dark:border-slate-700 dark:bg-slate-900/90 dark:hover:border-brand-maroon"
                        :role "button"
                        :tab-index 0
                        :on-click #(rfe/push-state :matches-by-championship {:championship-id cid})
                        :on-key-down (fn [e]
                                       (when (= "Enter" (.-key e))
                                         (rfe/push-state :matches-by-championship {:championship-id cid})))}
                  [:div {:class "flex items-center gap-3"}
                   [:div {:class "rounded-xl bg-brand-maroon/10 p-2 text-brand-maroon"}
                    [:> Trophy {:size 20}]]
                   [:div {:class "min-w-0 flex-1"}
                    [:h3 {:class "truncate text-base font-semibold text-slate-900 dark:text-slate-100"} (:name ch)]
                    [:p {:class "text-xs text-slate-500 dark:text-slate-400"}
                     (str match-count " partida(s)")]
                    (when-let [status (:status ch)]
                      [:div {:class "mt-1"}
                       [common/badge (common/status-label status) :variant (common/status-variant status)]])]]]))])]]))))

(defn match-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        preset-championship-id (str (or (:championship-id params) ""))
        match-loading? (r/atom is-edit?)
        enrolled-players (r/atom [])
        players-loading? (r/atom false)
        existing-stats (r/atom nil)
        form-data (r/atom {:championship-id (if is-edit? "" preset-championship-id)
                           :home-team-id ""
                           :date ""
                           :opponent ""
                           :away-score 0
                           :venue ""
                           :player-statistics {}})
        submitting? (r/atom false)
        form-error (r/atom nil)
        field-errors (r/atom {})
        parse-int (fn [v]
                    (let [trimmed (str/trim (or v ""))]
                      (when (not-empty trimmed)
                        (js/parseInt trimmed 10))))
        init-player-stats (fn [players existing]
                            (let [existing-by-id (into {}
                                                       (map (fn [stat]
                                                              (let [pid (str (:player-id stat))
                                                                    goals (or (parse-int (:goals stat)) 0)
                                                                    assists (or (parse-int (:assists stat)) 0)
                                                                    minutes (or (parse-int (:minutes-played stat)) 0)]
                                                                [pid {:goals goals
                                                                      :assists assists
                                                                      :played? (> minutes 0)}]))
                                                            (or existing [])))]
                              (into {}
                                    (map (fn [player]
                                           (let [pid (str (:_id player))
                                                 default {:goals 0 :assists 0 :played? false}]
                                             [pid (get existing-by-id pid default)]))
                                         players))))
        valid-form? (fn []
                      (let [errs (cond-> {}
                                   (str/blank? (:date @form-data)) (assoc :date "Informe a data"))]
                        (when (seq errs) errs)))
        prepare-payload (fn []
                          (let [home-team-id (:home-team-id @form-data)
                                stats-map (:player-statistics @form-data)
                                stats-vec (->> stats-map
                                               (filter (fn [[_pid stat]]
                                                         (or (:played? stat)
                                                             (> (:goals stat) 0)
                                                             (> (:assists stat) 0))))
                                               (mapv (fn [[pid stat]]
                                                       {:player-id pid
                                                        :team-id home-team-id
                                                        :goals (:goals stat)
                                                        :assists (:assists stat)
                                                        :minutes-played (if (:played? stat) 90 0)})))]
                            (-> @form-data
                                (assoc :player-statistics stats-vec))))
        load-enrolled! (fn [championship-id & {:keys [existing-stats-data]}]
                         (if (str/blank? championship-id)
                           (do
                             (reset! enrolled-players [])
                             (swap! form-data assoc :player-statistics {}))
                           (do
                             (reset! players-loading? true)
                             (api/get-championship-players
                              championship-id
                              (fn [result]
                                (reset! enrolled-players result)
                                (let [stats (init-player-stats result (or existing-stats-data @existing-stats))]
                                  (swap! form-data assoc :player-statistics stats))
                                (reset! players-loading? false))
                              (fn [err]
                                (let [msg (str "Erro ao carregar jogadores inscritos: " err)]
                                  (reset! form-error msg)
                                  (state/toast-error! msg))
                                (reset! players-loading? false))))))
        load-match! (fn []
                      (when is-edit?
                        (reset! form-error nil)
                        (reset! match-loading? true)
                        (api/get-match id
                                       (fn [result]
                                         (let [match-stats (:player-statistics result)]
                                           (reset! existing-stats match-stats)
                                           (reset! form-data {:championship-id (if (:championship-id result) (str (:championship-id result)) "")
                                                              :home-team-id (if (:home-team-id result) (str (:home-team-id result)) "")
                                                              :date (or (:date result) "")
                                                              :opponent (or (:opponent result) "")
                                                              :away-score (if (some? (:away-score result)) (js/parseInt (str (:away-score result)) 10) 0)
                                                              :venue (or (:venue result) "")
                                                              :player-statistics {}})
                                           (load-enrolled! (if (:championship-id result) (str (:championship-id result)) "")
                                                           :existing-stats-data match-stats))
                                         (reset! match-loading? false))
                                       (fn [err]
                                         (let [msg (str "Erro ao carregar partida: " err)]
                                           (reset! form-error msg)
                                           (state/toast-error! msg))
                                         (reset! match-loading? false)))))]
    (r/create-class
     {:component-did-mount (fn []
                             (load-match!)
                             (when (and (not is-edit?)
                                        (not (str/blank? preset-championship-id)))
                               (load-enrolled! preset-championship-id)))
      :reagent-render
      (fn []
        (let [championships (:championships @state/app-state)
              teams (:teams @state/app-state)
              active-championship (first (filter #(common/status-active? (:status %)) championships))
              active-champ-id (when active-championship (str (:_id active-championship)))
              active-champ-name (when active-championship (:name active-championship))
              _ (when (and active-champ-id (str/blank? (:championship-id @form-data)))
                  (swap! form-data assoc :championship-id active-champ-id)
                  (load-enrolled! active-champ-id))
              galaticos-team (first (filter #(= "Galáticos" (:name %)) teams))
              galaticos-id (when galaticos-team (str (:_id galaticos-team)))
              _ (when (and galaticos-id (str/blank? (:home-team-id @form-data)))
                  (swap! form-data assoc :home-team-id galaticos-id))
              stats-map (:player-statistics @form-data)
              total-goals (reduce + 0 (map :goals (vals stats-map)))
              total-assists (reduce + 0 (map :assists (vals stats-map)))
              home-score total-goals
              away-score (let [v (:away-score @form-data)
                               parsed (js/parseInt (or (str v) "0") 10)]
                           (if (js/isNaN parsed) 0 parsed))
              sorted-players (sort-by #(str/lower-case (or (:name %) "")) @enrolled-players)]
          [:div {:class "space-y-6"}
           [:div
            [:p {:class "text-sm text-slate-500"} "Cadastro"]
            [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} (if is-edit? "Editar Partida" "Nova Partida")]]
           (if @match-loading?
             [common/loading-spinner]
             [:form {:class "space-y-6"
                     :on-submit (fn [e]
                                  (.preventDefault e)
                                  (reset! form-error nil)
                                  (reset! field-errors {})
                                  (if-let [errs (valid-form?)]
                                    (do
                                      (reset! field-errors errs)
                                      (state/toast-field-errors! errs))
                                    (do
                                      (reset! submitting? true)
                                      (let [payload (prepare-payload)
                                            on-success (fn [_result]
                                                         (reset! submitting? false)
                                                         (effects/ensure-matches! {:force? true})
                                                         (effects/ensure-players! {:force? true})
                                                         (effects/ensure-dashboard! {:force? true})
                                                         (rfe/push-state :matches))
                                            on-error (fn [error]
                                                       (reset! submitting? false)
                                                       (let [msg (str "Erro ao " (if is-edit? "atualizar" "criar") " partida: " error)]
                                                         (reset! form-error msg)
                                                         (state/toast-error! msg)))]
                                        (if is-edit?
                                          (api/update-match id payload on-success on-error)
                                          (api/create-match payload on-success on-error))))))}
              [common/card
               [:h3 {:class "app-section-title"} "Detalhes da Partida"]
               [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
                [:div {:class "space-y-2"}
                 [:label {:class "text-sm font-medium text-slate-700 dark:text-slate-200"} "Campeonato"]
                 [:p {:class "w-full rounded-lg border border-slate-200 bg-slate-100 px-3 py-2 text-sm text-slate-900 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"}
                  (or active-champ-name "Nenhum campeonato ativo")]]
                [:div {:class "space-y-2"}
                 [:label {:class "text-sm font-medium text-slate-700 dark:text-slate-200"} "Time"]
                 [:p {:class "w-full rounded-lg border border-slate-200 bg-slate-100 px-3 py-2 text-sm text-slate-900 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"}
                  "Galáticos"]]
                [common/input-field "Data" (:date @form-data) #(swap! form-data assoc :date %) :type "date" :required? true :error (:date @field-errors)]
                [common/input-field "Adversário" (:opponent @form-data) #(swap! form-data assoc :opponent %)]
                [:div {:class "md:col-span-2"}
                 [common/input-field "Local" (:venue @form-data) #(swap! form-data assoc :venue %)]]]
               [:div {:class "mt-6 flex flex-col items-center gap-4"}
                [:div {:class "text-center space-y-2"}
                 [:label {:class "text-sm font-medium text-slate-700 dark:text-slate-200"} "Gols do Adversário"]
                 [:div {:class "flex justify-center"}
                  [common/number-stepper away-score #(swap! form-data assoc :away-score %) :min-val 0]]]
                [:div {:class "text-center space-y-2"}
                 [:label {:class "text-sm font-medium text-slate-700 dark:text-slate-200"} "Resultado Final"]
                 [:p {:class "text-2xl font-bold text-slate-900 dark:text-slate-100"}
                  (str home-score " x " away-score)]]]]

              [common/card
               [:div {:class "flex flex-wrap items-center justify-between gap-3"}
                [:h3 {:class "app-section-title"} "Estatísticas dos jogadores"]
                [:div {:class "text-xs text-slate-500"}
                 (str "Gols: " total-goals " • Assistências: " total-assists " • Placar: " home-score " x " away-score)]]
               (cond
                 (str/blank? (:championship-id @form-data))
                 [:p {:class "mt-3 text-sm text-amber-600 dark:text-amber-400"} "Nenhum campeonato ativo encontrado."]

                 @players-loading?
                 [:p {:class "mt-3 text-xs text-slate-500"} "Carregando jogadores inscritos..."]

                 (empty? sorted-players)
                 [:p {:class "mt-3 text-sm text-amber-600 dark:text-amber-400"} "Nenhum jogador inscrito neste campeonato."]

                 :else
                [:div {:class "mt-4 overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900"}
                 [:table {:class "min-w-full divide-y divide-slate-200 dark:divide-slate-700"}
                  [:thead {:class "bg-slate-50 dark:bg-slate-800/80"}
                   [:tr
                    [:th {:class "px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"} "Nome"]
                    [:th {:class "px-4 py-3 text-center text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"} "Gols"]
                    [:th {:class "px-4 py-3 text-center text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"} "Assistências"]
                    [:th {:class "px-4 py-3 text-center text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"} "Participação"]]]
                  [:tbody {:class "divide-y divide-slate-100 dark:divide-slate-800"}
                   (doall
                    (for [player sorted-players]
                      (let [pid (str (:_id player))
                            stat (get stats-map pid {:goals 0 :assists 0 :played? false})]
                        ^{:key pid}
                        [:tr {:class "hover:bg-slate-50 dark:hover:bg-slate-800/80"}
                         [:td {:class "px-4 py-3 text-sm font-medium text-slate-900 dark:text-slate-100"}
                          (:name player)]
                         [:td {:class "px-4 py-3 text-center"}
                          [common/number-stepper
                           (:goals stat)
                           #(swap! form-data assoc-in [:player-statistics pid :goals] %)
                           :min-val 0]]
                         [:td {:class "px-4 py-3 text-center"}
                          [common/number-stepper
                           (:assists stat)
                           #(swap! form-data assoc-in [:player-statistics pid :assists] %)
                           :min-val 0]]
                         [:td {:class "px-4 py-3 text-center"}
                          [common/checkbox-field
                           (:played? stat)
                           #(swap! form-data assoc-in [:player-statistics pid :played?] %)]]])))]]])]

              [:div {:class "flex flex-wrap gap-2"}
               [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar Partida"))
                nil
                :type "submit"
                :disabled @submitting?
                :variant :primary]
               [common/button "Cancelar" #(rfe/push-state :matches) :variant :outline]]])]))})))

(defn- season-matches-section
  "Collapsible section showing matches for a specific season."
  [season matches-atom delete-match! authenticated? championship-id]
  (let [expanded? (r/atom true)
        season-id (str (:_id season))
        season-label (or (:season season) "Temporada")]
    (fn [_season _matches-atom _delete-match! _authenticated? _championship-id]
      (let [season-matches (filter #(= (str (:season-id %)) season-id) @matches-atom)
            sorted-matches (reverse (sort-by (fn [m] (or (:date m) "")) season-matches))]
        [:div {:class "rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900/90"}
         [:div {:class "flex cursor-pointer items-center justify-between p-4"
                :on-click #(swap! expanded? not)}
          [:div {:class "flex items-center gap-3"}
           [:div {:class "rounded-lg bg-slate-100 p-2 text-slate-600 dark:bg-slate-800 dark:text-slate-300"}
            [:> CalendarPlus {:size 18}]]
           [:div
            [:h4 {:class "font-semibold text-slate-900 dark:text-slate-100"} season-label]
            [:p {:class "text-xs text-slate-500"} (str (count sorted-matches) " partida(s)")]
            (when-let [status (:status season)]
              [common/badge (common/status-label status) :variant (common/status-variant status)])]]
          [:div {:class "flex items-center gap-2"}
           (when authenticated?
             [common/button "Nova Partida"
              (fn [e]
                (.stopPropagation e)
                (rfe/push-state :match-new-in-championship {:championship-id championship-id}))
              :variant :outline])
           [:span {:class (str "text-slate-400 transition-transform duration-200 "
                               (if @expanded? "rotate-180" ""))}
            "▼"]]]
         (when @expanded?
           [:div {:class "border-t border-slate-100 p-4 dark:border-slate-800"}
            (if (seq sorted-matches)
              [:div {:class "space-y-3"}
               (for [m sorted-matches]
                 ^{:key (:_id m)}
                 [match-row m delete-match! authenticated?])]
              [:p {:class "app-muted"} "Nenhuma partida nesta temporada."])])]))))

(defn championship-matches-page
  "Page showing all matches for a championship, organized by season."
  [params]
  (let [championship-id (:championship-id params)
        championship (r/atom nil)
        seasons (r/atom [])
        matches (r/atom [])
        loading? (r/atom true)
        error (r/atom nil)
        load! (fn []
                (reset! error nil)
                (reset! loading? true)
                (reset! championship nil)
                (reset! seasons [])
                (reset! matches [])
                (api/get-championship
                 championship-id
                 (fn [ch]
                   (reset! championship ch)
                   (api/get-championship-seasons
                    championship-id
                    (fn [ss]
                      (reset! seasons ss)
                      (api/get-matches
                       {:championship-id championship-id}
                       (fn [ms]
                         (reset! matches ms)
                         (reset! loading? false))
                       (fn [err]
                         (reset! loading? false)
                         (let [msg (str "Erro ao carregar partidas: " err)]
                           (reset! error msg)
                           (state/toast-error! msg)))))
                    (fn [err]
                      (reset! loading? false)
                      (let [msg (str "Erro ao carregar temporadas: " err)]
                        (reset! error msg)
                        (state/toast-error! msg)))))
                 (fn [err resp]
                   (reset! loading? false)
                   (if (and resp (= 404 (:status resp)))
                     (reset! error "Campeonato não encontrado.")
                     (let [msg (str "Erro ao carregar campeonato: " err)]
                       (reset! error msg)
                       (state/toast-error! msg))))))]
    (r/create-class
     {:component-did-mount (fn [] (load!))
      :reagent-render
      (fn []
        (let [{:keys [authenticated]} @state/app-state
              delete-match! (fn [match-id]
                              (when (js/confirm "Tem certeza que deseja deletar esta partida?")
                                (api/delete-match
                                 match-id
                                 (fn [_]
                                   (swap! matches (fn [ms] (filterv #(not= (:_id %) match-id) ms))))
                                 (fn [err]
                                   (state/toast-error! (str "Erro ao deletar partida: " err))))))
              sorted-seasons (sort-by (fn [s] (str (:season s))) @seasons)
              matches-without-season (filter #(nil? (:season-id %)) @matches)]
          [:div {:class "space-y-6"}
           [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
            [:div {:class "flex items-center gap-3"}
             [common/button "Voltar" #(rfe/push-state :matches) :variant :outline]
             [:div
              [:p {:class "text-sm text-slate-500"} "Partidas do campeonato"]
              [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"}
               (or (:name @championship) "Carregando...")]]]
            (when authenticated
              [:div {:class "flex flex-wrap gap-2"}
               [common/button "Nova Partida"
                #(rfe/push-state :match-new-in-championship {:championship-id championship-id})
                :variant :primary]])]

           (cond
             @error
             [common/card
              [:p {:class "text-rose-600"} @error]
              [common/button "Tentar novamente" load! :variant :outline :class "mt-3"]]

             @loading?
             [common/loading-spinner]

             :else
             [:div {:class "space-y-4"}
              (if (seq sorted-seasons)
                (for [season sorted-seasons]
                  ^{:key (:_id season)}
                  [season-matches-section season matches delete-match! authenticated championship-id])
                [common/card
                 [:p {:class "app-muted"} "Nenhuma temporada cadastrada para este campeonato."]])

              (when (seq matches-without-season)
                [common/card
                 [:h4 {:class "font-semibold text-slate-900 dark:text-slate-100"} "Partidas sem temporada"]
                 [:p {:class "text-xs text-slate-500 mb-3"} (str (count matches-without-season) " partida(s)")]
                 [:div {:class "space-y-3"}
                  (for [m (reverse (sort-by :date matches-without-season))]
                    ^{:key (:_id m)}
                    [match-row m delete-match! authenticated])]])])]))})))
