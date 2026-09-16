(ns galaticos.components.championships
  "Championship list and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.components.merge-modal :as merge-modal]
            [galaticos.components.player-picker :as player-picker]
            [galaticos.effects :as effects]
            [galaticos.delete-undo :as delete-undo]
            [galaticos.enrollment :as enrollment]
            [galaticos.finalization :as finalization]
            [galaticos.breadcrumbs :as breadcrumbs]
            [galaticos.ui-copy :as ui-copy]
            [clojure.string :as str]
            ["lucide-react" :refer [Trophy AlertTriangle Check Circle]]))

(defn- api-get
  "Read API map field with keyword or string key (JSON interop)."
  [m k]
  (when m
    (or (get m k) (get m (name k)))))

(defn- normalize-id [v]
  (cond
    (string? v) v
    (map? v) (or (get v "$oid") (get v :$oid))
    :else (when v (str v))))

(defn- leaderboard-mini
  "Small ordered list for one metric (rows from /leaderboards API)."
  [label metric-key rows]
  [common/card
   [:h4 {:class "text-sm font-semibold text-slate-800"} label]
   (if (seq rows)
     [:ol {:class "mt-2 list-decimal list-inside space-y-1 text-sm text-slate-700"}
      (for [row rows]
        (let [pid (normalize-id (or (:player-id row) (:_id row)))]
          ^{:key (str pid "-" (:name row))}
          [:li (cond-> {:class "rounded px-1 py-0.5"}
                 pid (assoc :class "cursor-pointer rounded px-1 py-0.5 hover:bg-slate-100 dark:hover:bg-slate-800"
                            :role "link"
                            :tab-index 0
                            :on-click #(rfe/push-state :player-detail {:id pid})
                            :on-key-down (fn [e]
                                           (when (#{"Enter" " "} (.-key e))
                                             (.preventDefault e)
                                             (rfe/push-state :player-detail {:id pid})))))
           [:span (:name row)]
           " — "
           [:span {:class "font-medium tabular-nums"} (str (get row metric-key 0))]]))]
     [:p {:class "app-muted text-sm mt-2"} "Sem dados"])])

(defn- champ-dup-candidates-seq [entry]
  (let [c (or (:candidates entry) (get entry "candidates"))]
    (cond (nil? c) [] (vector? c) c (sequential? c) (vec c) :else [])))

(defn- champ-normalize-dup-row [r]
  (let [pid (some-> (or (:player-id r) (get r "player-id")) str not-empty)
        cands (mapv (fn [c]
                      {:id (str (or (:id c) (get c "id")))
                       :name (or (:name c) (get c "name"))
                       :similarity (or (:similarity c) (get c "similarity"))})
                    (champ-dup-candidates-seq r))]
    (when (and pid (seq cands))
      {:player-id pid :candidates cands})))

(defn- champ-dup-map-from-rows [rows]
  (let [v (cond (vector? rows) rows (sequential? rows) (vec rows) :else [])]
    (into {} (keep (fn [r] (when-let [row (champ-normalize-dup-row r)] [(:player-id row) row])) v))))

(defn- enrollment-progress-bar
  "RVMF progress bar for n/max. Pure presentational."
  [{:keys [ratio at-limit?]}]
  (when (some? ratio)
    (let [pct (int (* 100 ratio))
          fill (if at-limit?
                 "bg-amber-500 dark:bg-amber-400"
                 "bg-brand-maroon")]
      [:div {:class "h-2 w-24 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700"
             :role "progressbar"
             :aria-valuemin 0
             :aria-valuemax 100
             :aria-valuenow pct}
       [:div {:class (common/merge-classes "h-full rounded-full transition-all" fill)
              :style {:width (str pct "%")}}]])))

(defn- enrolled-roster
  "Desktop compact table + mobile card stack (≥44px targets)."
  [{:keys [players authenticated? dup-report teams on-remove on-merge]}]
  (let [by-id (enrollment/teams-index teams)
        rows (mapv #(enrollment/enrolled-display-row % by-id) players)]
    (if-not (seq rows)
      [:p {:class "app-muted"} ui-copy/empty-championship-roster]
      [:<>
       [:div {:class "hidden lg:block"}
        [common/table
         ["Atleta" "Posição" "Time Base" "Ação"]
         (map (fn [{:keys [id label position team-base player]}]
                (let [dup? (and authenticated?
                                (seq (:candidates (get dup-report id))))]
                  [[:div {:class "flex min-w-0 items-center gap-2"}
                    (when dup?
                      [:button {:type "button"
                                :class "shrink-0 text-amber-500 hover:text-amber-600"
                                :aria-label "Possível duplicado — mesclar"
                                :on-click #(on-merge id)}
                       [:> AlertTriangle {:size 18}]])
                    [:span {:class "truncate font-medium text-slate-800 dark:text-slate-100"} label]]
                   position
                   team-base
                   (when authenticated?
                     [common/button "Remover" #(on-remove player)
                      :variant :danger
                      :class "min-h-[36px]"])]))
              rows)
         :show-search? false
         :dense? true
         :sortable? true
         :row-data rows
         :on-row-click (fn [{:keys [id]}]
                         (when-let [pid (normalize-id id)]
                           (rfe/push-state :player-detail {:id pid})))]]
       [:div {:class "space-y-2 lg:hidden"}
        (doall
         (for [{:keys [id label position team-base player]} rows]
           (let [dup? (and authenticated?
                           (seq (:candidates (get dup-report id))))
                 go-player! #(when-let [pid (normalize-id id)]
                               (rfe/push-state :player-detail {:id pid}))]
             ^{:key id}
             [:div {:class "cursor-pointer rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-900/80"
                    :role "link"
                    :tab-index 0
                    :on-click go-player!
                    :on-key-down (fn [e]
                                   (when (#{"Enter" " "} (.-key e))
                                     (.preventDefault e)
                                     (go-player!)))}
              [:div {:class "flex items-start gap-2"}
               (when dup?
                 [:button {:type "button"
                           :class "mt-1 flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center text-amber-500 hover:text-amber-600"
                           :aria-label "Possível duplicado — mesclar"
                           :on-click (fn [e]
                                       (.stopPropagation e)
                                       (on-merge id))}
                  [:> AlertTriangle {:size 20}]])
               [:div {:class "min-w-0 flex-1"}
                [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} label]
                [:p {:class "mt-1 text-xs text-slate-500"} (str position " · " team-base)]]]
              (when authenticated?
                [:div {:class "mt-3"
                       :on-click #(.stopPropagation %)}
                 [common/button "Remover" #(on-remove player)
                  :variant :danger
                  :class "w-full min-h-[44px]"]])])))]])))

(defn- enroll-sequential!
  "POST enroll one-by-one via existing API. Calls on-done with result rows."
  [championship-id player-ids on-done]
  (let [results (atom [])]
    (letfn [(step [remaining]
              (if (empty? remaining)
                (on-done @results)
                (let [pid (first remaining)]
                  (api/enroll-player-in-championship
                   championship-id pid
                   (fn [_]
                     (swap! results conj {:id pid :ok? true})
                     (step (rest remaining)))
                   (fn [err]
                     (swap! results conj {:id pid :ok? false :error err})
                     (step (rest remaining)))))))]
      (step (vec player-ids)))))

(defn- batch-enroll-modal
  "Modal: local search + checkboxes over non-enrolled; submit via existing enroll API."
  [_props]
  (let [query (r/atom "")
        selected (r/atom #{})
        submitting? (r/atom false)
        local-error (r/atom nil)]
    (fn [{:keys [open? on-close players exclude-ids enrolled-count max-players
                 championship-id on-enrolled-reload]}]
      (when open?
        (let [remaining (enrollment/remaining-slots enrolled-count max-players)
              eligible (player-picker/sorted-eligible-for-picker
                        players exclude-ids @query)
              available-count (count (player-picker/eligible-players
                                     players exclude-ids))
              selected-count (count @selected)
              confirm? (and (enrollment/can-confirm-batch? @selected)
                            (not @submitting?))
              close! (fn []
                       (when-not @submitting?
                         (reset! query "")
                         (reset! selected #{})
                         (reset! local-error nil)
                         (on-close)))
              confirm! (fn []
                         (when confirm?
                           (reset! submitting? true)
                           (reset! local-error nil)
                           (let [ids (vec @selected)
                                 name-by-id (into {}
                                                  (keep (fn [p]
                                                          (when-let [pid (player-picker/player-id p)]
                                                            [pid (or (:name p) (get p "name") pid)]))
                                                        players))
                                 after-enroll!
                                 (fn [rows]
                                   (let [{:keys [succeeded failed]}
                                         (enrollment/partition-batch-results rows)
                                         ok-n (count succeeded)
                                         finish!
                                         (fn []
                                           (reset! submitting? false)
                                           (if (empty? failed)
                                             (do
                                               (state/toast-success! (ui-copy/batch-enroll-success ok-n))
                                               (close!))
                                             (let [labels (mapv #(or (get name-by-id %) %) failed)
                                                   msg (ui-copy/batch-enroll-partial-failure ok-n labels)]
                                               (reset! local-error msg)
                                               (state/toast-error! msg)
                                               (swap! selected
                                                      (fn [s]
                                                        (into #{} (remove (set succeeded) s)))))))]
                                     (api/get-championship-players
                                      championship-id
                                      (fn [player-rows]
                                        (on-enrolled-reload player-rows)
                                        (finish!))
                                      (fn [err]
                                        (state/toast-error! (str "Erro ao carregar inscritos: " err))
                                        (finish!)))))]
                             (enroll-sequential! championship-id ids after-enroll!))))]
          [common/modal
           {:title ui-copy/batch-enroll-title
            :on-close close!
            :content
            [:div {:class "space-y-3"}
             [:p {:class "text-sm font-medium tabular-nums text-slate-700 dark:text-slate-200"
                  :aria-live "polite"}
              (enrollment/batch-selection-label selected-count available-count)]
             (when (and remaining (zero? remaining))
               [:p {:class "text-sm text-amber-700 dark:text-amber-300"}
                (ui-copy/enrollment-limit-reached (long (enrollment/normalize-max-players max-players)))])
             (when @local-error
               [common/alert @local-error :variant :warning])
             [:input {:type "search"
                      :class "app-input w-full min-h-[44px]"
                      :placeholder ui-copy/batch-enroll-search-placeholder
                      :value @query
                      :disabled @submitting?
                      :on-change #(reset! query (.. % -target -value))}]
             [:div {:class "max-h-72 space-y-1 overflow-y-auto rounded-md border border-slate-200 p-2 dark:border-slate-700"}
              (cond
                (zero? available-count)
                [:p {:class "app-muted p-2"} ui-copy/batch-enroll-empty]
                (empty? eligible)
                [:p {:class "app-muted p-2"} ui-copy/batch-enroll-none-match]
                :else
                (doall
                 (for [p eligible]
                   (let [pid (player-picker/player-id p)
                         checked? (contains? @selected pid)
                         allow? (enrollment/batch-select-allowed? @selected pid remaining)
                         disabled? (or @submitting? (and (not checked?) (not allow?)))]
                     ^{:key pid}
                     [:label {:class (common/merge-classes
                                      "flex min-h-[44px] cursor-pointer items-center gap-3 rounded-md px-2 py-2"
                                      (when disabled? "opacity-50")
                                      (when checked? "bg-brand-maroon/5"))}
                      [:input {:type "checkbox"
                               :class "h-5 w-5 shrink-0"
                               :checked checked?
                               :disabled disabled?
                               :on-change #(swap! selected
                                                  enrollment/toggle-batch-id
                                                  pid remaining)}]
                      [:span {:class "min-w-0 flex-1 text-sm text-slate-800 dark:text-slate-100"}
                       (enrollment/athlete-label p)]]))))]]
            :actions
            [:<>
             [common/button "Cancelar" close!
              :variant :outline
              :modal-cancel? true
              :disabled @submitting?]
             [common/button ui-copy/batch-enroll-confirm confirm!
              :variant :primary
              :loading? @submitting?
              :disabled (not confirm?)]]}])))))

(defn- finalization-checklist-ui
  "Forcing-function checklist — green/amber rows; no dismiss."
  [items]
  [:div {:class "space-y-2" :aria-label ui-copy/finalize-checklist-title}
   [:div {:class "flex items-center justify-between gap-2"}
    [:h5 {:class "text-xs font-semibold uppercase tracking-wide text-slate-500"}
     ui-copy/finalize-checklist-title]
    [:span {:class "text-xs tabular-nums text-slate-500"}
     (finalization/checklist-summary-label items)]]
   [:ul {:class "space-y-1.5"}
    (doall
     (for [{:keys [id label ok?]} items]
       ^{:key (name id)}
       [:li {:class (common/merge-classes
                     "flex min-h-[44px] items-center gap-2 rounded-md border px-3 py-2 text-sm"
                     (if ok?
                       "border-emerald-200 bg-emerald-50/80 text-emerald-800 dark:border-emerald-800/40 dark:bg-emerald-950/30 dark:text-emerald-200"
                       "border-amber-200 bg-amber-50/80 text-amber-800 dark:border-amber-800/40 dark:bg-amber-950/30 dark:text-amber-200"))}
        (if ok?
          [:> Check {:size 18 :className "shrink-0"}]
          [:> Circle {:size 18 :className "shrink-0 opacity-60"}])
        [:span label]]))]])

(defn championship-list []
  (let [search (r/atom "")]
    (fn []
      (let [{:keys [authenticated championships championships-loading?]} @state/app-state
            q (str/lower-case (str/trim @search))
            filtered (if (str/blank? q)
                       championships
                       (filterv (fn [ch]
                                  (str/includes?
                                   (str/lower-case (str (or (api-get ch :name) "")))
                                   q))
                                championships))]
        [:div {:class "space-y-6"}
         [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
          [:div
           [:p {:class "text-sm text-slate-500"} "Gestão de competições"]
           [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} "Campeonatos"]]
          [:div {:class "flex flex-wrap gap-2"}
           (when authenticated
             [common/button "Novo Campeonato" #(rfe/push-state :championship-new) :variant :primary])
           [common/button "Atualizar" #(effects/ensure-championships! {:force? true}) :variant :outline]]]
         [common/card
          [common/list-toolbar
           {:search @search
            :search-id "championships-search"
            :search-placeholder "Buscar campeonato..."
            :on-search-change #(reset! search %)
            :result-count (when-not championships-loading? (count filtered))
            :on-clear #(reset! search "")
            :clear-disabled? (str/blank? @search)}]
          (cond
            championships-loading?
            [common/skeleton-table ["Nome" "Temporada" "Formato" "Status" "Títulos"] :rows 6 :class "mt-3"]
            (seq filtered)
            [common/table
             ["Nome" "Temporada" "Formato" "Status" "Títulos"]
             (map (fn [ch]
                    [(api-get ch :name)
                     (api-get ch :season)
                     (api-get ch :format)
                     (let [raw-status (or (api-get ch :status) "active")]
                       [common/badge (common/status-label raw-status)
                        :variant (common/status-variant raw-status)])
                     (or (api-get ch :titles-count) 0)])
                  filtered)
             :numeric-columns #{4}
             :show-search? false
             :on-row-click (fn [ch]
                             (if-let [id (:_id ch)]
                               (rfe/push-state :championship-detail {:id id})
                               (state/set-error! "ID do campeonato ausente; não foi possível abrir detalhes.")))
             :row-data filtered
             :sortable? true
             :class "mt-3"]
            :else [:p {:class "app-muted mt-3"}
                   (if (str/blank? @search)
                     "Nenhum campeonato encontrado"
                     "Nenhum campeonato corresponde à busca.")])]]))))

(defn championship-detail [params]
  (let [championship (r/atom nil)
        matches (r/atom [])
        seasons (r/atom [])
        enrolled-players (r/atom [])
        all-players (r/atom [])
        players-catalog-ready? (r/atom false)
        selected-winners (r/atom #{})
        titles-award-count (r/atom "1")
        finalize-reason (r/atom "")
        new-season-label (r/atom "")
        finalizing? (r/atom false)
        deleting? (r/atom false)
        loading? (r/atom true)
        error (r/atom nil)
        not-found? (r/atom false)
        leaderboards (r/atom nil)
        champ-dup-report (r/atom {})
        champ-merge-ui (r/atom {:active false :initial-ref nil :tick 0})
        batch-enroll-open? (r/atom false)
        id (:id params)
        refresh-champ-dups!
        (fn []
          (api/get-player-duplicates
           {:championship-id id}
           (fn [rows]
             (reset! champ-dup-report (champ-dup-map-from-rows rows)))
           (fn [_ _]
             (reset! champ-dup-report {}))))
        on-enrolled-loaded
        (fn [rows]
          (reset! enrolled-players rows)
          (refresh-champ-dups!))
        open-champ-merge!
        (fn [opts]
          (swap! champ-merge-ui merge {:active true
                                        :initial-ref (:initial-ref opts)
                                        :tick (inc (:tick @champ-merge-ui))}))
        close-champ-merge! #(swap! champ-merge-ui assoc :active false :initial-ref nil)
        remove-enrolled! (fn [player]
                           (let [pid (str (:_id player))
                                 pname (:name player)]
                             (delete-undo/schedule!
                              {:message (ui-copy/roster-player-removed pname)
                               :on-remove #(swap! enrolled-players
                                                 (fn [ps]
                                                   (vec (remove (fn [p] (= (str (:_id p)) pid)) ps))))
                               :on-rollback #(swap! enrolled-players
                                                  (fn [ps]
                                                    (enrollment/sort-enrolled-players (conj ps player))))
                               :on-commit (fn [on-success on-error]
                                            (api/unenroll-player-from-championship
                                             id pid
                                             (fn [result]
                                               (on-success result)
                                               (api/get-championship-players id on-enrolled-loaded
                                                                             (fn [e]
                                                                               (let [msg (str "Erro ao carregar inscritos: " e)]
                                                                                 (reset! error msg)
                                                                                 (state/toast-error! msg)))))
                                             on-error))})))
        delete-championship! (fn []
                               (delete-undo/schedule!
                                {:message ui-copy/championship-removed
                                 :on-remove #(rfe/push-state :championships)
                                 :on-rollback #(rfe/push-state :championship-detail {:id id})
                                 :on-commit (fn [on-success on-error]
                                              (reset! deleting? true)
                                              (api/delete-championship id
                                                                       (fn [result]
                                                                         (reset! deleting? false)
                                                                         (on-success result)
                                                                         (effects/ensure-championships! {:force? true}))
                                                                       (fn [err]
                                                                         (reset! deleting? false)
                                                                         (on-error err))))}))
        load! (fn []
                (reset! error nil)
                (reset! not-found? false)
                (reset! loading? true)
                (reset! all-players [])
                (reset! matches [])
                (reset! seasons [])
                (reset! leaderboards nil)
                (reset! players-catalog-ready? false)
                (reset! new-season-label "")
                (api/get-championship-leaderboards id #(reset! leaderboards %) (fn [_e]))
                (api/get-championship id
                                      (fn [result]
                                        (reset! championship result)
                                        (reset! selected-winners (set (map str (:winner-player-ids result))))
                                        (let [active-season-id (:active-season-id result)]
                                          (when active-season-id
                                            (api/get-matches {:season-id active-season-id}
                                                             (fn [result]
                                                               (reset! matches result))
                                                             (fn [err]
                                                               (let [msg (str "Erro ao carregar partidas: " err)]
                                                                 (reset! error msg)
                                                                 (state/toast-error! msg))))))
                                        (reset! loading? false))
                                      (fn [err resp]
                                        (reset! loading? false)
                                        (if (and resp (= 404 (:status resp)))
                                          (do (reset! not-found? true)
                                              (reset! error "Campeonato não encontrado."))
                                          (let [msg (str "Erro ao carregar campeonato: " err)]
                                            (reset! not-found? false)
                                            (reset! error msg)
                                            (state/toast-error! msg)))))
                (api/get-championship-seasons id
                                              (fn [result]
                                                (reset! seasons result))
                                              (fn [err]
                                                (let [msg (str "Erro ao carregar temporadas: " err)]
                                                  (reset! error msg)
                                                  (state/toast-error! msg))))
                (api/get-championship-players id on-enrolled-loaded
                                              (fn [err]
                                                (let [msg (str "Erro ao carregar inscritos: " err)]
                                                  (reset! error msg)
                                                  (state/toast-error! msg))))
                (api/get-players {}
                                 (fn [result]
                                   (reset! all-players (api/coerce-player-list result))
                                   (reset! players-catalog-ready? true))
                                 (fn [err]
                                   (reset! players-catalog-ready? true)
                                   (let [msg (str "Erro ao carregar jogadores: " err)]
                                     (reset! error msg)
                                     (state/toast-error! msg)))))]
    (r/create-class
     {:component-did-mount (fn []
                             (effects/ensure-teams!)
                             (load!))
      :reagent-render
      (fn []
        (let [ch @championship
              authenticated (:authenticated @state/app-state)
              raw-status (or (api-get ch :status) "indefinido")
              winner-ids (set (map str (or (api-get ch :winner-player-ids) [])))
              winner-names (->> @enrolled-players
                                (filter #(contains? winner-ids (str (:_id %))))
                                (map :name)
                                (sort))
              enrolled-sorted (enrollment/sort-enrolled-players @enrolled-players)
              enrolled-exclude-ids (into #{} (keep player-picker/player-id @enrolled-players))
              teams (:teams @state/app-state)
              capacity (enrollment/enrollment-capacity
                        (count @enrolled-players)
                        (api-get ch :max-players))
              at-limit? (:at-limit? capacity)
              awarded-count (or (api-get ch :titles-award-count) 0)
              active-season-id (api-get ch :active-season-id)
              season-options (map (fn [s]
                                    [(str (:_id s))
                                     (str (:season s) " (" (common/status-label (or (:status s) "active")) ")")])
                                  @seasons)]
          [:div {:class "space-y-6"}
           (cond
             @error
             (if @not-found?
               [common/not-found-resource @error #(rfe/push-state :championships)]
               [:div
                [common/button "Tentar novamente" load! :variant :outline]])

             @loading?
             [:div {:class "min-h-[20rem] space-y-4"}
              [common/skeleton-line :class "h-8 w-64"]
              [common/skeleton-table ["Col" "Col" "Col"] :rows 4]]

             @championship
             [:<>
              [common/breadcrumb
               (breadcrumbs/build-breadcrumbs :championship-detail {:id id}
                                              :entity-label (api-get ch :name))]
              [:div {:class "flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"}
               [:div {:class "flex items-center gap-3"}
                [:div {:class "rounded-xl bg-brand-maroon/10 p-3 text-brand-maroon"}
                 [:> Trophy {:size 20}]]
                [:div
                 [:p {:class "text-sm text-slate-500"} "Campeonato"]
                 [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} (api-get ch :name)]
                 [common/badge (common/status-label raw-status)
                  :variant (common/status-variant raw-status) :class "mt-2"]]]
               (when authenticated
                 [:div {:class "flex flex-wrap gap-2"}
                  [common/button "Editar" #(rfe/push-state :championship-edit {:id id}) :variant :outline]
                  [common/button "Exportar CSV"
                   #(api/download-csv! (str "/api/exports/championships/" id ".csv")
                                       (str (or (api-get ch :name) "campeonato") ".csv")
                                       (fn [] nil)
                                       (fn [err] (state/toast-error! err)))
                   :variant :outline]
                  [common/button "Deletar" delete-championship!
                   :variant :danger :disabled @deleting?]])]

              (when (and authenticated (seq @seasons))
                [:div {:class "mt-3 flex flex-wrap items-center gap-3"}
                 [common/select-field
                  "Temporada ativa"
                  active-season-id
                  season-options
                  (fn [selected-season-id]
                    (api/activate-season
                     selected-season-id
                     (fn [_result] (load!))
                     (fn [err]
                       (let [msg (str "Erro ao ativar temporada: " err)]
                         (reset! error msg)
                         (state/toast-error! msg)))))
                  :container-class "min-w-[260px]"]])

              [:div {:class "grid gap-4 md:grid-cols-2"}
               [common/card
                [:h3 {:class "app-section-title"} "Detalhes"]
                [:div {:class "mt-3 space-y-2 text-sm text-slate-600"}
                 [:p [:span {:class "font-medium text-slate-800"} "Temporada (ativa / última visão): "] (api-get ch :season)]
                 [:p [:span {:class "font-medium text-slate-800"} "Formato: "] (api-get ch :format)]
                 [:p [:span {:class "font-medium text-slate-800"} "Títulos (temporada ativa): "] (or (api-get ch :titles-count) 0)]
                 [:p [:span {:class "font-medium text-slate-800"} "Total de títulos (todas as temporadas): "]
                  (or (api-get ch :total-titles-across-seasons) 0)]
                 (when (some? (:max capacity))
                   [:p [:span {:class "font-medium text-slate-800"} "Limite de jogadores: "]
                    [:span {:class "tabular-nums"} (:label capacity)]
                    (when at-limit?
                      [:span {:class "ml-2 text-amber-600 dark:text-amber-400"} "(limite atingido)"])])
                 (when-let [location (api-get ch :location)]
                   [:p [:span {:class "font-medium text-slate-800"} "Local: "] location])
                 (when-let [notes (api-get ch :notes)]
                   [:p [:span {:class "font-medium text-slate-800"} "Notas: "] notes])
                 [:div {:class "mt-4 space-y-3 border-t border-slate-200 pt-4 dark:border-slate-700"}
                  [:h4 {:class "text-sm font-semibold text-slate-800"} "Temporadas"]
                  (when authenticated
                    [:<>
                     [common/input-field
                      "Nova Temporada"
                      @new-season-label
                      #(reset! new-season-label %)
                      :placeholder "Ex: 2026"]
                     [common/button
                      "Criar temporada"
                      (fn []
                        (let [v (str/trim @new-season-label)]
                          (when-not (str/blank? v)
                            (api/create-season
                             id
                             {:season v :status "inactive"}
                             (fn [_result]
                               (reset! new-season-label "")
                               (load!))
                             (fn [err]
                               (let [msg (str "Erro ao criar temporada: " err)]
                                 (reset! error msg)
                                 (state/toast-error! msg)))))))
                      :variant :outline]])
                  (when (seq @seasons)
                    (let [sorted-seasons (sort-by (fn [s] (str (:season s))) @seasons)
                          season-actions
                          (fn [s]
                            (let [sid (str (:_id s))
                                  is-active? (= sid (str active-season-id))]
                              [:div {:class "flex flex-wrap gap-2"}
                               [common/button
                                "Ver"
                                #(rfe/push-state :championship-season-detail {:id id :season-id sid})
                                :variant :outline
                                :class "min-h-[44px]"]
                               (when authenticated
                                 (if is-active?
                                   [common/badge "Ativa" :variant :success]
                                   [common/button
                                    "Ativar"
                                    (fn []
                                      (api/activate-season
                                       sid
                                       (fn [_] (load!))
                                       (fn [err]
                                         (let [msg (str "Erro ao ativar temporada: " err)]
                                           (reset! error msg)
                                           (state/toast-error! msg)))))
                                    :variant :outline
                                    :class "min-h-[44px]"]))]))]
                      [:div {:class "mt-2"}
                       [:div {:class "space-y-2 lg:hidden"}
                        (doall
                         (for [s sorted-seasons]
                           (let [sid (str (:_id s))
                                 s-status (or (:status s) "inactive")
                                 enrolled-count (count (or (:enrolled-player-ids s) []))
                                 match-n (count (or (:match-ids s) []))
                                 titles (or (:titles-count s) 0)
                                 go-season! #(rfe/push-state :championship-season-detail
                                                             {:id id :season-id sid})]
                             ^{:key sid}
                             [:div {:class "cursor-pointer rounded-lg border border-slate-200 p-3 dark:border-slate-700"
                                    :role "link"
                                    :tab-index 0
                                    :on-click go-season!
                                    :on-key-down (fn [e]
                                                   (when (#{"Enter" " "} (.-key e))
                                                     (.preventDefault e)
                                                     (go-season!)))}
                              [:div {:class "flex items-start justify-between gap-2"}
                               [:p {:class "font-semibold text-slate-900 dark:text-slate-100"} (:season s)]
                               [common/badge (common/status-label s-status)
                                :variant (common/status-variant s-status)]]
                              [:p {:class "mt-2 text-xs tabular-nums text-slate-500"}
                               (str enrolled-count " inscritos · " match-n " partidas · " titles " títulos")]
                              [:div {:class "mt-3"
                                     :on-click #(.stopPropagation %)}
                               (season-actions s)]])))]
                       [:div {:class "hidden lg:block"}
                        [common/table
                         ["Temporada" "Detalhes" "Status" "Inscritos" "Partidas" "Títulos" "Ações"]
                         (map (fn [s]
                                (let [s-status (or (:status s) "inactive")
                                      enrolled-count (count (or (:enrolled-player-ids s) []))
                                      match-n (count (or (:match-ids s) []))
                                      titles (or (:titles-count s) 0)
                                      detail-parts (filter seq
                                                        [(or (:format s) "")
                                                         (when (:start-date s) (str "Início: " (:start-date s)))
                                                         (when (:end-date s) (str "Fim: " (:end-date s)))])]
                                  [(:season s)
                                   [:span {:class "text-xs text-slate-600"} (if (seq detail-parts)
                                                                              (str/join " · " detail-parts)
                                                                              "—")]
                                   [common/badge (common/status-label s-status)
                                    :variant (common/status-variant s-status)]
                                   enrolled-count
                                   match-n
                                   titles
                                   (season-actions s)]))
                              sorted-seasons)
                         :dense? true
                         :show-search? false
                         :sortable? true
                         :row-data sorted-seasons
                         :on-row-click (fn [s]
                                         (when-let [sid (normalize-id (:_id s))]
                                           (rfe/push-state :championship-season-detail
                                                           {:id id :season-id sid})))]]]))]]]
               [common/card
                [:h3 {:class "app-section-title"} "Partidas"]
                (if (seq @matches)
                  [common/table
                   ["Data" "Adversário" "Local" "Resultado"]
                   (map (fn [match]
                          [(or (common/format-match-calendar-date (:date match)) "—")
                           (:opponent match)
                           (:venue match)
                           (common/format-match-result (:result match))])
                        @matches)
                   :sortable? true
                   :dense? true
                   :row-data @matches
                   :on-row-click (fn [match]
                                   (when-let [mid (normalize-id (:_id match))]
                                     (rfe/push-state :match-detail {:id mid})))]
                  [:p {:class "app-muted"} "Nenhuma partida encontrada"])]]

              (let [lb (or @leaderboards {})]
                [common/card
                 [:h3 {:class "app-section-title"} "Top 5 — soma das temporadas"]
                 [:p {:class "text-xs text-slate-500 mt-1 mb-3"}
                    "Com base em gols, assistências, jogos e títulos agregados nas estatísticas por campeonato/temporada (ex.: BASE_DADOS)."]
                 [:div {:class "grid gap-4 sm:grid-cols-2 xl:grid-cols-4"}
                  [leaderboard-mini "Artilheiros" :goals (:top-goals lb)]
                  [leaderboard-mini "Assistências" :assists (:top-assists lb)]
                  [leaderboard-mini "Partidas" :games (:top-games lb)]
                  [leaderboard-mini "Títulos" :titles (:top-titles lb)]]])

              [common/card
               [:div {:class "flex flex-wrap items-center justify-between gap-2"}
                [:h3 {:class "app-section-title"} "Inscrições no Campeonato"]
                [:div {:class "flex flex-wrap items-center gap-2"}
                 [:span {:class (common/merge-classes
                                 "inline-flex items-center rounded-md border px-2.5 py-1 text-sm font-semibold tabular-nums"
                                 (if at-limit?
                                   "border-amber-300 bg-amber-500/10 text-amber-800 dark:border-amber-500/30 dark:text-amber-300"
                                   "border-brand-maroon/20 bg-brand-maroon/10 text-brand-maroon"))
                         :aria-label (ui-copy/enrollment-counter-label (:label capacity))}
                  (:label capacity) " inscritos"]
                 [enrollment-progress-bar capacity]
                 (when authenticated
                   [:<>
                    [common/button ui-copy/batch-enroll-button
                     #(reset! batch-enroll-open? true)
                     :variant :secondary
                     :disabled at-limit?
                     :aria-label ui-copy/batch-enroll-button]
                    [common/button "Mesclar jogadores"
                     #(open-champ-merge! {:initial-ref nil})
                     :variant :outline]])]]
               [:div {:class "mt-3 space-y-3"}
                (when (and authenticated at-limit? (:max capacity))
                  [common/persistent-banner
                   (ui-copy/enrollment-limit-reached (long (:max capacity)))
                   :how-to-fix ui-copy/enrollment-limit-how-to-fix
                   :variant :warning])
                (when (and authenticated
                           (seq @seasons)
                           (str/blank? (str (or active-season-id ""))))
                  [common/persistent-banner
                   ui-copy/no-active-season-banner
                   :how-to-fix ui-copy/no-active-season-how-to-fix
                   :variant :warning])
                (when authenticated
                  [player-picker/player-search-add-panel
                   (cond-> {:label "Adicionar jogador"
                            :players @all-players
                            :players-loading? (not @players-catalog-ready?)
                            :exclude-ids enrolled-exclude-ids
                            :action-label "Inscrever"
                            :search-placeholder "Buscar jogador por nome..."
                            :disabled? at-limit?
                            :on-pick-player
                            (fn [player]
                              (when (and (enrollment/can-enroll?
                                          (count @enrolled-players)
                                          (api-get ch :max-players))
                                         (player-picker/player-id player))
                                (let [pid (player-picker/player-id player)]
                                  (api/enroll-player-in-championship
                                   id pid
                                   (fn [_result]
                                     (api/get-championship-players id
                                                                   on-enrolled-loaded
                                                                   (fn [e]
                                                                     (let [msg (str "Erro ao carregar inscritos: " e)]
                                                                       (reset! error msg)
                                                                       (state/toast-error! msg)))))
                                   (fn [err]
                                     (let [msg (str "Erro ao inscrever jogador: " err)]
                                       (reset! error msg)
                                       (state/toast-error! msg)))))))
                            :on-quick-create
                            (fn [name ok err]
                              (if-not (enrollment/can-enroll?
                                       (count @enrolled-players)
                                       (api-get ch :max-players))
                                (err (ui-copy/enrollment-limit-reached
                                      (or (:max capacity) (api-get ch :max-players))))
                                (api/create-player
                                 {:name name :position player-picker/quick-create-position}
                                 (fn [created]
                                   (if-let [pid (player-picker/player-id created)]
                                     (do
                                       (swap! all-players conj created)
                                       (api/enroll-player-in-championship
                                        id pid
                                        (fn [_result]
                                          (api/get-championship-players
                                           id
                                           (fn [rows]
                                             (on-enrolled-loaded rows)
                                             (ok created))
                                           (fn [e]
                                             (let [msg (str "Erro ao carregar inscritos: " e)]
                                               (reset! error msg)
                                               (state/toast-error! msg)
                                               (err msg)))))
                                        (fn [e]
                                          (let [msg (str "Erro ao inscrever novo jogador: " e)]
                                            (reset! error msg)
                                            (state/toast-error! msg)
                                            (err msg)))))
                                     (err "Jogador criado sem ID retornado.")))
                                 (fn [e]
                                   (err (str "Erro ao criar jogador: " e))))))}
                     at-limit?
                     (assoc :search-placeholder
                            (ui-copy/enrollment-limit-placeholder
                             (:enrolled capacity)
                             (long (:max capacity)))
                            :disabled-hint ui-copy/enrollment-limit-how-to-fix))])
                [enrolled-roster
                 {:players enrolled-sorted
                  :authenticated? authenticated
                  :dup-report @champ-dup-report
                  :teams teams
                  :on-remove remove-enrolled!
                  :on-merge #(open-champ-merge! {:initial-ref %})}]
               (when (and (seq winner-ids) (not authenticated))
                 [:div {:class "mt-4 border-t border-slate-200 pt-4 dark:border-slate-700"}
                  [:h4 {:class "text-sm font-semibold text-slate-800"} "Finalização do campeonato"]
                  [:p {:class "mt-2 text-xs text-slate-500"}
                   (str "Vencedores: " (if (seq winner-names) (str/join ", " winner-names) "—")
                        (when (pos? awarded-count) (str " • Títulos concedidos: " awarded-count)))]])
               (when authenticated
                 [:div {:class "mt-4 border-t border-slate-200 pt-4 dark:border-slate-700"}
                  [:h4 {:class "text-sm font-semibold text-slate-800"} "Finalização do campeonato"]
                  (cond
                    (not (common/status-active? (api-get ch :status)))
                    [:p {:class "mt-2 text-xs text-slate-500"} "Apenas campeonatos ativos podem ser finalizados."]

                    (seq winner-ids)
                    [:p {:class "mt-2 text-xs text-slate-500"}
                     (str "Vencedores: " (if (seq winner-names) (str/join ", " winner-names) "—")
                          (when (pos? awarded-count) (str " • Títulos concedidos: " awarded-count)))]

                    :else
                    (let [checklist (finalization/finalization-checklist
                                    {:status (api-get ch :status)
                                     :finished-at (api-get ch :finished-at)
                                     :enrolled-count (count @enrolled-players)
                                     :max-players (api-get ch :max-players)
                                     :titles-award-count @titles-award-count
                                     :winner-ids @selected-winners
                                     :match-count (count @matches)
                                     :reason @finalize-reason})
                          can-submit? (finalization/checklist-complete? checklist)]
                      [:div {:class "mt-3 space-y-3"}
                       [common/input-field "Títulos a conceder" @titles-award-count #(reset! titles-award-count %)
                        :type "number" :placeholder "1"]
                       (if (seq @enrolled-players)
                         [:div {:class "space-y-2"}
                          (for [player enrolled-sorted]
                            ^{:key (:_id player)}
                            [:label {:class "flex min-h-[44px] items-center gap-2 text-sm text-slate-700 dark:text-slate-200"}
                             [:input {:type "checkbox"
                                      :class "h-5 w-5"
                                      :checked (contains? @selected-winners (str (:_id player)))
                                      :on-change #(swap! selected-winners
                                                         (fn [current]
                                                           (let [pid (str (:_id player))]
                                                             (if (contains? current pid)
                                                               (disj current pid)
                                                               (conj current pid)))))}]
                             [:span (:name player)]])]
                         [:p {:class "text-xs text-slate-500"} "Inscreva jogadores antes de finalizar."])
                       [:div
                        [:label {:class "block text-sm font-medium text-slate-700 dark:text-slate-200"}
                         ui-copy/sensitive-reason-label]
                        [:p {:class "mt-1 text-xs text-slate-500"} ui-copy/sensitive-reason-hint]
                        [:textarea {:class "app-input mt-2 w-full min-h-[72px]"
                                    :placeholder ui-copy/finalize-reason-placeholder
                                    :value @finalize-reason
                                    :disabled @finalizing?
                                    :on-change #(reset! finalize-reason (.. % -target -value))}]]
                       [finalization-checklist-ui checklist]
                       [common/button (if @finalizing? "Finalizando..." "Finalizar campeonato")
                        (fn []
                          (when can-submit?
                            (reset! finalizing? true)
                            (api/finalize-championship
                             id
                             (vec @selected-winners)
                             (if (str/blank? @titles-award-count) 1 (js/parseInt @titles-award-count 10))
                             (fn [_result]
                               (reset! finalizing? false)
                               (reset! selected-winners #{})
                               (reset! titles-award-count "1")
                               (reset! finalize-reason "")
                               (load!))
                             (fn [err]
                               (reset! finalizing? false)
                               (let [msg (ui-copy/finalize-season-error err)]
                                 (reset! error msg)
                                 (state/toast-error! msg)))
                             :reason (finalization/normalize-reason @finalize-reason))))
                        :variant :primary
                        :disabled (or @finalizing? (not can-submit?))]]))])]]

              (when (and @batch-enroll-open? authenticated)
                [batch-enroll-modal
                 {:open? true
                  :on-close #(reset! batch-enroll-open? false)
                  :players @all-players
                  :exclude-ids enrolled-exclude-ids
                  :enrolled-count (count @enrolled-players)
                  :max-players (api-get ch :max-players)
                  :championship-id id
                  :on-enrolled-reload on-enrolled-loaded}])

              (when (and (:active @champ-merge-ui) authenticated)
                ^{:key (:tick @champ-merge-ui)}
                [merge-modal/merge-workflow-modal
                 {:championship-id id
                  :roster-players @enrolled-players
                  :initial-reference-id (:initial-ref @champ-merge-ui)
                  :on-close close-champ-merge!
                  :on-success (fn [_] (load!))}])]

             :else
             [:p {:class "app-muted"} "Campeonato não encontrado"])]))})))

(defn championship-season-detail [params]
  (let [season (r/atom nil)
        players (r/atom [])
        matches (r/atom [])
        loading? (r/atom true)
        error (r/atom nil)
        cid (:id params)
        sid (:season-id params)
        load! (fn []
                (reset! error nil)
                (reset! loading? true)
                (api/get-season sid
                                (fn [s]
                                  (reset! season s)
                                  (api/get-season-players sid
                                                          (fn [ps]
                                                            (reset! players ps)
                                                            (api/get-matches {:season-id sid}
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
                                                            (let [msg (str "Erro ao carregar inscritos: " err)]
                                                              (reset! error msg)
                                                              (state/toast-error! msg)))))
                                (fn [err resp]
                                  (reset! loading? false)
                                  (if (and resp (= 404 (:status resp)))
                                    (reset! error "Temporada não encontrada.")
                                    (let [msg (str "Erro ao carregar temporada: " err)]
                                      (reset! error msg)
                                      (state/toast-error! msg))))))]
    (r/create-class
     {:component-did-mount (fn [] (load!))
      :reagent-render
      (fn []
        (let [s-status (or (when @season (:status @season)) "inactive")
              raw (or (when @season (:status @season)) "indefinido")
              enrolled-sorted (sort-by #(str/lower-case (str (:name %))) @players)]
          [:div {:class "space-y-6"}
           [common/breadcrumb
            (breadcrumbs/build-breadcrumbs :championship-season-detail
                                           {:id cid :season-id sid}
                                           :championship-label (or (:championship-name @season) "Campeonato")
                                           :season-label (or (:season @season) "Temporada"))]
           (cond
             @error
             [:div [common/button "Tentar novamente" load! :variant :outline]]

             @loading?
             [common/skeleton-table ["Campo" "Valor"] :rows 4]

             @season
             [:<>
              [:div
               [:p {:class "text-sm text-slate-500"} "Temporada"]
               [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"}
                (str (:championship-name @season) " · " (:season @season))]
               [common/badge (common/status-label raw)
                :variant (common/status-variant s-status) :class "mt-2"]]
              [common/card
               [:h3 {:class "app-section-title"} "Detalhes"]
               [:div {:class "mt-3 space-y-2 text-sm text-slate-600"}
                [:p [:span {:class "font-medium text-slate-800"} "Formato: "] (or (:format @season) "—")]
                [:p [:span {:class "font-medium text-slate-800"} "Títulos (registro): "] (or (:titles-count @season) 0)]
                (when (:start-date @season)
                  [:p [:span {:class "font-medium text-slate-800"} "Início: "] (str (:start-date @season))])
                (when (:end-date @season)
                  [:p [:span {:class "font-medium text-slate-800"} "Fim: "] (str (:end-date @season))])
                (when (:finished-at @season)
                  [:p [:span {:class "font-medium text-slate-800"} "Finalizada em: "] (str (:finished-at @season))])]]
              [common/card
               [:h3 {:class "app-section-title"} (str "Inscritos (" (count @players) ")")]
               (if (seq @players)
                 [:ul {:class "mt-2 divide-y divide-slate-100 text-sm text-slate-700"}
                  (for [p enrolled-sorted]
                    (let [pid (normalize-id (or (:_id p) (:id p)))]
                      ^{:key pid}
                      [:li (cond-> {:class "py-2"}
                             pid (assoc :class "cursor-pointer py-2 hover:bg-slate-50 dark:hover:bg-slate-900/50"
                                        :role "link"
                                        :tab-index 0
                                        :on-click #(rfe/push-state :player-detail {:id pid})
                                        :on-key-down (fn [e]
                                                       (when (#{"Enter" " "} (.-key e))
                                                         (.preventDefault e)
                                                         (rfe/push-state :player-detail {:id pid})))))
                       (:name p)]))]
                 [:p {:class "app-muted"} "Nenhum jogador inscrito nesta temporada."])]
              [common/card
               [:h3 {:class "app-section-title"} "Partidas"]
               (if (seq @matches)
                 [common/table
                  ["Data" "Adversário" "Local" "Resultado"]
                  (map (fn [match]
                         [(or (common/format-match-calendar-date (:date match)) "—")
                          (:opponent match)
                          (:venue match)
                          (common/format-match-result (:result match))])
                       @matches)
                  :sortable? true
                  :dense? true
                  :row-data @matches
                  :on-row-click (fn [match]
                                  (when-let [mid (normalize-id (:_id match))]
                                    (rfe/push-state :match-detail {:id mid})))]
                 [:p {:class "app-muted"} "Nenhuma partida nesta temporada."])]]

             :else
             [:p {:class "app-muted"} "Temporada não encontrada."])]))})))

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
                           :titles-count ""
                           :max-players ""})
        submitting? (r/atom false)
        form-error (r/atom nil)
        field-errors (r/atom {})
        valid-form? (fn []
                      (let [tc (when-not (str/blank? (:titles-count @form-data))
                                 (js/parseInt (:titles-count @form-data) 10))
                            mp (when-not (str/blank? (:max-players @form-data))
                                 (js/parseInt (:max-players @form-data) 10))
                            errs (cond-> {}
                                   (str/blank? (:name @form-data)) (assoc :name "Nome é obrigatório")
                                   (str/blank? (:season @form-data)) (assoc :season "Temporada é obrigatória")
                                   (and (some? tc) (js/isNaN tc)) (assoc :titles-count "Títulos deve ser um número")
                                   (and (number? tc) (not (js/isNaN tc)) (< tc 0)) (assoc :titles-count "Títulos não pode ser negativo")
                                   (and (some? mp) (js/isNaN mp)) (assoc :max-players "Limite de jogadores deve ser um número")
                                   (and (number? mp) (not (js/isNaN mp)) (< mp 0)) (assoc :max-players "Limite de jogadores não pode ser negativo"))]
                        (when (seq errs) errs)))
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
                                      {:titles-count (js/parseInt tc 10)}))
                                  (when-let [mp (:max-players @form-data)]
                                    (when-not (str/blank? mp)
                                      {:max-players (js/parseInt mp 10)})))))
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
                                                                        :titles-count (if (:titles-count result) (str (:titles-count result)) "")
                                                                        :max-players (if (:max-players result) (str (:max-players result)) "")})
                                                     (reset! championship-loading? false))
                                                   (fn [err]
                                                     (let [msg (str "Erro ao carregar campeonato: " err)]
                                                       (reset! form-error msg)
                                                       (state/toast-error! msg))
                                                     (reset! championship-loading? false)))))]
    (r/create-class
     {:component-did-mount load-championship!
      :reagent-render
      (fn []
        [:div {:class "space-y-6"}
         [common/breadcrumb
          (breadcrumbs/build-breadcrumbs (if is-edit? :championship-edit :championship-new)
                                         (when is-edit? {:id id})
                                         :entity-label (or (:name @form-data)
                                                           (if is-edit? "Campeonato" "Novo")))]
         [:div
          [:p {:class "text-sm text-slate-500"} "Cadastro"]
          [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} (if is-edit? "Editar Campeonato" "Novo Campeonato")]]
         (if @championship-loading?
           [common/skeleton-table ["Campo" "Valor"] :rows 5]
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
                                                      (effects/ensure-championships! {:force? true})
                                                      (rfe/push-state :championships))
                                          on-error (fn [error]
                                                    (reset! submitting? false)
                                                    (let [msg (str "Erro ao " (if is-edit? "atualizar" "criar") " campeonato: " error)]
                                                      (reset! form-error msg)
                                                      (state/toast-error! msg)))]
                                      (if is-edit?
                                        (api/update-championship id payload on-success on-error)
                                        (api/create-championship payload on-success on-error))))))}
            [common/card
             [:h3 {:class "app-section-title"} "Informações principais"]
             [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
              [common/input-field "Nome" (:name @form-data) #(swap! form-data assoc :name %) :placeholder "Nome do campeonato" :required? true :error (:name @field-errors)]
              [common/input-field "Temporada" (:season @form-data) #(swap! form-data assoc :season %) :placeholder "Ex: 2024" :required? true :error (:season @field-errors)]
              [common/input-field "Títulos" (:titles-count @form-data) #(swap! form-data assoc :titles-count %) :type "number" :placeholder "0" :error (:titles-count @field-errors)]
              [common/input-field "Limite de jogadores" (:max-players @form-data) #(swap! form-data assoc :max-players %) :type "number" :placeholder "Sem limite" :error (:max-players @field-errors)]
              [common/select-field "Status" (:status @form-data)
               [["" "Selecione um status"]
                ["active" "Ativo"]
                ["inactive" "Inativo"]
                ["completed" "Concluído"]
                ["cancelled" "Cancelado"]]
               #(swap! form-data assoc :status %)]
              [common/input-field "Formato" (:format @form-data) #(swap! form-data assoc :format %) :placeholder "Ex: Liga, Copa, etc."]
              [common/input-field "Local" (:location @form-data) #(swap! form-data assoc :location %) :placeholder "Local do campeonato"]]]

            [common/card
             [:h3 {:class "app-section-title"} "Datas e observações"]
             [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
              [common/input-field "Data de Início" (:start-date @form-data) #(swap! form-data assoc :start-date %) :type "date"]
              [common/input-field "Data de Término" (:end-date @form-data) #(swap! form-data assoc :end-date %) :type "date"]
              [common/input-field "Notas" (:notes @form-data) #(swap! form-data assoc :notes %) :placeholder "Observações adicionais" :container-class "md:col-span-2"]]]

            [:div {:class "flex flex-wrap gap-2"}
             [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar"))
              nil
              :type "submit"
              :disabled @submitting?
              :variant :primary]
             [common/button "Cancelar" #(rfe/push-state :championships) :variant :outline]]])])})))

