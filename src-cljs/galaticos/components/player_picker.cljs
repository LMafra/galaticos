(ns galaticos.components.player-picker
  "Busca local, lista alfabética e criação rápida de jogador (reutilizável)."
  (:require [reagent.core :as r]
            [galaticos.components.common :as common]
            [galaticos.state :as state]
            [clojure.string :as str]
            [goog.object :as gobj]))

(def quick-create-position
  "Posição enviada na criação rápida até o utilizador editar o jogador."
  "A definir")

(defn normalize-id [v]
  (cond
    (string? v) v
    (map? v) (or (get v "$oid") (get v :$oid))
    (some? v) (str v)
    :else nil))

(defn- js-plain-obj? [x]
  (and (some? x)
       (object? x)
       (not (map? x))
       (not (array? x))
       (not (fn? x))))

(defn- player-row? [x]
  (or (map? x) (js-plain-obj? x)))

(defn- player-id-raw [player]
  (when player
    (cond
      (map? player)
      (or (:_id player) (:id player) (get player "_id") (get player "id"))
      (js-plain-obj? player)
      (or (gobj/get player "_id") (gobj/get player "id"))
      :else nil)))

(defn player-id [player]
  (normalize-id (player-id-raw player)))

(defn- player-name-str [player]
  (str
    (cond
      (not (some? player)) ""
      (map? player) (or (:name player) (get player "name")
                        (:search-name player) (get player "search-name")
                        "")
      (js-plain-obj? player) (or (gobj/get player "name")
                                 (gobj/get player "search-name")
                                 "")
      :else "")))

(defn- player-nickname-str [player]
  (str
    (cond
      (not (some? player)) ""
      (map? player) (or (:nickname player) (get player "nickname") "")
      (js-plain-obj? player) (or (gobj/get player "nickname") "")
      :else "")))

(defn- player-sort-key [player]
  (str/lower-case (player-name-str player)))

(defn- unwrap-players-input [x]
  (cond
    (and (map? x) (true? (:success x))) (:data x)
    (and (map? x) (true? (get x "success"))) (get x "data")
    (and (js-plain-obj? x) (true? (gobj/get x "success"))) (gobj/get x "data")
    :else x))

(defn- as-player-vec [players]
  (let [players (unwrap-players-input players)]
    (cond
      (nil? players) []
      (or (map? players) (js-plain-obj? players))
      (if (or (player-id-raw players) (seq (player-name-str players))) [players] [])
      (sequential? players) (vec (filter player-row? players))
      (array? players) (vec (filter player-row? (array-seq players)))
      (and (seqable? players) (not (string? players)) (not (map? players)))
      (vec (filter player-row? (or (seq players) [])))
      :else [])))

(defn- exclude-id-set [exclude-ids]
  (into #{}
        (comp (map str) (remove str/blank?))
        (cond
          (nil? exclude-ids) []
          (set? exclude-ids) exclude-ids
          (sequential? exclude-ids) exclude-ids
          :else [])))

(defn filter-players-by-query [players q]
  (let [qs (str (or q ""))]
    (if (str/blank? qs)
      players
      (let [qq (str/lower-case (str/trim qs))]
        (filter (fn [p]
                  (let [n (str/lower-case (player-name-str p))
                        nick (str/lower-case (player-nickname-str p))]
                    (or (str/includes? n qq)
                        (and (not (str/blank? nick)) (str/includes? nick qq)))))
                players)))))

(defn eligible-players [players exclude-ids]
  (let [ex (exclude-id-set exclude-ids)]
    (filter (fn [p]
              (let [pid (player-id p)]
                (or (str/blank? pid) (not (contains? ex pid)))))
            players)))

