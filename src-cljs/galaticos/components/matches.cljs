(ns galaticos.components.matches
  "Match list and form components.
  
  ## Regras de UI para Temporadas (RN-MATCH-08/09)
  
  | Variável | Uso |
  |----------|-----|
  | has-active-season? | Esconde botão 'Nova Partida' quando false |
  | create-locked? | Desabilita submit do form de criação; NÃO afeta edição |
  | is-edit? | Distingue edição (sempre permitida) de criação (requer temporada ativa) |
  
  IMPORTANTE: Update/delete de partidas existentes são permitidos mesmo com
  temporada concluída (RN-MATCH-09). Apenas CREATE requer temporada ativa.
  
  Ver: docs/reference/domain/matches-seasons-hybrid-stats.md"
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.match-draft :as match-draft]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]
            [galaticos.delete-undo :as delete-undo]
            [galaticos.ui-copy :as ui-copy]
            ["lucide-react" :refer [CalendarPlus Circle AlertOctagon Pencil Trash2 Trophy]]))

(defn- navigate-match-hub!
  "Return to stored route (e.g. championship detail), else championship hub, else matches list."
  [champ-id]
  (if-let [{:keys [route params]} (effects/consume-match-return!)]
    (rfe/push-state route params)
    (if (str/blank? (str champ-id))
      (rfe/push-state :matches)
      (rfe/push-state :matches-by-championship {:championship-id (str champ-id)}))))

(defn- match-breadcrumb-items
  [champ-id champ-name current-label]
  (cond-> [{:label "Campeonatos" :route :championships}
           {:label "Partidas" :route :matches}]
    (not (str/blank? (str champ-id)))
    (conj {:label (or champ-name "Campeonato")
           :route :matches-by-championship
           :route-params {:championship-id (str champ-id)}})
    true
    (conj {:label current-label})))

