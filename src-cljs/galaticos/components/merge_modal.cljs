(ns galaticos.components.merge-modal
  "Three-step merge workflow: pick reference → fuzzy candidate checkboxes → field-by-field merge."
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [galaticos.api :as api]
            [galaticos.components.common :as common]
            [galaticos.state :as state]))

(def ^:private field-rows
  [{:kw :name :label "Nome"}
   {:kw :nickname :label "Apelido"}
   {:kw :position :label "Posição"}
   {:kw :team-id :label "Time (ID)"}
   {:kw :birth-date :label "Nascimento"}
   {:kw :nationality :label "Nacionalidade"}
   {:kw :height :label "Altura"}
   {:kw :weight :label "Peso"}
   {:kw :preferred-foot :label "Pé preferido"}
   {:kw :shirt-number :label "Camisa"}
   {:kw :email :label "E-mail"}
   {:kw :phone :label "Telefone"}
   {:kw :number :label "Número"}
   {:kw :photo-url :label "Foto (URL)"}
   {:kw :notes :label "Notas"}
   {:kw :aggregated-stats :label "Estatísticas agregadas" :combined? true}
   {:kw :active :label "Ativo"}])

(defn- initial-selections []
  (into {}
        (map (fn [row]
               [(:kw row) (if (:combined? row) "combined" "master")])
             field-rows)))

(defn- normalize-id [v]
  (cond
    (string? v) v
    (map? v) (or (get v "$oid") (get v :$oid))
    :else nil))

(defn- player-id-str [p]
  (str (or (normalize-id (:_id p)) (normalize-id (:id p)) "")))

(defn- fmt-val [v]
  (cond
    (nil? v) "—"
    (and (map? v) (:combined-preview v)) "Combinado (servidor)"
    (map? v) "[objeto]"
    (boolean? v) (if v "Sim" "Não")
    :else (str v)))

(defn- coerce-candidate-rows [data]
  (let [v (cond
            (vector? data) data
            (sequential? data) (vec data)
            :else [])]
    (mapv (fn [row]
            {:id (str (or (:id row) (get row "id")))
             :name (or (:name row) (get row "name") "—")
             :similarity (or (:similarity row) (get row "similarity"))
             :position (or (:position row) (get row "position"))
             :shirt-number (or (:shirt-number row) (get row "shirt-number"))})
          v)))

(defn- fetch-docs-sequential!
  [ids on-done]
  (if (empty? ids)
    (on-done {})
    ((fn step [remaining acc]
       (let [id (str (first remaining))]
         (api/get-player id
                         (fn [doc]
                           (let [rest (rest remaining)
                                 acc* (assoc acc id (or doc {}))]
                             (if (empty? rest)
                               (on-done acc*)
                               (step rest acc*))))
                         (fn [_ _]
                           (on-done nil)))))
     (mapv str ids) {})))