(defn sorted-eligible-for-picker [players exclude-ids query]
  (->> (eligible-players (as-player-vec players) exclude-ids)
       (#(filter-players-by-query % query))
       (sort-by player-sort-key)
       vec))

(defn player-search-add-panel
  "Painel: busca, lista A–Z, ação por linha, criação rápida quando não há resultados.
   Props: :players, :exclude-ids (set de ids string), :action-label,
   :on-pick-player (fn [player]), :on-quick-create (fn [name ok err]) opcional,
   :compact?, :disabled?, :search-placeholder, :selected-id (string opcional),
   :players-loading? (boolean; true enquanto o catálogo de :players ainda não foi obtido — ex. GET /api/players pendente)."
  [_props]
  (let [search (r/atom "")
        creating? (r/atom false)]
    (fn [{:keys [players exclude-ids action-label on-pick-player on-quick-create
                 compact? disabled? search-placeholder selected-id label
                 players-loading?]}]
      (let [q-raw @search
            q (str (or q-raw ""))
            has-query? (not (str/blank? (str/trim q)))
            players-vec (as-player-vec players)
            eligible-vec (->> (eligible-players players-vec exclude-ids) vec)
            rows (if has-query?
                   (->> eligible-vec
                        (#(filter-players-by-query % q))
                        (sort-by player-sort-key)
                        vec)
                   [])
            picker-panel-class (if compact?
                                "overflow-hidden rounded-lg border border-slate-300 bg-white shadow-sm"
                                "overflow-hidden rounded-lg border border-slate-300 bg-white shadow-sm")
            picker-input-class (common/merge-classes
                                "w-full border-0 border-b bg-white px-3 shadow-none placeholder:text-slate-400 focus:outline-none focus:ring-0"
                                (if disabled?
                                  "cursor-not-allowed border-slate-200 text-slate-400"
                                  "border-slate-200 text-slate-900")
                                (if compact? "py-1.5 text-xs" "py-2 text-sm"))
            list-class (if compact?
                         "max-h-36 overflow-y-auto bg-slate-50/50"
                         "max-h-56 overflow-y-auto bg-slate-50/50")
            row-class (if compact?
                        "flex items-center justify-between gap-2 border-b border-slate-100 px-2 py-1.5 text-xs last:border-b-0"
                        "flex items-center justify-between gap-2 border-b border-slate-100 px-3 py-2 text-sm last:border-b-0")
            can-quick? (and (fn? on-quick-create)
                            has-query?
                            (empty? rows)
                            (not @creating?)
                            (not disabled?)
                            (not players-loading?))]
        [:div {:class (if compact? "space-y-2" "space-y-3")}
         (when label
           [:label {:class (str "text-sm font-medium " (if disabled? "text-slate-400" "text-slate-700"))}
            label])
         [:div {:class picker-panel-class}
          [:input {:type "text"
                   :value q
                   :disabled disabled?
                   :placeholder (or search-placeholder "Buscar por nome ou apelido...")
                   :on-change (fn [e]
                                (reset! search (str (or (some-> e .-target .-value) ""))))
                   :class picker-input-class}]
          (if disabled?
            [:p {:class "px-3 py-3 text-xs text-slate-500"} "Indisponível."]
            [:div {:class list-class}
             (if (seq rows)
               (doall
                (map-indexed
                 (fn [i p]
                   (let [pid (player-id p)
                         sel? (= pid selected-id)]
                     ^{:key (str i "-" (or pid "p"))}
                     [:div {:class (common/merge-classes row-class (when sel? "bg-brand-maroon/5"))}
                      [:div {:class "min-w-0 flex-1"}
                       [:span {:class "font-medium text-slate-800"}
                        (let [nm (player-name-str p)] (if (str/blank? nm) "—" nm))]
                       (when-let [pos (cond
                                        (map? p) (or (:position p) (get p "position"))
                                        (js-plain-obj? p) (gobj/get p "position")
                                        :else nil)]
                         (when (and (not compact?) (not (str/blank? (str pos))))
                           [:span {:class "ml-2 text-slate-500"} pos]))]
                      (if sel?
                        [common/badge "Atual" :variant :info]
                        [common/button action-label
                         #(when (fn? on-pick-player) (on-pick-player p))
                         :variant :outline
                         :class (when compact? "px-2 py-1 text-xs")])]))
                 rows))
               [:p {:class (str (if compact? "px-2 py-3 text-xs" "px-3 py-4 text-sm") " text-slate-500")}
                (cond
                  players-loading? "Carregando jogadores..."
                  (not has-query?) "Digite para buscar jogadores."
                  has-query?
                  (cond
                    (empty? players-vec) "Não há jogadores cadastrados no sistema."
                    (empty? eligible-vec) "Todos os jogadores já estão inscritos neste campeonato."
                    :else "Nenhum jogador disponível para adicionar.")
                  :else "Nenhum resultado para esta busca.")])])]
         (when (and selected-id (not (str/blank? selected-id)))
           (when-let [p (some #(when (= (player-id %) selected-id) %) (as-player-vec players))]
             [:p {:class (if compact? "text-xs text-slate-600" "text-sm text-slate-600")}
              [:span {:class "font-medium text-slate-800"} "Selecionado: "] (player-name-str p)]))
         (when can-quick?
           [:div {:class "rounded-lg border border-dashed border-slate-300 bg-white p-3"}
            [common/button
             (if @creating? "Criando..." (str "Criar jogador \"" (str/trim q) "\""))
             (fn []
               (when-not @creating?
                 (reset! creating? true)
                 (on-quick-create (str/trim q)
                                  (fn [_created]
                                    (reset! creating? false)
                                    (reset! search ""))
                                  (fn [err-msg]
                                    (reset! creating? false)
                                    (state/toast-error! (str err-msg))))))
             :variant :primary
             :disabled @creating?
             :class (when compact? "text-xs")]])]))))