(defn- match-row
  [match delete-match! authenticated?]
  (let [match-id (:_id match)
        go-detail! #(rfe/push-state :match-detail {:id match-id})
        row-body
        [:div {:class "flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900/90 dark:hover:bg-slate-800 lg:flex-row lg:items-center lg:justify-between"}
         [:div {:class "flex min-w-0 flex-1 cursor-pointer items-center gap-3"
                :role "link"
                :tab-index 0
                :on-click go-detail!
                :on-key-down (fn [e]
                               (when (= "Enter" (.-key e))
                                 (go-detail!)))}
          [:div {:class "rounded-xl bg-brand-maroon/10 p-2 text-brand-maroon"}
           [:> CalendarPlus {:size 18 :aria-hidden true}]]
          [:div {:class "min-w-0"}
           [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} (:opponent match)]
           [:p {:class "text-xs text-slate-500 dark:text-slate-400"} (str (or (common/format-match-calendar-date (:date match)) "-") " • " (or (:venue match) "-"))]]]
         [:div {:class "flex shrink-0 items-center gap-3"
                :on-click #(.stopPropagation %)
                :on-mouse-down #(.stopPropagation %)}
          [common/badge (common/format-match-result match) :variant :info]
          (when authenticated?
            [:<>
             [:button {:type "button"
                       :class "rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-100 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
                       :aria-label "Editar partida"
                       :on-click #(rfe/push-state :match-edit {:id match-id})}
              [:> Pencil {:size 16 :aria-hidden true}]]
             [:button {:type "button"
                       :class "rounded-lg border border-slate-200 p-2 text-rose-600 hover:bg-rose-50 dark:border-slate-600 dark:hover:bg-rose-950/50"
                       :aria-label "Remover partida"
                       :on-click #(delete-match! match)}
              [:> Trash2 {:size 16 :aria-hidden true}]]])]]
        hover-preview
        [:div {:class "space-y-1 text-slate-600 dark:text-slate-300"}
         [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Placar: "]
          (common/format-match-result match)]
         [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Local: "]
          (or (:venue match) "—")]
         [:p [:span {:class "font-medium text-slate-800 dark:text-slate-200"} "Data: "]
          (or (common/format-match-calendar-date (:date match)) "—")]]]
    [common/list-hover-card row-body hover-preview]))

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
      :component-will-unmount #(state/clear-page-context!)
      :component-did-update
      (fn [_ _]
        (when @match
          (let [ch-id (some-> @match :championship-id str)
                ch-name (some->> (:championships @state/app-state)
                                  (filter #(= (str (:_id %)) ch-id))
                                  first
                                  :name)]
            (when ch-name
              (state/set-page-context! {:badge ch-name :title (or (:opponent @match) "Partida")})))))
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
           [common/breadcrumb
            (match-breadcrumb-items ch-id ch-name (or (:opponent @match) "Partida"))]
           [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"}
            [:div
             (when ch-name
               [common/badge ch-name :variant :info :class "mb-2"])
             [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} (or (:opponent @match) "Detalhe da partida")]]
            [:div {:class "flex flex-wrap gap-2"}
             [common/button "Voltar" #(navigate-match-hub! ch-id) :variant :outline]
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
                 (common/format-match-result @match)]
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
                  :show-search? false
                  :numeric-columns #{2 3 4}]
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

(def ^:private stat-grid-cols 6)

(defn- default-player-stat []
  {:goals 0 :assists 0 :yellow-cards 0 :red-cards 0 :minutes-played 0 :played? false})

(defn- mark-stat-dirty! [dirty-cells pid field]
  (swap! dirty-cells conj (str pid ":" (name field))))

(defn- dirty-cell? [dirty-cells pid field]
  (contains? @dirty-cells (str pid ":" (name field))))

(defn- dirty-cell-class [dirty-cells pid field]
  (when (dirty-cell? dirty-cells pid field)
    "bg-amber-50 dark:bg-amber-950/30"))

(defn- card-highlight-class [stat field]
  (case field
    :yellow-cards (when (pos? (or (:yellow-cards stat) 0))
                    "ring-1 ring-amber-300/80 dark:ring-amber-700/60")
    :red-cards (when (pos? (or (:red-cards stat) 0))
                "ring-1 ring-rose-400/80 dark:ring-rose-700/60")
    nil))

(defn- focus-grid-cell! [row-idx col-idx]
  (when-let [el (.querySelector js/document
                                (str "[data-grid-row='" row-idx "'][data-grid-col='" col-idx
                                     "'] button:not([disabled])"))]
    (.focus el)))

(defn- handle-grid-key! [e row-idx col-idx row-count]
  (let [key (.-key e)
        max-col (dec stat-grid-cols)
        max-row (dec row-count)]
    (when (#{"ArrowRight" "ArrowLeft" "ArrowDown" "ArrowUp"} key)
      (.preventDefault e)
      (let [[nr nc]
            (case key
              "ArrowRight" [row-idx (if (< col-idx max-col) (inc col-idx) col-idx)]
              "ArrowLeft" [row-idx (if (pos? col-idx) (dec col-idx) col-idx)]
              "ArrowDown" [(if (< row-idx max-row) (inc row-idx) row-idx) col-idx]
              "ArrowUp" [(if (pos? row-idx) (dec row-idx) row-idx) col-idx])]
        (focus-grid-cell! nr nc)))))

(defn- form-has-local-changes? [dirty-cells form-data]
  (or (seq @dirty-cells)
      (some (fn [[_ v]] (or (pos? (:goals v)) (pos? (:assists v)) (:played? v)
                           (pos? (:yellow-cards v)) (pos? (:red-cards v))))
            (:player-statistics @form-data))))

(defn- update-stat! [form-data dirty-cells pid field value]
  (mark-stat-dirty! dirty-cells pid field)
  (swap! form-data assoc-in [:player-statistics pid field] value)
  (when (= field :played?)
    (when value
      (let [mins (get-in @form-data [:player-statistics pid :minutes-played] 0)]
        (when (or (nil? mins) (zero? mins))
          (swap! form-data assoc-in [:player-statistics pid :minutes-played] 90))))
    (when-not value
      (swap! form-data assoc-in [:player-statistics pid :minutes-played] 0))))

(defn- stat-grid-cell
  [form-data dirty-cells pid stat row-idx col-idx field touch? row-count]
  (let [value (get stat field 0)
        on-change #(update-stat! form-data dirty-cells pid field %)]
    [:td {:class (common/merge-classes
                  "px-2 py-3 text-center tabular-nums cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
                  (dirty-cell-class dirty-cells pid field)
                  (card-highlight-class stat field))
          :data-grid-row row-idx
          :data-grid-col col-idx
          :tab-index -1
          :on-key-down #(handle-grid-key! % row-idx col-idx row-count)}
     (case field
       :played?
       [common/checkbox-field
        (:played? stat)
        #(update-stat! form-data dirty-cells pid :played? %)]
       :yellow-cards
       [:div {:class "inline-flex flex-col items-center gap-0.5"}
        (when (pos? value)
          [:> Circle {:size 12 :class "text-amber-500 fill-amber-400"}])
        [common/number-stepper value on-change :min-val 0 :touch? touch?]]
       :red-cards
       [:div {:class "inline-flex flex-col items-center gap-0.5"}
        (when (pos? value)
          [:> AlertOctagon {:size 12 :class "text-rose-600 fill-rose-500"}])
        [common/number-stepper value on-change :min-val 0 :max-val 1 :touch? touch?]]
       [common/number-stepper value on-change :min-val 0 :touch? touch?])]))

(defn- player-stat-row
  [form-data player stats-map dirty-cells row-idx touch? row-count]
  (let [pid (str (:_id player))
        stat (merge (default-player-stat) (get stats-map pid))]
    [:tr {:class "hover:bg-slate-50 dark:hover:bg-slate-900/50"}
     [:td {:class "px-4 py-3 text-sm font-medium text-slate-900 dark:text-slate-100"}
      (:name player)]
     (stat-grid-cell form-data dirty-cells pid stat row-idx 0 :goals touch? row-count)
     (stat-grid-cell form-data dirty-cells pid stat row-idx 1 :assists touch? row-count)
     (stat-grid-cell form-data dirty-cells pid stat row-idx 2 :minutes-played touch? row-count)
     (stat-grid-cell form-data dirty-cells pid stat row-idx 3 :yellow-cards touch? row-count)
     (stat-grid-cell form-data dirty-cells pid stat row-idx 4 :red-cards touch? row-count)
     (stat-grid-cell form-data dirty-cells pid stat row-idx 5 :played? touch? row-count)]))

(defn- player-stat-card
  [form-data player stats-map dirty-cells row-idx touch?]
  (let [expanded? (r/atom false)
        pid (str (:_id player))]
    (fn [form-data player stats-map dirty-cells row-idx touch?]
      (let [stat (merge (default-player-stat) (get stats-map pid))]
        [:div {:class "rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900/90"}
         [:button {:type "button"
                   :class "flex w-full items-center justify-between gap-2 px-4 py-3 text-left min-h-[44px]"
                   :on-click #(swap! expanded? not)}
          [:span {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} (:name player)]
          [:span {:class "text-xs text-slate-500 tabular-nums"}
           (str (:goals stat) "G " (:assists stat) "A"
                (when (:played? stat) " • 90'"))]]
         (when @expanded?
           [:div {:class "space-y-3 border-t border-slate-100 px-4 py-3 dark:border-slate-800"}
            [:div {:class "grid grid-cols-2 gap-3"}
             [:div
              [:p {:class "mb-1 text-xs font-medium text-slate-500"} "Gols"]
              [common/number-stepper (:goals stat)
               #(update-stat! form-data dirty-cells pid :goals %)
               :min-val 0 :touch? true]]
             [:div
              [:p {:class "mb-1 text-xs font-medium text-slate-500"} "Assistências"]
              [common/number-stepper (:assists stat)
               #(update-stat! form-data dirty-cells pid :assists %)
               :min-val 0 :touch? true]]
             [:div
              [:p {:class "mb-1 text-xs font-medium text-slate-500"} "Minutos"]
              [common/number-stepper (:minutes-played stat)
               #(update-stat! form-data dirty-cells pid :minutes-played %)
               :min-val 0 :max-val 120 :touch? true]]
             [:div
              [:p {:class "mb-1 text-xs font-medium text-slate-500"} "Participação"]
              [common/checkbox-field (:played? stat)
               #(update-stat! form-data dirty-cells pid :played? %)
               :label "Jogou"]]]
            [:div {:class "flex items-center justify-between gap-4"}
             [:div {:class (common/merge-classes "flex-1 rounded-lg px-2 py-2"
                                                (card-highlight-class stat :yellow-cards))}
              [:p {:class "mb-1 text-xs font-medium text-amber-700 dark:text-amber-300"} "Amarelo"]
              [common/number-stepper (:yellow-cards stat)
               #(update-stat! form-data dirty-cells pid :yellow-cards %)
               :min-val 0 :touch? true]]
             [:div {:class (common/merge-classes "flex-1 rounded-lg px-2 py-2"
                                                (card-highlight-class stat :red-cards))}
              [:p {:class "mb-1 text-xs font-medium text-rose-700 dark:text-rose-300"} "Vermelho"]
              [common/number-stepper (:red-cards stat)
               #(update-stat! form-data dirty-cells pid :red-cards %)
               :min-val 0 :max-val 1 :touch? true]]]])]))))

(defn- enrollment-banner [championship-id]
  (when-not (str/blank? (str championship-id))
    [common/alert
     [:span "Nenhum jogador inscrito neste campeonato. "
      [:a {:class "font-semibold text-brand-maroon underline dark:text-brand-maroon/90"
           :href (str "/#/championships/" championship-id)
           :on-click (fn [e]
                       (.preventDefault e)
                       (rfe/push-state :championship-detail {:id championship-id}))}
       "Gerir inscrições"]]
     :variant :warning]))

(defn- match-form-sticky-header
  [{:keys [form-title hub-champ-name opponent home-score away-score
           dirty? submitting? draft-restored?]}]
  [:div {:class "sticky top-0 z-10 -mx-4 mb-4 border-b border-slate-200 bg-white/95 px-4 py-3 backdrop-blur dark:border-slate-700 dark:bg-slate-900/95 sm:-mx-0 sm:rounded-xl sm:border sm:px-4"}
   [:div {:class "flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between"}
    [:div {:class "min-w-0"}
     (when hub-champ-name
       [common/badge hub-champ-name :variant :info :class "mb-1"])
     [:h2 {:class "truncate text-lg font-semibold text-slate-900 dark:text-slate-100"}
      (or (not-empty (str/trim opponent)) form-title)]
     (when (and hub-champ-name (not (str/blank? opponent)))
       [:p {:class "text-xs text-slate-500 dark:text-slate-400"} hub-champ-name])]
    [:div {:class "flex flex-wrap items-center gap-3"}
     [:p {:class "text-xl font-bold tabular-nums text-slate-900 dark:text-slate-100"}
      (str home-score " x " away-score)]
     [:div {:class "flex flex-col items-end gap-0.5 text-xs text-slate-500 dark:text-slate-400"}
      (when dirty?
        [:span {:class "text-amber-600 dark:text-amber-400"} "Partida em curso"])
      (when submitting?
        [:span "A sincronizar…"])
      (when draft-restored?
        [:span {:class "text-sky-600 dark:text-sky-400"} "Rascunho restaurado"])]]]])

(defn match-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        preset-championship-id (str (or (:championship-id params) ""))
        form-data (r/atom {:championship-id (if is-edit? "" preset-championship-id)
                           :home-team-id ""
                           :date (if is-edit? "" (match-draft/today-date-str))
                           :opponent ""
                           :away-score 0
                           :venue ""
                           :player-statistics {}})
        resolve-draft-route-id
        (fn []
          (match-draft/match-draft-route-id
           is-edit? id
           (or (not-empty preset-championship-id)
               (not-empty (str/trim (str (:championship-id @form-data))))
               "")))
        match-loading? (r/atom is-edit?)
        enrolled-players (r/atom [])
        players-loading? (r/atom false)
        existing-stats (r/atom nil)
        dirty-cells (r/atom #{})
        draft-restored? (r/atom false)
        draft-banner? (r/atom nil)
        save-draft-timer (r/atom nil)
        submitting? (r/atom false)
        form-error (r/atom nil)
        field-errors (r/atom {})
        conflict (r/atom nil)
        match-field-keys #{:date :opponent :venue :away-score :championship-id :home-team-id}
        championship-seasons (r/atom [])
        seasons-loading? (r/atom false)
        parse-int (fn [v]
                    (let [trimmed (str/trim (str (or v "")))]
                      (when (not-empty trimmed)
                        (js/parseInt trimmed 10))))
        update-form-field!
        (fn [k v]
          (match-draft/save-draft! (resolve-draft-route-id) (swap! form-data assoc k v)))
        schedule-draft-save!
        (fn []
          (when (not= @draft-banner? :stale)
            (match-draft/save-draft! (resolve-draft-route-id) @form-data)
            (when-let [tid @save-draft-timer]
              (js/clearTimeout tid))
            (reset! save-draft-timer
                    (js/setTimeout
                     #(do (reset! save-draft-timer nil)
                          (when (not= @draft-banner? :stale)
                            (match-draft/save-draft! (resolve-draft-route-id) @form-data)))
                     400))))
        init-player-stats (fn [players existing]
                            (let [existing-by-id (into {}
                                                       (map (fn [stat]
                                                              (let [pid (str (:player-id stat))
                                                                    goals (or (parse-int (:goals stat)) 0)
                                                                    assists (or (parse-int (:assists stat)) 0)
                                                                    minutes (or (parse-int (:minutes-played stat)) 0)
                                                                    yellow (or (parse-int (:yellow-cards stat)) 0)
                                                                    red (or (parse-int (:red-cards stat)) 0)]
                                                                [pid {:goals goals
                                                                      :assists assists
                                                                      :yellow-cards yellow
                                                                      :red-cards red
                                                                      :minutes-played minutes
                                                                      :played? (> minutes 0)}]))
                                                            (or existing [])))]
                              (into {}
                                    (map (fn [player]
                                           (let [pid (str (:_id player))]
                                             [pid (merge (default-player-stat)
                                                         (get existing-by-id pid))]))
                                         players))))
        valid-form? (fn []
                      (let [errs (cond-> {}
                                   (str/blank? (:date @form-data)) (assoc :date "Informe a data"))]
                        (when (seq errs) errs)))
        prepare-payload (fn []
                          (let [home-team-id (:home-team-id @form-data)
                                team-by-player (into {}
                                                     (keep (fn [player]
                                                             (when-let [tid (:team-id player)]
                                                               [(str (:_id player)) (str tid)]))
                                                           @enrolled-players))
                                stats-map (:player-statistics @form-data)
                                stats-vec (->> stats-map
                                               (filter (fn [[_pid stat]]
                                                         (or (:played? stat)
                                                             (> (:goals stat) 0)
                                                             (> (:assists stat) 0)
                                                             (> (:yellow-cards stat) 0)
                                                             (> (:red-cards stat) 0))))
                                               (mapv (fn [[pid stat]]
                                                       (let [played? (:played? stat)
                                                             mins (or (:minutes-played stat) 0)]
                                                         {:player-id pid
                                                          :team-id (or (get team-by-player pid) home-team-id)
                                                          :goals (:goals stat)
                                                          :assists (:assists stat)
                                                          :yellow-cards (or (:yellow-cards stat) 0)
                                                          :red-cards (or (:red-cards stat) 0)
                                                          :minutes-played (if played?
                                                                            (if (pos? mins) mins 90)
                                                                            0)}))))]
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
        load-seasons! (fn [championship-id]
                        (if (str/blank? championship-id)
                          (reset! championship-seasons [])
                          (do
                            (reset! seasons-loading? true)
                            (api/get-championship-seasons
                             championship-id
                             (fn [result]
                               (reset! championship-seasons result)
                               (reset! seasons-loading? false))
                             (fn [_err]
                               (reset! championship-seasons [])
                               (reset! seasons-loading? false))))))
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
                                                              :date (or (common/match-date-for-input (:date result)) "")
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
                                         (reset! match-loading? false)))))
        mark-all-participation! (fn []
                                  (swap! form-data update :player-statistics
                                         (fn [stats]
                                           (reduce
                                            (fn [m player]
                                              (let [pid (str (:_id player))
                                                    current (merge (default-player-stat) (get m pid))]
                                                (assoc m pid
                                                       (-> current
                                                           (assoc :played? true)
                                                           (assoc :minutes-played
                                                                  (if (pos? (:minutes-played current))
                                                                    (:minutes-played current)
                                                                    90))))))
                                            stats
                                            @enrolled-players)))
                                  (reset! dirty-cells #{}))
        normalize-draft-stats (fn [fd]
                                (update fd :player-statistics
                                        (fn [m]
                                          (into {}
                                                (map (fn [[k v]]
                                                       [(str k) (merge (default-player-stat)
                                                                       (if (map? v) v {}))]))
                                                     (or m {})))))
        restore-draft! (fn [draft]
                         (when-let [fd (normalize-draft-stats (:form-data draft))]
                           (reset! form-data fd)
                           (reset! draft-restored? true)
                           (reset! draft-banner? nil)
                           (when-let [cid (:championship-id fd)]
                             (when-not (str/blank? (str cid))
                               (let [stats-vec (mapv (fn [[pid s]]
                                                        (assoc s :player-id (str pid)))
                                                      (:player-statistics fd))]
                                 (load-enrolled! (str cid) :existing-stats-data stats-vec)
                                 (load-seasons! (str cid)))))))
        discard-draft! (fn []
                         (match-draft/clear-draft! (resolve-draft-route-id))
                         (reset! draft-banner? nil))
        form-mounted? (r/atom false)]
    (r/create-class
     {:component-did-mount (fn []
                             (reset! form-mounted? true)
                             (when (and (not is-edit?) (str/blank? preset-championship-id))
                               (when-let [last-cid (match-draft/load-last-championship-id)]
                                 (when-not (str/blank? last-cid)
                                   (swap! form-data assoc :championship-id last-cid))))
                             (when-let [draft (match-draft/load-draft (resolve-draft-route-id))]
                               (if (match-draft/draft-stale? (:saved-at draft))
                                   (reset! draft-banner? :stale)
                                   (restore-draft! draft)))
                             (load-match!)
                             (when (and (not is-edit?)
                                        (not @draft-restored?)
                                        (not (str/blank? (or preset-championship-id
                                                             (:championship-id @form-data)))))
                               (let [cid (or preset-championship-id (:championship-id @form-data))]
                                 (load-enrolled! cid)
                                 (load-seasons! cid))))
      :component-will-unmount (fn []
                                (when-let [tid @save-draft-timer]
                                  (js/clearTimeout tid)
                                  (reset! save-draft-timer nil))
                                (when (and @form-mounted?
                                           (not= @draft-banner? :stale))
                                  (let [route-id (resolve-draft-route-id)
                                        existing (match-draft/load-draft route-id)]
                                    (when-not (match-draft/draft-stale? (:saved-at existing))
                                      (match-draft/save-draft! route-id @form-data))))
                                (state/clear-page-context!))
      :component-did-update
      (fn [_ _]
        (when @form-mounted? (schedule-draft-save!))
        (let [championships (:championships @state/app-state)
              selected-champ-id (:championship-id @form-data)
              selected-championship (when-not (str/blank? selected-champ-id)
                                      (first (filter #(= (str (:_id %)) selected-champ-id) championships)))
              hub-champ-name (:name selected-championship)
              form-title (if is-edit? "Editar Partida" "Nova Partida")
              next-ctx (when hub-champ-name {:badge hub-champ-name :title form-title})
              current-ctx (get-in @state/app-state [:ui :page-context])]
          (when (not= next-ctx current-ctx)
            (state/set-page-context! (or next-ctx {})))))
      :reagent-render
      (fn []
        (let [championships (:championships @state/app-state)
              teams (:teams @state/app-state)
              selected-champ-id (:championship-id @form-data)
              selected-championship (when-not (str/blank? selected-champ-id)
                                      (first (filter #(= (str (:_id %)) selected-champ-id) championships)))
              active-championship (or selected-championship
                                      (first (filter #(common/status-active? (:status %)) championships)))
              active-champ-id (when active-championship (str (:_id active-championship)))
              active-champ-name (when active-championship (:name active-championship))
              has-active-season? (boolean (some #(common/status-active? (:status %)) @championship-seasons))
              create-locked? (and (not is-edit?)
                                  (not (str/blank? selected-champ-id))
                                  (not @seasons-loading?)
                                  (not has-active-season?))
              _ (when (and active-champ-id (str/blank? (:championship-id @form-data)))
                  (swap! form-data assoc :championship-id active-champ-id)
                  (load-enrolled! active-champ-id)
                  (load-seasons! active-champ-id))
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
              sorted-players (sort-by #(str/lower-case (or (:name %) "")) @enrolled-players)
              hub-champ-id (or (not-empty selected-champ-id) (not-empty preset-championship-id))
              hub-champ-name (or (:name selected-championship) active-champ-name)
              form-title (if is-edit? "Editar Partida" "Nova Partida")
              display-champ-name (or hub-champ-name active-champ-name)
              row-count (count sorted-players)
              form-dirty? (form-has-local-changes? dirty-cells form-data)
              opponent (:opponent @form-data)
              incomplete-opponent? (str/blank? (str opponent))
              incomplete-venue? (str/blank? (str (:venue @form-data)))]
          [:div {:class "space-y-6 pb-24 md:pb-6"}
           [common/breadcrumb (match-breadcrumb-items hub-champ-id hub-champ-name form-title)]
           (when (= :restore @draft-banner?)
             [common/alert
              [:div {:class "flex flex-wrap items-center justify-between gap-2"}
               [:span "Rascunho local encontrado."]
               [:div {:class "flex gap-2"}
                [common/button "Restaurar"
                 #(restore-draft! (match-draft/load-draft (resolve-draft-route-id)))
                 :variant :primary :class "text-xs px-3 py-1"]
                [common/button "Descartar" discard-draft! :variant :outline :class "text-xs px-3 py-1"]]]
              :variant :info])
           (when (= :stale @draft-banner?)
             [common/alert
              [:div {:class "flex flex-wrap items-center justify-between gap-2"}
               [:span "Rascunho antigo (>7 dias)."]
               [common/button "Descartar" discard-draft! :variant :outline :class "text-xs px-3 py-1"]]
              :variant :warning])
           (if @match-loading?
             [:div {:class "space-y-4"}
              [common/skeleton-score-header]
              [common/skeleton-table ["Nome" "Gols" "Assist." "Min" "Am." "Vm." "Jogou"] :rows 5]]
             [:form {:class "space-y-6"
                     :on-submit (fn [e]
                                  (.preventDefault e)
                                  (reset! form-error nil)
                                  (reset! field-errors {})
                                  (reset! conflict nil)
                                  (if-let [errs (valid-form?)]
                                    (do
                                      (reset! field-errors errs)
                                      (state/toast-field-errors! errs))
                                    (do
                                      (reset! submitting? true)
                                      (let [payload (prepare-payload)
                                            on-success (fn [_result]
                                                         (reset! submitting? false)
                                                         (reset! dirty-cells #{})
                                                         (match-draft/clear-draft! (resolve-draft-route-id))
                                                         (when-not (str/blank? selected-champ-id)
                                                           (match-draft/save-last-championship-id! selected-champ-id))
                                                         (state/toast-success! "Partida guardada.")
                                                         (effects/ensure-matches! {:force? true})
                                                         (effects/ensure-players! {:force? true})
                                                         (effects/ensure-dashboard! {:force? true})
                                                         (navigate-match-hub! hub-champ-id))
                                            on-error (fn [error resp]
                                                       (reset! submitting? false)
                                                       (common/apply-form-api-error!
                                                        {:message error
                                                         :response resp
                                                         :field-keys match-field-keys
                                                         :field-errors field-errors
                                                         :form-error form-error
                                                         :conflict conflict})
                                                       (when (and (not= 409 (:status resp))
                                                                  (empty? @field-errors))
                                                         (state/toast-error! (or @form-error error))))]
                                        (if is-edit?
                                          (api/update-match id payload on-success on-error)
                                          (api/create-match payload on-success on-error))))))}
              (when @conflict
                [common/form-conflict-alert (:message @conflict)
                 :action-label (when (:resource-id @conflict) "Ver partida")
                 :on-action (when-let [mid (:resource-id @conflict)]
                              #(rfe/push-state :match-detail {:id (str mid)}))])
              (when (seq @field-errors)
                [common/form-error-summary @field-errors])
              (when (and @form-error (empty? @field-errors))
                [common/alert @form-error :variant :error])

              [match-form-sticky-header
               {:form-title form-title
                :hub-champ-name display-champ-name
                :opponent opponent
                :home-score home-score
                :away-score away-score
                :dirty? form-dirty?
                :submitting? @submitting?
                :draft-restored? @draft-restored?}]

              [common/card
               [:h3 {:class "app-section-title"} "Dados gerais"]
               [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
                [common/readonly-field "Campeonato" (or display-champ-name "Nenhum campeonato ativo")]
                [common/readonly-field "Time" "Galáticos"]
                [common/input-field "Data" (:date @form-data) #(update-form-field! :date %) :type "date" :required? true :error (:date @field-errors)]
                [:div
                 [common/input-field "Adversário" opponent #(update-form-field! :opponent %)
                  :error (:opponent @field-errors)
                  :on-blur #(match-draft/save-draft! (resolve-draft-route-id) @form-data)]
                 (when incomplete-opponent?
                   [common/badge "Recomendado" :variant :warning :class "mt-1"])]
                [:div {:class "md:col-span-2"}
                 [common/input-field "Local" (:venue @form-data) #(update-form-field! :venue %) :error (:venue @field-errors)]
                 (when incomplete-venue?
                   [common/badge "Opcional" :variant :warning :class "mt-1"])]]
               [:div {:class "mt-6 flex flex-col items-center gap-4"}
                [:div {:class "text-center space-y-2"}
                 [:label {:class "text-sm font-medium text-slate-700 dark:text-slate-200"} "Gols do Adversário"]
                 [:div {:class "flex justify-center"}
                  [common/number-stepper away-score #(swap! form-data assoc :away-score %) :min-val 0]]]
                [:div {:class "text-center space-y-2"}
                 [:label {:class "text-sm font-medium text-slate-700 dark:text-slate-200"} "Resultado Final"]
                 [:p {:class "text-2xl font-bold tabular-nums text-slate-900 dark:text-slate-100"}
                  (str home-score " x " away-score)]]]]

              [common/card
               [:div {:class "flex flex-wrap items-center justify-between gap-3"}
                [:h3 {:class "app-section-title"} "Estatísticas"]
                [:div {:class "flex flex-wrap items-center gap-2"}
                 (when (seq sorted-players)
                   [common/button "Marcar participação de todos"
                    mark-all-participation!
                    :variant :outline])
                 [:div {:class "text-xs text-slate-500"}
                  (str "Gols: " total-goals " • Assistências: " total-assists " • Placar: " home-score " x " away-score)]]]
               (cond
                 (and (not is-edit?) (str/blank? (:championship-id @form-data)))
                 [:p {:class "mt-3 text-sm text-amber-600 dark:text-amber-400"} "Nenhum campeonato ativo encontrado."]

                 @players-loading?
                 [common/skeleton-table ["Nome" "Gols" "Assist." "Min" "Am." "Vm." "Jogou"] :rows 5 :class "mt-3"]

                 (empty? sorted-players)
                 [enrollment-banner selected-champ-id]

                 :else
                 [:div {:class "mt-4 space-y-3"}
                  (when (and (not is-edit?) create-locked?)
                    [:p {:class "text-sm text-amber-600 dark:text-amber-400"}
                     (if (empty? @championship-seasons)
                       "Nenhuma temporada cadastrada neste campeonato. Crie uma temporada ativa para registrar partidas."
                       "Nenhuma temporada ativa neste campeonato. Ative uma temporada ou edite partidas existentes.")])
                  [:div {:class "hidden md:block overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900"}
                   [:table {:class "min-w-full divide-y divide-slate-200 dark:divide-slate-700"}
                    [:thead {:class "bg-slate-50 dark:bg-slate-800/80"}
                     [:tr
                      [:th {:scope "col" :class "px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"} "Nome"]
                      [:th {:scope "col" :class "px-2 py-3 text-center text-xs font-semibold uppercase tracking-wide tabular-nums text-slate-500 dark:text-slate-400"} "Gols"]
                      [:th {:scope "col" :class "px-2 py-3 text-center text-xs font-semibold uppercase tracking-wide tabular-nums text-slate-500 dark:text-slate-400"} "Assist."]
                      [:th {:scope "col" :class "px-2 py-3 text-center text-xs font-semibold uppercase tracking-wide tabular-nums text-slate-500 dark:text-slate-400"} "Min"]
                      [:th {:scope "col" :class "px-2 py-3 text-center text-xs font-semibold uppercase tracking-wide tabular-nums text-slate-500 dark:text-slate-400"} "Am."]
                      [:th {:scope "col" :class "px-2 py-3 text-center text-xs font-semibold uppercase tracking-wide tabular-nums text-slate-500 dark:text-slate-400"} "Vm."]
                      [:th {:scope "col" :class "px-2 py-3 text-center text-xs font-semibold uppercase tracking-wide tabular-nums text-slate-500 dark:text-slate-400"} "Jogou"]]]
                    [:tbody {:class "divide-y divide-slate-100 dark:divide-slate-800"}
                     (doall
                      (for [[idx player] (map-indexed vector sorted-players)]
                        ^{:key (:_id player)}
                        (player-stat-row form-data player stats-map dirty-cells idx false row-count)))]]]
                  [:div {:class "space-y-3 md:hidden"}
                   (doall
                    (for [[idx player] (map-indexed vector sorted-players)]
                      ^{:key (str "card-" (:_id player))}
                      [player-stat-card form-data player stats-map dirty-cells idx true]))]])]

              [:div {:class "hidden md:flex flex-wrap gap-2"}
               [common/submit-button (if is-edit? "Atualizar" "Criar Partida")
                nil
                :saving? @submitting?
                :disabled (and (not is-edit?) create-locked?)]
               [common/button "Cancelar" #(navigate-match-hub! hub-champ-id) :variant :outline]]

              [:div {:class "md:hidden fixed inset-x-0 bottom-16 z-20 border-t border-slate-200 bg-white/95 p-4 backdrop-blur dark:border-slate-700 dark:bg-slate-900/95"}
               [common/submit-button "Guardar partida"
                nil
                :saving? @submitting?
                :disabled (and (not is-edit?) create-locked?)
                :class "w-full min-h-11"]]])]))})))

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
           (when (and authenticated? (common/status-active? (:status season)))
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
              [:p {:class "app-muted"}
               (if (common/status-active? (:status season))
                 ui-copy/empty-matches-season
                 (str "Temporada " (common/status-label (:status season)) " — novas partidas só podem ser criadas em temporadas ativas."))])])]))))

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
              has-active-season? (boolean (some #(common/status-active? (:status %)) @seasons))
              delete-match! (fn [match]
                              (let [match-id (:_id match)]
                                (delete-undo/schedule!
                                 {:message ui-copy/match-removed
                                  :on-remove #(swap! matches (fn [ms]
                                                               (filterv (fn [m] (not= (:_id m) match-id)) ms)))
                                  :on-rollback #(swap! matches (fn [ms]
                                                                 (vec (sort-by :date (conj ms match)))))
                                  :on-commit (fn [on-success on-error]
                                              (api/delete-match match-id on-success on-error))})))
              sorted-seasons (sort-by (fn [s] (str (:season s))) @seasons)
              matches-without-season (filter #(nil? (:season-id %)) @matches)]
          [:div {:class "space-y-6"}
           [common/breadcrumb
            [{:label "Campeonatos" :route :championships}
             {:label "Partidas" :route :matches}
             {:label (or (:name @championship) "Campeonato")}]]
           [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
            [:div
             (when (:name @championship)
               [common/badge (:name @championship) :variant :info :class "mb-2"])
             [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"}
              (or (:name @championship) "Carregando...")]]
            (when (and authenticated has-active-season?)
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
             [:div {:class "space-y-4"}
              [common/skeleton-line :class "h-8 w-64"]
              [common/skeleton-line :class "h-24 w-full"]]

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
