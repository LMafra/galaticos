(ns galaticos.components.common
  "Shared/common UI components"
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(defn merge-classes [& classes]
  (->> classes
       (filter #(and % (not (str/blank? %))))
       (str/join " ")))

(defn loading-spinner []
  [:div {:class "flex items-center justify-center gap-3 py-10 text-slate-500"}
   [:div {:class "h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-brand-maroon"}]
   [:span "Carregando..."]])

(defn alert [message & {:keys [variant class]}]
  (let [variant (or variant :error)
        styles (case variant
                 :success "border-emerald-200 bg-emerald-50 text-emerald-700"
                 :warning "border-amber-200 bg-amber-50 text-amber-700"
                 :info "border-sky-200 bg-sky-50 text-sky-700"
                 "border-rose-200 bg-rose-50 text-rose-700")]
    [:div {:class (merge-classes "rounded-xl border px-4 py-3 text-sm" styles class)}
     message]))

(defn error-message [message]
  [alert message :variant :error])

(defn button [text on-click & {:keys [class style disabled type variant aria-label]}]
  (let [variant (or variant :secondary)
        base "inline-flex items-center justify-center rounded-lg px-4 py-2 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-brand-maroon/40 disabled:cursor-not-allowed disabled:opacity-60"
        styles (case variant
                 :primary "bg-brand-maroon text-white hover:bg-brand-maroon/90"
                 :danger "bg-rose-600 text-white hover:bg-rose-700"
                 :outline "border border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                 :ghost "text-slate-600 hover:bg-slate-100"
                 "bg-slate-100 text-slate-700 hover:bg-slate-200")]
    [:button (merge {:on-click on-click
                     :class (merge-classes base styles class)
                     :type (or type "button")
                     :disabled disabled
                     :style style}
                    (when aria-label {:aria-label aria-label}))
     text]))

(defn not-found-resource
  "Shown when a detail resource (player, championship, team) is not found (404).
   message: e.g. 'Jogador não encontrado.'
   on-back: callback to navigate to list (e.g. #(rfe/push-state :players))"
  [message on-back]
  [:div {:class "space-y-4"}
   [alert message :variant :warning]
   [button "Voltar" on-back :variant :outline]])

(defn card [& children]
  (into [:div {:class "app-card p-5"}] children))

(defn stat-card [label value & {:keys [delta icon class]}]
  [:div {:class (merge-classes "app-card p-5" class)}
   [:div {:class "flex items-start justify-between"}
    [:div
     [:p {:class "text-sm font-medium text-slate-500"} label]
     [:p {:class "mt-2 text-2xl font-semibold text-slate-900"} value]
     (when delta
       [:p {:class "mt-1 text-xs text-emerald-600"} delta])]
    (when icon
      [:div {:class "rounded-xl bg-brand-maroon/10 p-2 text-brand-maroon"} icon])]])

(defn badge [text & {:keys [variant class]}]
  (let [styles (case variant
                 :success "bg-emerald-100 text-emerald-700"
                 :warning "bg-amber-100 text-amber-700"
                 :info "bg-sky-100 text-sky-700"
                 :danger "bg-rose-100 text-rose-700"
                 "bg-slate-100 text-slate-700")]
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
      "completed" "Concluído"
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

(defn format-match-result
  "Format match result for display. result can be a map with :our-score/:opponent-score
   (or :result.our-score from API) or nil. Returns e.g. \"3 x 1\" or \"-\"."
  [result]
  (cond
    (nil? result) "-"
    (map? result)
    (let [our (get result :our-score (get result "our-score"))
          opp (get result :opponent-score (get result "opponent-score"))]
      (if (and (some? our) (some? opp))
        (str our " x " opp)
        "-"))
    :else "-"))

(defonce field-id-counter (atom 0))

(defn input-field [label value on-change & {:keys [type placeholder class container-class required? error id]}]
  (let [field-id (or id (str "field-" (swap! field-id-counter inc)))
        border-class (if error
                      "border-rose-500 focus:border-rose-500 focus:ring-rose-500/20"
                      "border-slate-300 focus:border-brand-maroon focus:ring-brand-maroon/20")]
    [:div {:class (merge-classes "space-y-2" container-class)}
     [:label {:for field-id
              :class (merge-classes "text-sm font-medium" (if error "text-rose-700" "text-slate-700"))}
      (str label (when required? " *"))]
     [:input (merge {:type (or type "text")
                     :id field-id
                     :value value
                     :on-change #(on-change (-> % .-target .-value))
                     :placeholder placeholder
                     :class (merge-classes "w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:outline-none focus:ring-2" border-class class)}
                   (when error {:aria-invalid "true"}))]]))

(defn select-field [label value options on-change & {:keys [class container-class required? error id]}]
  (let [field-id (or id (str "field-" (swap! field-id-counter inc)))
        border-class (if error
                      "border-rose-500 focus:border-rose-500 focus:ring-rose-500/20"
                      "border-slate-300 focus:border-brand-maroon focus:ring-brand-maroon/20")]
    [:div {:class (merge-classes "space-y-2" container-class)}
     [:label {:for field-id
              :class (merge-classes "text-sm font-medium" (if error "text-rose-700" "text-slate-700"))}
      (str label (when required? " *"))]
     (into [:select (merge {:value value
                            :id field-id
                            :on-change #(on-change (-> % .-target .-value))
                            :class (merge-classes "w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:outline-none focus:ring-2" border-class class)}
                          (when error {:aria-invalid "true"}))]
           (map (fn [[opt-value opt-label]]
                  [:option {:key opt-value :value opt-value} opt-label])
                options))]))

(defn modal [{:keys [title content on-close actions]}]
  (let [title-id "modal-title"]
    [:div {:class "fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
           :role "dialog"
           :aria-modal "true"
           :aria-labelledby title-id}
     [:div {:class "app-card w-full max-w-lg p-6"}
      [:div {:class "flex items-center justify-between"}
       [:h3 {:id title-id :class "text-lg font-semibold text-slate-900"} title]
       [:button {:class "text-slate-400 hover:text-slate-600" :on-click on-close :aria-label "Fechar"} "×"]]
      [:div {:class "mt-4 text-sm text-slate-600"} content]
      [:div {:class "mt-6 flex justify-end gap-2"} actions]]]))

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
    (fn [headers rows & {:keys [on-row-click row-data sortable? sortable-columns class dense? show-search?]
                          :or {show-search? true}}]
      (let [headers-vec (vec headers)
            rows-vec (vec rows)
            row-data-vec (if row-data (vec row-data) (repeat nil))
            sortable-cols (if sortable-columns
                            (set sortable-columns)
                            (set (range (count headers-vec))))
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
                     :class "w-full max-w-sm rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-maroon focus:outline-none focus:ring-2 focus:ring-brand-maroon/20"}]])
         [:div {:class "overflow-hidden rounded-xl border border-slate-200 bg-white"}
          [:table {:class "min-w-full divide-y divide-slate-200"}
           [:thead {:class "bg-slate-50"}
            [:tr
             (doall
              (map-indexed
               (fn [idx header]
                 (let [is-sortable (and sortable? (contains? sortable-cols idx))
                       is-sorted (= @sort-column idx)
                       sort-indicator (when is-sorted
                                        (if (= @sort-direction :asc) " ↑" " ↓"))]
                   ^{:key idx}
                   [:th {:on-click (when is-sortable #(handle-header-click idx))
                         :class (merge-classes "select-none text-left text-xs font-semibold uppercase tracking-wide text-slate-500"
                                               cell-class
                                               (when is-sortable "cursor-pointer")
                                               (when is-sorted "text-slate-900"))}
                    (str header sort-indicator)]))
               headers-vec))]]
           [:tbody {:class "divide-y divide-slate-100"}
            (doall
             (map-indexed
              (fn [idx row]
                ^{:key idx}
                [:tr {:on-click (when on-row-click #(on-row-click (nth final-row-data idx)))
                      :class (merge-classes "hover:bg-slate-50"
                                            (when on-row-click "cursor-pointer"))}
                 (doall
                  (map-indexed
                   (fn [idx2 cell]
                     ^{:key idx2}
                     [:td {:class (merge-classes "text-slate-700" cell-class)} cell])
                   row))])
              final-rows))]]]]))))

