(ns galaticos.components.common
  "Shared/common UI components.

  Design system (UX-PLAN-01) — Tailwind tokens:
  - color-brand: `brand-maroon` (primary actions, links)
  - color-surface-page: `bg-slate-50` / `dark:bg-slate-950`
  - color-surface-card: `app-card` — white + `shadow-sm`; dark `border-slate-800` `bg-slate-900`
  - color-surface-muted: `bg-slate-50` thead / toolbar strips
  - spacing grid: 8px (Tailwind 2 = 8px: p-2, gap-2, py-3, etc.)
  - radius: cards `rounded-lg`, inputs `rounded-md`, badges `rounded-full`
  - typography stats: `tabular-nums` on numeric table columns via `:numeric-columns`
  - status badges: `badge` variants (:success :warning :danger :info :maroon)"
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [reitit.frontend.easy :as rfe]
            [galaticos.routes :as routes]
            [galaticos.ui-copy :as ui-copy]))

(defn merge-classes
  "Join non-blank Tailwind class strings."
  [& classes]
  (->> classes
       (filter #(and % (not (str/blank? %))))
       (str/join " ")))

(def ^:private input-base-class
  "w-full rounded-md border bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:placeholder:text-slate-500")

(def ^:private badge-variant-styles
  {:success "bg-emerald-100 text-emerald-700 border border-transparent dark:bg-emerald-500/10 dark:text-emerald-400 dark:border-emerald-500/20"
   :warning "bg-amber-100 text-amber-700 border border-transparent dark:bg-amber-500/10 dark:text-amber-400 dark:border-amber-500/20"
   :info "bg-sky-100 text-sky-700 border border-transparent dark:bg-sky-500/10 dark:text-sky-400 dark:border-sky-500/20"
   :danger "bg-rose-100 text-rose-700 border border-transparent dark:bg-rose-500/10 dark:text-rose-400 dark:border-rose-500/20"
   :maroon "bg-brand-maroon/15 text-brand-maroon border border-transparent dark:bg-brand-maroon/25 dark:text-brand-maroon/90 dark:border-brand-maroon/30"})

(defn format-match-calendar-date
  "Format ISO match :date using the UTC calendar day (midnight UTC otherwise shows as prior local day)."
  [d]
  (when d
    (.toLocaleDateString (js/Date. d) js/undefined (clj->js {:timeZone "UTC"}))))

(defn match-date-for-input
  "Value for HTML `<input type=\"date\">` from API :date (ISO instant or YYYY-MM-DD)."
  [d]
  (when d
    (let [s (str d)]
      (when-not (str/blank? s)
        (if (>= (count s) 10)
          (subs s 0 10)
          s)))))

(defn loading-spinner []
  [:div {:class "flex items-center justify-center gap-3 py-10 text-slate-500 dark:text-slate-400"}
   [:div {:class "h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-brand-maroon dark:border-slate-600"}]
   [:span "Carregando..."]])

(defn delayed-loading-panel
  "Spinner first; after `delay-ms` still loading → `skeleton` (UX-PLAN-17)."
  [loading? skeleton & children]
  (let [delay-ms 1000
        show-skeleton? (r/atom false)
        alive? (r/atom true)]
    (r/create-class
     {:component-did-mount
      (fn []
        (when loading?
          (reset! show-skeleton? false)
          (js/setTimeout
           (fn []
             (when (and @alive? loading?)
               (reset! show-skeleton? true)))
           delay-ms)))
      :component-did-update
      (fn [_ _ [_ prev-loading?] loading?]
        (when (and loading? (not prev-loading?))
          (reset! show-skeleton? false)
          (js/setTimeout
           (fn []
             (when (and @alive? loading?)
               (reset! show-skeleton? true)))
           delay-ms))
        (when-not loading?
          (reset! show-skeleton? false)))
      :component-will-unmount #(reset! alive? false)
      :reagent-render
      (fn [loading? skeleton & children]
        (cond
          (not loading?) (into [:<>] children)
          @show-skeleton? skeleton
          :else [loading-spinner]))})))

(defn skeleton-line
  "Single pulsing placeholder bar (UX-PLAN-18)."
  [& {:keys [class]}]
  [:div {:class (merge-classes "h-4 animate-pulse rounded-md bg-slate-200 dark:bg-slate-700" class)}])

(defn skeleton-table
  "Placeholder table while list data loads (UX-PLAN-04)."
  [headers & {:keys [rows class]}]
  (let [row-count (or rows 5)
        cols (count headers)]
    [:div {:class (merge-classes "overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900" class)}
     [:table {:class "min-w-full divide-y divide-slate-200 dark:divide-slate-700"}
      [:thead {:class "bg-slate-50 dark:bg-slate-800/80"}
       [:tr
        (for [idx (range cols)]
          ^{:key idx}
          [:th {:class "px-4 py-3 text-left"}
           [skeleton-line :class "w-20"]])]]
      [:tbody {:class "divide-y divide-slate-100 dark:divide-slate-800"}
       (for [r (range row-count)]
         ^{:key r}
         [:tr
          (for [c (range cols)]
            ^{:key c}
            [:td {:class "px-4 py-3"}
             [skeleton-line :class (if (zero? c) "w-32" "w-16")]])])]]]))

(defn alert [message & {:keys [variant class]}]
  (let [variant (or variant :error)
        styles (case variant
                 :success "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800/50 dark:bg-emerald-950/40 dark:text-emerald-200"
                 :warning "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-800/50 dark:bg-amber-950/40 dark:text-amber-200"
                 :info "border-sky-200 bg-sky-50 text-sky-700 dark:border-sky-800/50 dark:bg-sky-950/40 dark:text-sky-200"
                 "border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800/50 dark:bg-rose-950/40 dark:text-rose-200")]
    [:div {:class (merge-classes "rounded-xl border px-4 py-3 text-sm" styles class)}
     message]))

(defn error-message [message]
  [alert message :variant :error])

(defn button
  "Action button. Variants: :primary :secondary :outline :ghost :danger.
   States: hover/focus-visible ring, disabled, loading? (spinner + aria-busy).
   :modal-cancel? — initial focus target in modal (UX-PLAN-19)."
  [text on-click & {:keys [class style disabled type variant aria-label loading? loading-label modal-cancel?]}]
  (let [variant (or variant :secondary)
        show-loading? (boolean loading?)
        disabled* (or disabled show-loading?)
        base (merge-classes
              "inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition"
              "focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-maroon/40"
              "disabled:cursor-not-allowed disabled:opacity-60")
        styles (case variant
                 :primary "bg-brand-maroon text-white hover:bg-[#6b0000] focus-visible:ring-brand-maroon/50"
                 :danger "bg-rose-600 text-white hover:bg-rose-700"
                 :outline "border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
                 :ghost "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                 "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700")
        label (if show-loading? (or loading-label text) text)]
    [:button (merge {:on-click on-click
                     :class (merge-classes base styles class (when modal-cancel? "modal-cancel"))
                     :type (or type "button")
                     :disabled disabled*
                     :style style
                     :aria-busy (when show-loading? "true")}
                    (when aria-label {:aria-label aria-label}))
     (when show-loading?
       [:span {:class "h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-white/40 border-t-white"
               :aria-hidden "true"}])
     label]))

(defn not-found-resource
  "Shown when a detail resource (player, championship, team) is not found (404).
   message: e.g. 'Jogador não encontrado.'
   on-back: callback to navigate to list (e.g. #(rfe/push-state :players))"
  [message on-back]
  [:div {:class "space-y-4"}
   [alert message :variant :warning]
   [button "Voltar" on-back :variant :outline]
   [:p {:class "text-sm text-slate-500 dark:text-slate-400"} "Outros destinos:"]
   [:div {:class "flex flex-wrap gap-2"}
    [button "Dashboard" #(rfe/push-state :dashboard) :variant :ghost]
    [button "Jogadores" #(rfe/push-state :players) :variant :ghost]
    [button "Partidas" #(rfe/push-state :matches) :variant :ghost]]])

(defn breadcrumb
  "Trail of {:label :route :route-params?}. Last segment or items without :route render as text."
  [items]
  (let [segments (vec items)]
    [:nav {:aria-label "Breadcrumb"
           :class "flex flex-wrap items-center gap-1 text-sm text-slate-500 dark:text-slate-400"}
     (doall
      (for [[idx {:keys [label route route-params]}] (map-indexed vector segments)
            :let [last? (= idx (dec (count segments)))]]
        ^{:key idx}
        [:span {:class "inline-flex items-center gap-1"}
         (when (pos? idx)
           [:span {:aria-hidden "true" :class "text-slate-300 dark:text-slate-600"} "»"])
         (if (and route (not last?))
           [:a {:href (if route-params
                        (routes/href route route-params)
                        (routes/href route))
                :on-click (fn [e]
                            (.preventDefault e)
                            (if route-params
                              (rfe/push-state route route-params)
                              (rfe/push-state route)))
                :class "font-medium text-brand-maroon hover:underline dark:text-brand-maroon/90"}
            label]
           [:span {:class (when last? "font-medium text-slate-700 dark:text-slate-200")}
            label])]))]))

(defn card [& children]
  (into [:div {:class "app-card p-5"}] children))

(defn stat-card [label value & {:keys [delta icon class]}]
  [:div {:class (merge-classes "app-card p-5" class)}
   [:div {:class "flex items-start justify-between"}
    [:div
     [:p {:class "text-sm font-medium text-slate-500 dark:text-slate-400"} label]
     [:p {:class "mt-2 text-2xl font-semibold tabular-nums text-slate-900 dark:text-slate-100"} value]
     (when delta
       [:p {:class "mt-1 text-xs text-emerald-600"} delta])]
    (when icon
      [:div {:class "rounded-xl bg-brand-maroon/10 p-2 text-brand-maroon"} icon])]])

(defn badge
  "Status pill. Variants: :success :warning :danger :info :maroon (§2 dark semi-transparent + border)."
  [text & {:keys [variant class]}]
  (let [styles (get badge-variant-styles variant
                    "bg-slate-100 text-slate-700 border border-transparent dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700")]
    [:span {:class (merge-classes "rounded-full px-2.5 py-1 text-xs font-medium" styles class)}
     text]))

(defn- normalize-status-code
  [raw]
  (let [s (-> (str raw) str/trim str/lower-case)]
    (cond
      (str/blank? s) ""
      (= s "finished") "completed"
      :else s)))

(defn status-label
  "Rótulo em português para valores de status da API (chaves em inglês no banco)."
  [raw]
  (let [n (normalize-status-code raw)]
    (case n
      "" "-"
      "active" "Ativo"
      "inactive" "Inativo"
      "completed" "Finalizado"
      "cancelled" "Cancelado"
      "indefinido" "Indefinido"
      "pending" "Pendente"
      "scheduled" "Agendado"
      (if (str/blank? (str raw))
        "-"
        (str raw)))))

(defn status-variant
  "Variante visual do badge para o status (inglês normalizado)."
  [raw]
  (case (normalize-status-code raw)
    "active" :success
    "completed" :info
    "inactive" :warning
    "cancelled" :danger
    :info))

(defn status-active?
  "True se o status da entidade é active (após normalizar finished → completed)."
  [raw]
  (= "active" (normalize-status-code raw)))

(defn status-badge
  "Badge from API status string (listas principais)."
  [raw & {:keys [class]}]
  [badge (status-label raw) :variant (status-variant raw) :class class])

(defn championship-header-badge
  "Badge de status no detalhe do campeonato (UX-PLAN-14): Ativo maroon, Finalizado emerald, Pendente amber."
  [raw]
  (let [n (normalize-status-code raw)
        label (case n
                "active" "Ativo"
                "completed" "Finalizado"
                "pending" "Pendente"
                "" "-"
                (status-label raw))
        variant (case n
                  "active" :maroon
                  "completed" :success
                  "pending" :warning
                  "inactive" :warning
                  :info)]
    {:label label :variant variant}))

(defn format-match-result
  "Format match score for display. Prefers :result (our-score/opponent-score); falls back
   to :home-score/:away-score for UI-created matches. Returns e.g. \"3 x 1\" or \"-\"."
  [match-or-result]
  (let [match (when (and (map? match-or-result)
                         (or (contains? match-or-result :home-score)
                             (contains? match-or-result :away-score)
                             (contains? match-or-result "home-score")
                             (contains? match-or-result "away-score")))
               match-or-result)
        result (if match (:result match) match-or-result)
        our (or (when (map? result)
                (or (:our-score result) (get result "our-score")))
                (when match
                  (or (:home-score match) (get match "home-score"))))
        opp (or (when (map? result)
                (or (:opponent-score result) (get result "opponent-score")))
                (when match
                  (or (:away-score match) (get match "away-score"))))]
    (if (and (some? our) (some? opp))
      (str our " x " opp)
      "-")))

(defonce field-id-counter (atom 0))

(defn- field-error-hint [field-id error]
  (when error
    [:p {:id (str field-id "-error")
         :class "text-xs text-rose-600 dark:text-rose-400"
         :role "alert"}
     error]))

(defn- field-hint [field-id hint]
  (when hint
    [:p {:id (str field-id "-hint")
         :class "text-xs text-slate-500 dark:text-slate-400"}
     hint]))

(defn input-field
  "Labeled text input. Optional :hint, :error, :on-blur; aria-describedby links hint/error."
  [label value on-change & {:keys [type placeholder class container-class required? error hint id on-blur readonly?]}]
  (let [field-id (or id (str "field-" (swap! field-id-counter inc)))
        is-date? (= type "date")
        border-class (cond
                      error "border-rose-500 focus:border-rose-500 focus:ring-rose-500/20"
                      readonly? "border-slate-200 bg-slate-100 text-slate-600 cursor-not-allowed dark:border-slate-700 dark:bg-slate-800/80 dark:text-slate-400"
                      :else "border-slate-300 focus:border-brand-maroon focus:ring-brand-maroon/20")]
    [:div {:class (merge-classes "space-y-2" container-class)}
     [:label {:for field-id
              :class (merge-classes "text-sm font-medium"
                                    (if error "text-rose-700 dark:text-rose-300" "text-slate-700 dark:text-slate-200"))}
      (str label (when required? " *"))]
     [:input (merge {:type (or type "text")
                     :id field-id
                     :placeholder placeholder
                     :on-change #(when-not readonly? (on-change (-> % .-target .-value)))
                     :read-only readonly?
                     :class (merge-classes input-base-class border-class class)}
                   (if is-date?
                     {:default-value value}
                     {:value value})
                   (when on-blur {:on-blur on-blur})
                   (when (or error hint)
                     {:aria-describedby (str/join " "
                                                  (remove str/blank?
                                                          [(when hint (str field-id "-hint"))
                                                           (when error (str field-id "-error"))]))})
                   (when error {:aria-invalid "true"}))]
     [field-error-hint field-id error]
     [field-hint field-id hint]]))

(defn readonly-field
  "Non-editable context field (UX-PLAN-05): distinct from inputs."
  [label value & {:keys [container-class id]}]
  (let [field-id (or id (str "readonly-" (swap! field-id-counter inc)))]
    [:div {:class (merge-classes "space-y-2" container-class)}
     [:label {:for field-id :class "text-sm font-medium text-slate-500 dark:text-slate-400"} label]
     [:p {:id field-id
          :class (merge-classes input-base-class "border-slate-200 bg-slate-100 text-slate-800 dark:border-slate-700 dark:bg-slate-800/80")}
      (if (str/blank? (str value)) "-" (str value))]]))

(defn select-field [label value options on-change & {:keys [class container-class required? error id]}]
  (let [field-id (or id (str "field-" (swap! field-id-counter inc)))
        border-class (if error
                      "border-rose-500 focus:border-rose-500 focus:ring-rose-500/20"
                      "border-slate-300 focus:border-brand-maroon focus:ring-brand-maroon/20")]
    [:div {:class (merge-classes "space-y-2" container-class)}
     [:label {:for field-id
              :class (merge-classes "text-sm font-medium" (if error "text-rose-700 dark:text-rose-300" "text-slate-700 dark:text-slate-200"))}
      (str label (when required? " *"))]
     (into [:select (merge {:value value
                            :id field-id
                            :on-change #(on-change (-> % .-target .-value))
                            :class (merge-classes input-base-class border-class class)}
                          (when error {:aria-invalid "true"
                                       :aria-describedby (str field-id "-error")}))]
           (map (fn [[opt-value opt-label]]
                  [:option {:key opt-value :value opt-value} opt-label])
                options))
     [field-error-hint field-id error]]))

(defn form-error-summary
  "Top-of-form summary when multiple field errors exist (UX-PLAN-05)."
  [field-errors & {:keys [class]}]
  (when (> (count field-errors) 1)
    [:div {:class (merge-classes "rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 dark:border-rose-800/50 dark:bg-rose-950/40" class)
           :role "alert"}
     [:p {:class "text-sm font-medium text-rose-800 dark:text-rose-200"} ui-copy/form-field-summary-title]
     [:ul {:class "mt-2 list-disc space-y-1 pl-5 text-sm text-rose-700 dark:text-rose-300"}
      (for [[k msg] (sort-by key field-errors)]
        ^{:key k}
        [:li msg])]]))

(defn form-conflict-alert
  "Persistent 409 conflict banner; optional link to existing resource."
  [message & {:keys [action-label on-action class]}]
  [:div {:class (merge-classes "rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 dark:border-amber-800/50 dark:bg-amber-950/40" class)
         :role "alert"}
   [:p {:class "text-sm text-amber-900 dark:text-amber-100"} message]
   (when (and action-label on-action)
     [:button {:type "button"
               :class "mt-2 text-sm font-semibold text-brand-maroon hover:underline dark:text-brand-maroon/90"
               :on-click on-action}
      action-label])])

(defn submit-button
  "Primary submit with loading label shown immediately (UX-PLAN-05)."
  [label on-click & {:keys [saving? saving-label disabled variant class type]}]
  (let [show-saving? (boolean saving?)
        text (if show-saving? (or saving-label "A guardar…") label)]
    [button text on-click
     :type (or type "submit")
     :variant (or variant :primary)
     :disabled (or disabled show-saving?)
     :class class
     :aria-busy show-saving?]))

(defn- field-name-in-message? [msg-lc field-kw]
  (let [kn (name field-kw)
        variants [kn (str/replace kn "-" "_") (str/replace kn "-" " ")]]
    (some #(str/includes? msg-lc %) variants)))

(defn field-errors-from-api
  "Map API error message to field keys when the message mentions them."
  [message field-keys]
  (when-not (str/blank? (str message))
    (let [msg-lc (str/lower-case (str message))
          matched (into {}
                        (keep (fn [k]
                                (when (field-name-in-message? msg-lc k)
                                  [k message]))
                              field-keys))]
      (if (seq matched)
        matched
        {:_form message}))))

(defn apply-form-api-error!
  "Set field-errors / form-error / conflict from API response without clearing form-data."
  [{:keys [message response field-keys field-errors form-error conflict]}]
  (let [status (:status response)
        body (:body response)
        body-msg (when (map? body) (or (:error body) (:message body)))
        msg (or message body-msg ui-copy/form-save-error)]
    (reset! conflict nil)
    (if (= status 409)
      (do
        (reset! field-errors {})
        (reset! form-error nil)
        (reset! conflict {:message msg
                          :resource-id (when (map? body)
                                         (or (:id body) (:resource-id body) (:match-id body)))}))
      (let [parsed (field-errors-from-api msg field-keys)
            field-only (dissoc parsed :_form)]
        (if (:_form parsed)
          (do
            (reset! field-errors field-only)
            (reset! form-error (:_form parsed)))
          (do
            (reset! field-errors parsed)
            (reset! form-error (when (> (count parsed) 1) msg))))))))

(defn list-toolbar
  "Consistent list toolbar: search, filters, count, clear (UX-PLAN-06)."
  ([] (list-toolbar {}))
  ([{:keys [search on-search-change search-placeholder search-id filtering?
            filter-controls result-count on-clear clear-disabled? extra-actions class]}]
  [:div {:class (merge-classes "flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-800/50 sm:flex-row sm:flex-wrap sm:items-end" class)}
   [:div {:class "flex min-w-0 flex-1 flex-col gap-2 sm:max-w-md"}
    [:label {:for (or search-id "list-search")
             :class "text-xs font-medium text-slate-500 dark:text-slate-400"} "Buscar"]
    [:div {:class "relative"}
     [:input {:type "search"
              :id (or search-id "list-search")
              :value search
              :placeholder (or search-placeholder "Buscar...")
              :on-change #(when on-search-change
                            (on-search-change (-> % .-target .-value)))
              :class (merge-classes input-base-class "focus:border-brand-maroon focus:ring-brand-maroon/20 py-2 pl-3 pr-24")}]
     (when filtering?
       [:span {:class "pointer-events-none absolute inset-y-0 right-2 flex items-center text-xs text-slate-500 dark:text-slate-400"
               :aria-live "polite"}
        "A filtrar…"])]]
   (when (seq filter-controls)
     [:div {:class "flex flex-wrap items-end gap-3"}
      filter-controls])
   [:div {:class "flex flex-wrap items-center gap-2 sm:ml-auto"}
    (when (some? result-count)
      [:p {:class "text-sm tabular-nums text-slate-600 dark:text-slate-300"
           :aria-live "polite"}
       (str result-count " encontrado(s)")])
    (when on-clear
      [button "Limpar" on-clear
       :variant :ghost
       :disabled clear-disabled?
       :class "text-sm"])
    extra-actions]]))

(defn skeleton-score-header
  "Placar + adversário placeholder while match form loads (UX-PLAN-08)."
  []
  [:div {:class "flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900/90 sm:flex-row sm:items-center sm:justify-between"}
   [:div {:class "space-y-2 flex-1"}
    [skeleton-line :class "h-6 w-48"]
    [skeleton-line :class "h-4 w-32"]]
   [:div {:class "flex gap-4"}
    [skeleton-line :class "h-10 w-24"]
    [skeleton-line :class "h-10 w-16"]]])

(defn number-stepper
  "Stepper with +/- buttons for incrementing/decrementing numeric values.
   :touch? uses 44px hit targets (UX-PLAN-10)."
  [value on-change & {:keys [min-val max-val touch? class]}]
  (let [min-v (or min-val 0)
        size (if touch? 44 28)
        current (if (number? value) value 0)
        can-dec? (> current min-v)
        can-inc? (or (nil? max-val) (< current max-val))
        btn-style {:width (str size "px")
                   :height (str size "px")
                   :border-radius "50%"
                   :min-width (str size "px")
                   :min-height (str size "px")}]
    [:div {:class (merge-classes "inline-flex items-center gap-1" class)}
     [:button {:type "button"
               :style (merge btn-style
                             (if can-dec?
                               {:background-color "#e11d48"}
                               {:background-color "#f1f5f9" :color "#cbd5e1" :cursor "not-allowed"}))
               :class "flex items-center justify-center text-sm font-medium text-white transition hover:opacity-80"
               :disabled (not can-dec?)
               :on-click #(when can-dec? (on-change (dec current)))}
      "-"]
     [:span {:class "w-8 text-center text-sm font-medium text-slate-900 dark:text-slate-100"}
      current]
     [:button {:type "button"
               :style (merge btn-style
                             (if can-inc?
                               {:background-color "#059669"}
                               {:background-color "#f1f5f9" :color "#cbd5e1" :cursor "not-allowed"}))
               :class "flex items-center justify-center text-sm font-medium text-white transition hover:opacity-80"
               :disabled (not can-inc?)
               :on-click #(when can-inc? (on-change (inc current)))}
      "+"]]))

(defn checkbox-field
  "Checkbox with label."
  [checked? on-change & {:keys [label id]}]
  (let [field-id (or id (str "checkbox-" (swap! field-id-counter inc)))]
    [:label {:for field-id
             :class "inline-flex cursor-pointer items-center gap-2"}
     [:input {:type "checkbox"
              :id field-id
              :checked (boolean checked?)
              :on-change #(on-change (-> % .-target .-checked))
              :class "h-4 w-4 rounded border-slate-300 text-brand-maroon focus:ring-2 focus:ring-brand-maroon/40 dark:border-slate-600 dark:bg-slate-800"}]
     (when label
       [:span {:class "text-sm text-slate-700 dark:text-slate-200"} label])]))

(defn focus-modal-cancel! [panel-el]
  (when panel-el
    (if-let [cancel (.querySelector panel-el ".modal-cancel")]
      (.focus cancel)
      (when-let [buttons (.querySelectorAll panel-el "button")]
        (when-let [first-btn (aget buttons 0)]
          (.focus first-btn))))))

(defn- modal-focus-trap! [panel-el e]
  (let [key (.-key e)
        shift? (.-shiftKey e)]
    (cond
      (= key "Escape")
      nil
      (= key "Tab")
      (when-let [nodes (seq (vec (.querySelectorAll panel-el
                                                    "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])")))]
        (let [first (first nodes)
              last (aget nodes (dec (count nodes)))]
          (when (or (and shift? (= (.-activeElement js/document) first))
                    (and (not shift?) (= (.-activeElement js/document) last)))
            (.preventDefault e)
            (if shift?
              (.focus last)
              (.focus first))))))))

(defn modal
  "Accessible dialog: Esc closes, Tab cycles inside, initial focus on .modal-cancel (UX-PLAN-19)."
  [{:keys [title content on-close actions]}]
  (let [title-id "modal-title"
        panel-ref (atom nil)
        overlay-ref (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn []
        (when-let [panel @panel-ref]
          (focus-modal-cancel! panel))
        (when-let [ov @overlay-ref]
          (.focus ov)))
      :reagent-render
      (fn [{:keys [title content on-close actions]}]
        [:div {:ref #(reset! overlay-ref %)
               :class "fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 dark:bg-black/50"
               :role "dialog"
               :aria-modal "true"
               :aria-labelledby title-id
               :tab-index -1
               :on-key-down (fn [e]
                              (when (= "Escape" (.-key e))
                                (.preventDefault e)
                                (on-close))
                              (when-let [panel @panel-ref]
                                (modal-focus-trap! panel e)))}
         [:div {:ref #(reset! panel-ref %)
                :class "app-card w-full max-w-lg p-6"
                :on-click #(.stopPropagation %)}
          [:div {:class "flex items-center justify-between"}
           [:h3 {:id title-id :class "text-lg font-semibold text-slate-900 dark:text-slate-100"} title]
           [:button {:type "button"
                     :class "text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
                     :on-click on-close
                     :aria-label "Fechar"} "×"]]
          [:div {:class "mt-4 text-sm text-slate-600 dark:text-slate-300"} content]
          [:div {:class "mt-6 flex justify-end gap-2"} actions]]])})))

(defn list-hover-card
  "List row wrapper: shows preview panel on hover (UX-PLAN-20)."
  [row-content preview & {:keys [class]}]
  [:div {:class (merge-classes "group relative" class)}
   row-content
   [:div {:class "pointer-events-none absolute left-0 top-full z-30 mt-1 hidden w-56 rounded-lg border border-slate-200 bg-white p-3 text-xs shadow-lg group-hover:block dark:border-slate-600 dark:bg-slate-900"}
    preview]])

(defn- parse-number
  "Try to parse a string as a number, return nil if not a number"
  [s]
  (let [parsed (js/parseFloat s)]
    (when (and (not (js/isNaN parsed))
               (not (js/isNaN (js/parseInt s 10))))
      parsed)))

(defn- compare-values
  "Compare two values for sorting, handling strings and numbers"
  [a b]
  (let [a-str (str a)
        b-str (str b)
        a-num (parse-number a-str)
        b-num (parse-number b-str)]
    (cond
      (and a-num b-num) (compare a-num b-num)
      a-num -1
      b-num 1
      :else (compare (str/lower-case a-str) (str/lower-case b-str)))))

(defn table [_headers _rows & _opts]
  (let [search-query (r/atom "")
        sort-column (r/atom nil)
        sort-direction (r/atom :asc)]
    (fn [headers rows & {:keys [on-row-click row-data sortable? sortable-columns numeric-columns class dense? show-search?]
                          :or {show-search? true}}]
      (let [headers-vec (vec headers)
            rows-vec (vec rows)
            row-data-vec (if row-data (vec row-data) (repeat nil))
            sortable-cols (if sortable-columns
                            (set sortable-columns)
                            (set (range (count headers-vec))))
            numeric-cols (set (or numeric-columns #{}))
            handle-header-click (fn [col-idx]
                                  (if (and (= @sort-column col-idx)
                                           (contains? sortable-cols col-idx))
                                    (swap! sort-direction #(if (= % :asc) :desc :asc))
                                    (do
                                      (reset! sort-column col-idx)
                                      (reset! sort-direction :asc))))
            row-pairs (map vector rows-vec row-data-vec)
            filtered-pairs (if-not show-search?
                             row-pairs
                             (if (or (nil? @search-query) (str/blank? @search-query))
                               row-pairs
                               (let [query-lower (str/lower-case @search-query)]
                                 (filter (fn [[row _]]
                                           (some (fn [cell]
                                                   (str/includes? (str/lower-case (str cell)) query-lower))
                                                 row))
                                         row-pairs))))
            sorted-pairs (if (and sortable?
                                  (some? @sort-column)
                                  (contains? sortable-cols @sort-column))
                           (let [sorted (sort-by (fn [[row _]]
                                                   (nth row @sort-column))
                                                 compare-values
                                                 filtered-pairs)]
                             (if (= @sort-direction :desc)
                               (reverse sorted)
                               sorted))
                           filtered-pairs)
            final-rows (mapv first sorted-pairs)
            final-row-data (mapv second sorted-pairs)
            cell-class (if dense? "px-3 py-2 text-sm" "px-4 py-3 text-sm")]
        [:div {:class (merge-classes (if show-search? "space-y-3" "") class)}
         (when show-search?
           [:div {:class "flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between"}
            [:input {:type "text"
                     :value @search-query
                     :on-change #(reset! search-query (-> % .-target .-value))
                     :placeholder "Buscar..."
                     :class (merge-classes input-base-class "max-w-sm focus:border-brand-maroon focus:ring-brand-maroon/20")}]])
         [:div {:class "overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900"}
          [:table {:class "min-w-full divide-y divide-slate-200 dark:divide-slate-700"}
           [:thead {:class "bg-slate-50 dark:bg-slate-800/80"}
            [:tr
             (doall
              (map-indexed
               (fn [idx header]
                 (let [is-sortable (and sortable? (contains? sortable-cols idx))
                       is-sorted (= @sort-column idx)
                       sort-indicator (when is-sorted
                                        (if (= @sort-direction :asc) " ↑" " ↓"))]
                   ^{:key idx}
                   [:th {:scope "col"
                         :on-click (when is-sortable #(handle-header-click idx))
                         :class (merge-classes "select-none text-left text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                                               cell-class
                                               (when is-sortable "cursor-pointer")
                                               (when is-sorted "text-slate-900 dark:text-slate-100"))}
                    (str header sort-indicator)]))
               headers-vec))]]
           [:tbody {:class "divide-y divide-slate-100 dark:divide-slate-800"}
            (doall
             (map-indexed
              (fn [idx row]
                ^{:key idx}
                [:tr {:on-click (when on-row-click #(on-row-click (nth final-row-data idx)))
                      :class (merge-classes "hover:bg-slate-50 dark:hover:bg-slate-900/50"
                                            (when on-row-click "cursor-pointer"))}
                 (doall
                  (map-indexed
                   (fn [idx2 cell]
                     ^{:key idx2}
                     [:td {:class (merge-classes "text-slate-700 dark:text-slate-300" cell-class
                                                 (when (contains? numeric-cols idx2) "tabular-nums"))}
                      cell])
                   row))])
              final-rows))]]]]))))