(defn- parse-source [v]
  (let [s (str/trim (str v))]
    (cond
      (= "master" s) {:kind :master}
      (= "combined" s) {:kind :combined}
      :else
      (when-let [[_ idx] (re-find #"^merged-(\d+)$" s)]
        {:kind :merged :idx (js/parseInt idx 10)}))))

(defn- run-merge! [st master-doc merged-docs-vec on-success on-close]
  (when-not (:submitting? @st)
    (swap! st assoc :submitting? true :error nil)
    (let [m-id (normalize-id (or (:_id master-doc) (:id master-doc)))
          merged-ids (mapv #(normalize-id (or (:_id %) (:id %))) merged-docs-vec)
          sels (:selections @st)
          payload {:master-id m-id
                   :merged-ids merged-ids
                   :field-selections (into {} (map (fn [[k v]] [(name k) v]) sels))}]
      (api/merge-players payload
                         (fn [data]
                           (swap! st assoc :submitting? false)
                           (when on-success (on-success data))
                           (on-close))
                         (fn [err _]
                           (swap! st assoc :submitting? false :error (str err)))))))

(defn merge-workflow-modal
  [{:keys [championship-id roster-players initial-reference-id]}]
  (let [st (r/atom {:step :pick-ref
                    :reference-id nil
                    :ref-doc nil
                    :candidates []
                    :checked-ids #{}
                    :loading? false
                    :step1-q ""
                    :search-hits []
                    :searching? false
                    :docs-by-id nil
                    :master-id nil
                    :ordered-ids []
                    :selections (initial-selections)
                    :error nil
                    :submitting? false})
        fetch-candidates!
        (fn [ref-id]
          (when ref-id
            (swap! st assoc :loading? true :error nil)
            (api/get-merge-candidates
             ref-id
             {:championship-id championship-id}
             (fn [rows]
               (let [v (coerce-candidate-rows rows)
                     ids (into #{} (map :id v))]
                 (swap! st assoc
                        :candidates v
                        :checked-ids ids
                        :loading? false
                        :step :pick-candidates)))
             (fn [err _]
               (swap! st assoc :loading? false :error (str err))
               (state/toast-error! (str err))))))
        load-step3!
        (fn []
          (let [ref-id (str (:reference-id @st))
                checked (mapv str (:checked-ids @st))
                ids (vec (distinct (cons ref-id checked)))]
            (swap! st assoc :loading? true :error nil :ordered-ids ids)
            (fetch-docs-sequential!
             ids
             (fn [m]
               (if (nil? m)
                 (do (swap! st assoc :loading? false)
                     (state/toast-error! "Falha ao carregar jogadores."))
                 (let [master ref-id
                       _docs (mapv #(get m %) ids)]
                   (swap! st assoc
                          :docs-by-id m
                          :master-id master
                          :selections (initial-selections)
                          :step :merge
                          :loading? false)))))))]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (when initial-reference-id
          (swap! st assoc :reference-id (str initial-reference-id))
          (api/get-player
           (str initial-reference-id)
           (fn [doc]
             (swap! st assoc :ref-doc doc)
             (fetch-candidates! initial-reference-id))
           (fn [_ _]
             (swap! st assoc :error "Referência inválida.")))))
      :reagent-render
      (fn [{:keys [on-close on-success]}]
        (let [{:keys [step reference-id ref-doc candidates checked-ids loading?
                       step1-q search-hits searching? docs-by-id master-id
                       ordered-ids selections error submitting?]} @st
              championship-mode? (boolean (seq roster-players))
              roster-filtered (if championship-mode?
                                (filter (fn [p]
                                          (let [nm (str/lower-case (str (:name p) " " (:nickname p "")))
                                                q (str/lower-case (str/trim step1-q))]
                                            (or (str/blank? q) (str/includes? nm q))))
                                        roster-players)
                                [])
              pick-reference! (fn [id doc]
                                (swap! st assoc :reference-id id :ref-doc doc)
                                (fetch-candidates! id))
              master-doc (when docs-by-id (get docs-by-id (str master-id)))
              merged-docs (when (and docs-by-id master-id)
                            (let [mid (str master-id)]
                              (->> ordered-ids
                                   (remove #(= (str %) mid))
                                   (mapv #(get docs-by-id (str %))))))
              n-merged (count merged-docs)
              preview-fn (fn []
                           (when master-doc
                             (into {}
                                   (map (fn [row]
                                          (let [kw (:kw row)
                                                sel (selections kw)]
                                            [kw
                                             (cond
                                               (= sel "master") (get master-doc kw)
                                               (= sel "combined") (when (= kw :aggregated-stats)
                                                                    {:combined-preview true})
                                               :else (when-let [src (parse-source sel)]
                                                       (case (:kind src)
                                                         :merged (get (nth merged-docs (:idx src) {}) kw)
                                                         nil)))]))
                                   field-rows))))]
          [:div {:class "fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/50 p-3 sm:p-4 dark:bg-black/60"
                 :role "dialog"}
           [:div {:class "app-card flex max-h-[min(92vh,calc(100vh-1.5rem))] w-full max-w-5xl flex-col overflow-hidden shadow-xl"}
            [:div {:class "flex shrink-0 items-start justify-between gap-3 border-b border-slate-100 px-4 pb-3 pt-4 dark:border-slate-700 sm:px-6 sm:pt-6"}
             [:div {:class "min-w-0 pr-2"}
              [:h2 {:class "text-lg font-semibold text-slate-900 sm:text-xl dark:text-slate-100"}
               "Mesclar jogadores"]
              [:p {:class "mt-1 text-sm text-slate-500"}
               (case step
                 :pick-ref "Escolha o jogador de referência."
                 :pick-candidates "Marque quais candidatos similares entram na mesclagem (pelo menos um)."
                 "Compare registros e confirme.")]]
             [:button {:type "button"
                       :class "shrink-0 text-2xl leading-none text-slate-400 hover:text-slate-600"
                       :aria-label "Fechar"
                       :on-click on-close} "×"]]

            (when error
              [:p {:class "shrink-0 px-4 pt-2 text-sm text-red-600 sm:px-6"} error])

            [:div {:class "flex min-h-0 flex-1 flex-col"}
             (if loading?
               [:div {:class "flex flex-1 items-center justify-center py-10"} [common/loading-spinner]]
               (case step
                 :pick-ref
                 [:div {:class "min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-6 sm:pb-6"}
                  [:div {:class "space-y-4"}
                 (when reference-id
                   [:div {:class "rounded-lg border border-amber-200 bg-amber-50/80 p-3 text-sm dark:border-amber-900/50 dark:bg-amber-950/30"}
                    [:span "Referência: " [:strong (or (:name ref-doc) reference-id)]]
                    [:button {:type "button"
                              :class "ml-3 text-brand-maroon underline"
                              :on-click #(swap! st assoc :reference-id nil :ref-doc nil :candidates [] :checked-ids #{})}
                     "Limpar"]])
                 (if championship-mode?
                   [:div {:class "space-y-2"}
                    [:input {:type "text"
                             :value step1-q
                             :placeholder "Filtrar inscritos..."
                             :on-change #(swap! st assoc :step1-q (-> % .-target .-value))
                             :class "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-800"}]
                    [:ul {:class "max-h-56 space-y-1 overflow-y-auto rounded-lg border border-slate-200 p-2 dark:border-slate-700"}
                     (for [p roster-filtered]
                       ^{:key (player-id-str p)}
                       [:li
                        [:button {:type "button"
                                  :class "w-full rounded px-2 py-1.5 text-left text-sm hover:bg-slate-100 dark:hover:bg-slate-800"
                                  :on-click #(pick-reference! (player-id-str p) p)}
                         (:name p) " — " (or (:position p) "—")]])]]
                   [:div {:class "space-y-2"}
                    [:div {:class "flex flex-wrap gap-2"}
                     [:input {:type "text"
                              :value step1-q
                              :placeholder "Nome para buscar..."
                              :on-change #(swap! st assoc :step1-q (-> % .-target .-value))
                              :class "min-w-[200px] flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-800"}]
                     [common/button "Buscar"
                      (fn []
                        (swap! st assoc :searching? true)
                        (api/search-players
                         {:q (str/trim step1-q) :page 1 :limit 40}
                         (fn [res]
                           (swap! st assoc :searching? false :search-hits (api/coerce-player-list res)))
                         (fn [err]
                           (swap! st assoc :searching? false)
                           (state/toast-error! (str err)))))
                      :variant :outline
                      :disabled (str/blank? (str/trim step1-q))]]
                    (when searching? [:p {:class "text-xs text-slate-500"} "Buscando…"])
                    [:ul {:class "max-h-56 space-y-1 overflow-y-auto rounded-lg border border-slate-200 p-2 dark:border-slate-700"}
                     (for [p search-hits]
                       ^{:key (player-id-str p)}
                       [:li
                        [:button {:type "button"
                                  :class "w-full rounded px-2 py-1.5 text-left text-sm hover:bg-slate-100 dark:hover:bg-slate-800"
                                  :on-click #(pick-reference! (player-id-str p) p)}
                         (:name p)]])]])]]

                :pick-candidates
                [:div {:class "min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-6 sm:pb-6"}
                 [:div {:class "space-y-4"}
                 [:p {:class "text-sm text-slate-600"}
                  "Referência: " [:strong (or (:name ref-doc) reference-id)]]
                 [:button {:type "button"
                           :class "text-sm text-brand-maroon underline"
                           :on-click #(swap! st assoc :step :pick-ref :candidates [] :checked-ids #{} :reference-id nil :ref-doc nil)}
                  "← Alterar referência"]
                 (if (seq candidates)
                   [:div {:class "overflow-x-auto"}
                    [:table {:class "w-full min-w-[480px] border-collapse text-sm"}
                     [:thead
                      [:tr {:class "border-b border-slate-200 dark:border-slate-700"}
                       [:th {:class "py-2 pr-2"} ""]
                       [:th {:class "py-2 px-2 text-left"} "Nome"]
                       [:th {:class "py-2 px-2"} "Sim."]
                       [:th {:class "py-2 px-2"} "Pos."]
                       [:th {:class "py-2 px-2"} "Camisa"]]]
                     [:tbody
                      (for [row candidates]
                        ^{:key (:id row)}
                        [:tr {:class "border-b border-slate-100 dark:border-slate-800"}
                         [:td {:class "py-2 pr-2"}
                          [:input {:type "checkbox"
                                   :checked (contains? checked-ids (:id row))
                                   :on-change (fn [e]
                                                 (swap! st update :checked-ids
                                                        (fn [s]
                                                          (if (.-checked (.-target e))
                                                            (conj s (:id row))
                                                            (disj s (:id row))))))}]]
                         [:td {:class "py-2 px-2"} (:name row)]
                         [:td {:class "py-2 px-2 tabular-nums"} (str (:similarity row))]
                         [:td {:class "py-2 px-2"} (or (:position row) "—")]
                         [:td {:class "py-2 px-2"} (or (:shirt-number row) "—")]])]]]
                   [:p {:class "text-sm text-slate-500"} "Nenhum candidato acima do limiar de similaridade."])
                 [:div {:class "flex justify-end gap-3"}
                  [common/button "Cancelar" on-close :variant :outline]
                  [common/button
                   "Próximo: comparar"
                   #(load-step3!)
                   :variant :primary
                   :disabled (empty? checked-ids)]]]]

                :merge
                [:div {:class "flex min-h-0 flex-1 flex-col"}
                 [:div {:class "min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-6 sm:pb-4"}
                  (if (or (nil? master-doc) (nil? merged-docs))
                    [:p {:class "text-slate-500"} "Carregando comparação…"]
                    [:div {:class "space-y-6"}
                     [:div {:class "space-y-2"}
                      [:p {:class "text-xs font-semibold text-slate-500"} "Registro mestre (permanece no sistema)"]
                      [:p {:class "text-xs text-slate-500"} "Para cada campo escolha Mestre ou Outro. Na linha «Estatísticas agregadas», use Combinar para somar no servidor."]
                      [:div {:class "flex flex-wrap gap-3"}
                       (for [pid ordered-ids]
                         ^{:key pid}
                         (let [d (get docs-by-id (str pid))
                               lbl-cls (if (= (str pid) (str master-id))
                                         "inline-flex cursor-pointer items-center gap-2 rounded-lg border border-brand-maroon bg-brand-maroon/5 px-3 py-2 text-sm"
                                         "inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm dark:border-slate-600")]
                           [:label {:class lbl-cls}
                            [:input {:type "radio"
                                     :name "merge-master-pick"
                                     :checked (= (str pid) (str master-id))
                                     :on-change #(when d
                                                   (swap! st assoc :master-id (str pid) :selections (initial-selections)))}]
                            (or (:name d) pid)]))]]
                     [:div {:class "overflow-x-auto"}
                      [:table {:class "w-full min-w-[640px] border-collapse text-sm"}
                       [:thead
                        [:tr {:class "border-b border-slate-200 text-left dark:border-slate-700"}
                         [:th {:class "py-2 pr-2 font-semibold"} "Campo"]
                         [:th {:class "py-2 px-2 font-semibold"} (str "Mestre: " (or (:name master-doc) "—"))]
                         (for [i (range n-merged)]
                           ^{:key i}
                           [:th {:class "py-2 px-2 font-semibold"}
                            [:div (str "Outro " (inc i))]
                            [:div {:class "text-xs font-normal text-slate-500"}
                             (or (:name (nth merged-docs i)) "—")]])
                         [:th {:class "py-2 pl-2 font-semibold w-32"}
                          [:div "Stats"]
                          [:div {:class "text-xs font-normal text-slate-500"} "só linha Estatísticas"]]]]
                       [:tbody
                        (for [row field-rows]
                          ^{:key (:kw row)}
                          [:tr {:class "border-b border-slate-100 dark:border-slate-800"}
                           [:td {:class "py-2 pr-2 align-top"} (:label row)]
                           [:td {:class "py-2 px-2 align-top"}
                            [:label {:class "flex cursor-pointer items-start gap-2"}
                             [:input {:type "radio"
                                      :name (str "merge-field-" (name (:kw row)))
                                      :checked (= (selections (:kw row)) "master")
                                      :on-change #(swap! st assoc-in [:selections (:kw row)] "master")}]
                             [:span {:class "break-all"} (fmt-val (get master-doc (:kw row)))]]]
                           (for [i (range n-merged)]
                             ^{:key i}
                             [:td {:class "py-2 px-2 align-top"}
                              [:label {:class "flex cursor-pointer items-start gap-2"}
                               [:input {:type "radio"
                                        :name (str "merge-field-" (name (:kw row)))
                                        :checked (= (selections (:kw row)) (str "merged-" i))
                                        :on-change #(swap! st assoc-in [:selections (:kw row)] (str "merged-" i))}]
                               [:span {:class "break-all"}
                                (fmt-val (get (nth merged-docs i {}) (:kw row)))]]])
                           [:td {:class "py-2 pl-2 align-top text-center text-slate-400 dark:text-slate-500"}
                            (if (:combined? row)
                              [:label {:class "inline-flex cursor-pointer flex-col items-center gap-1 text-slate-800 dark:text-slate-100"}
                               [:input {:type "radio"
                                        :name (str "merge-field-" (name (:kw row)))
                                        :checked (= (selections (:kw row)) "combined")
                                        :on-change #(swap! st assoc-in [:selections (:kw row)] "combined")}]
                               [:span {:class "text-xs text-slate-500"} "Combinar"]]
                              "—")]])]]]
                     [:div {:class "rounded-lg bg-slate-50 p-4 dark:bg-slate-900/40"}
                      [:p {:class "text-xs font-semibold text-slate-500"} "Pré-visualização do resultado"]
                      [:p {:class "mt-1 text-xs text-slate-500"}
                       "Valores finais após as escolhas acima (exceto stats combinadas, aplicadas no servidor)."]
                      (into [:ul {:class "mt-2 max-h-48 space-y-1 overflow-y-auto text-xs"}]
                            (for [row field-rows]
                              ^{:key (:kw row)}
                              [:li {:class "flex gap-2"}
                               [:span {:class "shrink-0 font-medium text-slate-600 dark:text-slate-400"}
                                (str (:label row) ":")]
                               [:span {:class "break-all text-slate-800 dark:text-slate-200"}
                                (fmt-val (get (preview-fn) (:kw row)))]]))]])]
                 (when (and master-doc merged-docs)
                   [:div {:class "shrink-0 border-t border-slate-200 bg-slate-50/95 px-4 py-4 dark:border-slate-700 dark:bg-slate-900/95 sm:px-6"}
                    [:div {:class "flex flex-wrap justify-end gap-3"}
                     [common/button "← Voltar"
                      #(swap! st assoc :step :pick-candidates :docs-by-id nil :master-id nil)
                      :variant :outline]
                     [common/button "Cancelar" on-close :variant :outline]
                     [common/button
                      (if submitting? "Mesclando…" "Confirmar mesclagem")
                      #(run-merge! st master-doc merged-docs on-success on-close)
                      :variant :primary
                      :disabled (or submitting? (zero? n-merged))]]])]

                ))]]]))})))

;; Legacy name for callers that only need sequential player fetch (unused externally).
(defn fetch-candidate-docs!
  [candidate-ids on-done]
  (if (empty? candidate-ids)
    (on-done [])
    ((fn step [remaining acc]
       (api/get-player (first remaining)
                       (fn [doc]
                         (let [rest (rest remaining)
                               acc* (conj acc (or doc {}))]
                           (if (empty? rest)
                             (on-done (vec acc*))
                             (step rest acc*))))
                       (fn [_ _]
                         (on-done nil))))
     candidate-ids [])))
