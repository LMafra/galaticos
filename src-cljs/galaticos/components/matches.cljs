(ns galaticos.components.matches
  "Match list and form components"
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.components.player-picker :as player-picker]
            [galaticos.effects :as effects]
            ["lucide-react" :refer [CalendarPlus Pencil Trash2]]))

(defn- quick-create-player-for-match!
  [championship-id stat-idx enrolled-players-atom form-data-atom form-error-atom name ok err]
  (api/create-player
   {:name name :position player-picker/quick-create-position}
   (fn [created]
     (if-let [pid (player-picker/player-id created)]
       (api/enroll-player-in-championship
        championship-id
        pid
        (fn [_result]
          (api/get-championship-players
           championship-id
           (fn [rows]
             (reset! enrolled-players-atom rows)
             (swap! form-data-atom assoc-in [:player-statistics stat-idx :player-id] pid)
             (ok created))
           (fn [e]
             (reset! form-error-atom (str "Erro ao carregar inscritos: " e))
             (err (str "Erro ao carregar inscritos: " e)))))
        (fn [e]
          (reset! form-error-atom (str "Erro ao inscrever jogador: " e))
          (err (str "Erro ao inscrever jogador: " e))))
       (err "Jogador criado sem ID retornado.")))
   (fn [e]
     (err (str "Erro ao criar jogador: " e)))))

(defn- match-row
  [match delete-match!]
  (let [match-id (:_id match)]
    [:div {:class "flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm lg:flex-row lg:items-center lg:justify-between"}
     [:div {:class "flex items-center gap-3"}
      [:div {:class "rounded-xl bg-brand-maroon/10 p-2 text-brand-maroon"}
       [:> CalendarPlus {:size 18}]]
      [:div
       [:p {:class "text-sm font-semibold text-slate-900"} (:opponent match)]
       [:p {:class "text-xs text-slate-500"} (str (.toLocaleDateString (js/Date. (:date match))) " • " (or (:venue match) "-"))]]]
     [:div {:class "flex items-center gap-3"}
      [common/badge (common/format-match-result (:result match)) :variant :info]
      [:button {:class "rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-100"
                :on-click #(rfe/push-state :match-edit {:id match-id})}
       [:> Pencil {:size 16}]]
      [:button {:class "rounded-lg border border-slate-200 p-2 text-rose-600 hover:bg-rose-50"
                :on-click #(delete-match! match-id)}
       [:> Trash2 {:size 16}]]]]))

(defn match-list []
  (let [search (r/atom "")]
    (fn []
      (let [{:keys [matches matches-loading? matches-error championships]} @state/app-state
            delete-match! (fn [match-id]
                            (when (js/confirm "Tem certeza que deseja deletar esta partida?")
                              (api/delete-match match-id
                                                (fn [_result]
                                                  (effects/ensure-matches! {:force? true}))
                                                (fn [err]
                                                  (state/set-error! (str "Erro ao deletar partida: " err))))))
            filtered (->> matches
                          (filter (fn [match]
                                    (if (str/blank? @search)
                                      true
                                      (str/includes? (str/lower-case (str (:opponent match)))
                                                     (str/lower-case @search))))))
            by-champ (group-by (fn [m] (str (or (:championship-id m) ""))) filtered)
            known-ids (set (map (comp str :_id) championships))
            sorted-champs (sort-by (fn [ch] (str/lower-case (str (:name ch)))) championships)
            sort-ms (fn [ms] (reverse (sort-by (fn [m] (or (:date m) "")) ms)))
            orphan-ids (sort (filter #(and (not (contains? known-ids %))
                                           (not (str/blank? %)))
                                     (keys by-champ)))
            no-champ-ms (sort-ms (get by-champ ""))
            has-group-ui? (or (seq sorted-champs) (seq orphan-ids) (seq no-champ-ms))]
        [:div {:class "space-y-6"}
         [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
          [:div
           [:p {:class "text-sm text-slate-500"} "Calendário"]
           [:h2 {:class "text-2xl font-semibold text-slate-900"} "Partidas"]]
          [:div {:class "flex flex-wrap gap-2"}
           [common/button "Nova Partida" #(rfe/push-state :match-new) :variant :primary]
           [common/button "Atualizar"
            #(do (effects/ensure-championships! {:force? true})
                 (effects/ensure-matches! {:force? true}))
            :variant :outline]]]

         [common/card
          [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
           [:input {:type "text"
                    :value @search
                    :placeholder "Buscar adversário..."
                    :on-change #(reset! search (-> % .-target .-value))
                    :class "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm focus:border-brand-maroon focus:outline-none focus:ring-2 focus:ring-brand-maroon/20 sm:w-64"}]
           [:div {:class "text-xs text-slate-500"} (str "Total: " (count filtered) " partidas")]]

          (cond
            matches-error [common/error-message matches-error]
            matches-loading? [common/loading-spinner]
            (not has-group-ui?)
            [:p {:class "app-muted mt-4"} "Nenhuma partida encontrada."]
            :else
            [:div {:class "mt-4 space-y-6"}
             (for [ch sorted-champs]
               (let [cid (str (:_id ch))
                     ms (sort-ms (get by-champ cid []))]
                 ^{:key cid}
                 [common/card
                  [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
                   [:div {:class "min-w-0"}
                    [:h3 {:class "text-lg font-semibold text-slate-900"} (:name ch)]
                    [:p {:class "text-xs text-slate-500"} (str (count ms) " partida(s)")]]
                   [common/button "Nova partida"
                    #(rfe/push-state :match-new-in-championship {:championship-id cid})
                    :variant :outline]]
                  (if (seq ms)
                    [:div {:class "mt-4 space-y-3"}
                     (for [m ms]
                       ^{:key (:_id m)}
                       [match-row m delete-match!])]
                    [:p {:class "app-muted mt-3"} "Nenhuma partida neste campeonato."])]))
             (for [oid orphan-ids]
               (let [ms (sort-ms (get by-champ oid []))]
                 ^{:key (str "orphan-" oid)}
                 [common/card
                  [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
                   [:div {:class "min-w-0"}
                    [:h3 {:class "text-lg font-semibold text-slate-900"} "Outro campeonato"]
                    [:p {:class "text-xs text-slate-400 truncate"} oid]
                    [:p {:class "text-xs text-slate-500"} (str (count ms) " partida(s)")]]]
                  [:div {:class "mt-4 space-y-3"}
                   (for [m ms]
                     ^{:key (:_id m)}
                     [match-row m delete-match!])]]))
             (when (seq no-champ-ms)
               ^{:key "no-championship"}
               [common/card
                [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
                 [:div {:class "min-w-0"}
                  [:h3 {:class "text-lg font-semibold text-slate-900"} "Sem campeonato"]
                  [:p {:class "text-xs text-slate-500"} (str (count no-champ-ms) " partida(s)")]]]
                [:div {:class "mt-4 space-y-3"}
                 (for [m no-champ-ms]
                   ^{:key (:_id m)}
                   [match-row m delete-match!])]])])]]))))

(defn match-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        preset-championship-id (str (or (:championship-id params) ""))
        match-loading? (r/atom is-edit?)
        enrolled-players (r/atom [])
        players-loading? (r/atom false)
        form-data (r/atom {:championship-id (if is-edit? "" preset-championship-id)
                           :home-team-id ""
                           :date ""
                           :opponent ""
                           :away-score ""
                           :venue ""
                           :result ""
                           :player-statistics [{:player-id "" :team-id "" :goals "" :assists "" :minutes-played ""}]})
        submitting? (r/atom false)
        form-error (r/atom nil)
        field-errors (r/atom {})
        parse-int (fn [v]
                    (let [trimmed (str/trim (or v ""))]
                      (when (not-empty trimmed)
                        (js/parseInt trimmed 10))))
        valid-form? (fn []
                      (let [stats (:player-statistics @form-data)
                            errs (cond-> {}
                                    (str/blank? (:championship-id @form-data)) (assoc :championship-id "Selecione um campeonato")
                                    (str/blank? (:date @form-data)) (assoc :date "Informe a data")
                                    (str/blank? (:home-team-id @form-data)) (assoc :home-team-id "Selecione o time (mandante)")
                                    (empty? stats) (assoc :player-statistics "Inclua estatísticas de pelo menos um jogador")
                                    (some #(str/blank? (:player-id %)) stats) (assoc :player-statistics "Cada estatística deve ter um jogador selecionado"))]
                        (when (seq errs) errs)))
        prepare-payload (fn []
                          (let [home-team-id (:home-team-id @form-data)]
                            (-> @form-data
                                (update :away-score #(if (str/blank? (str %)) nil (parse-int %)))
                                (update :player-statistics
                                        (fn [stats]
                                          (mapv (fn [stat]
                                                  (cond-> (assoc stat :team-id home-team-id)
                                                    true (update :player-id clojure.string/trim)
                                                    true (update :goals parse-int)
                                                    true (update :assists parse-int)
                                                    true (update :minutes-played parse-int)))
                                                stats))))))
        load-enrolled! (fn [championship-id]
                         (if (str/blank? championship-id)
                           (reset! enrolled-players [])
                           (do
                             (reset! players-loading? true)
                             (api/get-championship-players
                              championship-id
                              (fn [result]
                                (reset! enrolled-players result)
                                (reset! players-loading? false))
                              (fn [err]
                                (reset! form-error (str "Erro ao carregar jogadores inscritos: " err))
                                (reset! players-loading? false))))))
        load-match! (fn []
                     (when is-edit?
                       (reset! form-error nil)
                       (reset! match-loading? true)
                       (api/get-match id
                                      (fn [result]
                                        (reset! form-data {:championship-id (if (:championship-id result) (str (:championship-id result)) "")
                                                          :home-team-id (if (:home-team-id result) (str (:home-team-id result)) "")
                                                          :date (or (:date result) "")
                                                          :opponent (or (:opponent result) "")
                                                          :away-score (if (some? (:away-score result)) (str (:away-score result)) "")
                                                          :venue (or (:venue result) "")
                                                          :result (or (:result result) "")
                                                          :player-statistics (if (seq (:player-statistics result))
                                                                              (mapv (fn [stat]
                                                                                      {:player-id (if (:player-id stat) (str (:player-id stat)) "")
                                                                                       :team-id (if (:team-id stat) (str (:team-id stat)) "")
                                                                                       :goals (if (:goals stat) (str (:goals stat)) "")
                                                                                       :assists (if (:assists stat) (str (:assists stat)) "")
                                                                                       :minutes-played (if (:minutes-played stat) (str (:minutes-played stat)) "")})
                                                                                    (:player-statistics result))
                                                                              [{:player-id "" :team-id "" :goals "" :assists "" :minutes-played ""}])})
                                        (load-enrolled! (if (:championship-id result) (str (:championship-id result)) ""))
                                        (reset! match-loading? false))
                                      (fn [err]
                                        (reset! form-error (str "Erro ao carregar partida: " err))
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
              team-options (cons ["" "Selecione um time"]
                                 (map (fn [team] [(str (:_id team)) (:name team)]) teams))
              to-int (fn [v]
                       (let [parsed (js/parseInt (or v "0") 10)]
                         (if (js/isNaN parsed) 0 parsed)))
              total-goals (reduce + 0 (map #(to-int (:goals %)) (:player-statistics @form-data)))
              total-assists (reduce + 0 (map #(to-int (:assists %)) (:player-statistics @form-data)))
              home-team-id (:home-team-id @form-data)
              home-score (reduce + 0 (map #(to-int (:goals %)) (:player-statistics @form-data)))
              away-score (to-int (:away-score @form-data))]
          [:div {:class "space-y-6"}
           [:div
            [:p {:class "text-sm text-slate-500"} "Cadastro"]
            [:h2 {:class "text-2xl font-semibold text-slate-900"} (if is-edit? "Editar Partida" "Nova Partida")]]
           (if @match-loading?
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
                                                        (effects/ensure-matches! {:force? true})
                                                        (rfe/push-state :matches))
                                            on-error (fn [error]
                                                      (reset! submitting? false)
                                                      (reset! form-error (str "Erro ao " (if is-edit? "atualizar" "criar") " partida: " error)))]
                                        (if is-edit?
                                          (api/update-match id payload on-success on-error)
                                          (api/create-match payload on-success on-error))))))}
              [common/card
               [:h3 {:class "app-section-title"} "Detalhes da partida"]
               [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
                [common/select-field
                 "Campeonato"
                 (:championship-id @form-data)
                 (cons ["" "Selecione um campeonato"]
                       (map (fn [ch] [(str (:_id ch)) (:name ch)]) championships))
                 (fn [value]
                   (swap! form-data assoc :championship-id value)
                   (load-enrolled! value))
                 :required? true :error (:championship-id @field-errors)]
                [common/select-field "Time mandante" (:home-team-id @form-data) team-options
                 #(swap! form-data assoc :home-team-id %) :error (:home-team-id @field-errors)]
                [common/input-field "Data" (:date @form-data) #(swap! form-data assoc :date %) :type "date" :required? true :error (:date @field-errors)]
                [common/input-field "Adversário" (:opponent @form-data) #(swap! form-data assoc :opponent %)]
                [common/input-field "Gols do adversário" (:away-score @form-data) #(swap! form-data assoc :away-score %) :type "number" :placeholder "0"]
                [common/input-field "Local" (:venue @form-data) #(swap! form-data assoc :venue %)]
                [common/input-field "Resultado" (:result @form-data) #(swap! form-data assoc :result %) :placeholder "Ex: 2-1"]]]

              [common/card
               [:div {:class "flex flex-wrap items-center justify-between gap-3"}
                [:h3 {:class "app-section-title"} "Estatísticas dos jogadores"]
                (when (:player-statistics @field-errors)
                  [:p {:class "text-xs text-rose-600"} (:player-statistics @field-errors)])
                [:div {:class "text-xs text-slate-500"}
                 (str "Gols: " total-goals " • Assistências: " total-assists
                      (when (not (str/blank? home-team-id))
                        (str " • Placar: " home-score " x " away-score)))]]
               (cond
                 (str/blank? (:championship-id @form-data))
                 [:p {:class "mt-3 text-xs text-slate-500"} "Selecione um campeonato para listar jogadores inscritos."]
                 @players-loading?
                 [:p {:class "mt-3 text-xs text-slate-500"} "Carregando jogadores inscritos..."]
                 :else
                 [:div {:class "mt-4 space-y-3"}
                  (doall
                   (for [[idx stat] (map-indexed vector (:player-statistics @form-data))]
                     ^{:key idx}
                     [:div {:class "space-y-3 rounded-xl border border-slate-200 bg-white p-4"}
                      [player-picker/player-search-add-panel
                       {:compact? true
                        :label "Jogador"
                        :players @enrolled-players
                        :exclude-ids #{}
                        :selected-id (when-not (str/blank? (:player-id stat)) (:player-id stat))
                        :action-label "Usar"
                        :on-pick-player
                        (fn [player]
                          (when-let [pid (player-picker/player-id player)]
                            (swap! form-data assoc-in [:player-statistics idx :player-id] pid)))
                        :on-quick-create
                        (fn [name ok err]
                          (let [ch-id (:championship-id @form-data)]
                            (if (str/blank? ch-id)
                              (err "Selecione um campeonato antes de criar jogador.")
                              (quick-create-player-for-match!
                               ch-id idx enrolled-players form-data form-error name ok err))))}]
                      [:div {:class "grid gap-3 md:grid-cols-4 md:items-end"}
                       [common/input-field "Gols" (:goals stat)
                        #(swap! form-data assoc-in [:player-statistics idx :goals] %)
                        :type "number"]
                       [common/input-field "Assistências" (:assists stat)
                        #(swap! form-data assoc-in [:player-statistics idx :assists] %)
                        :type "number"]
                       [common/input-field "Minutos jogados" (:minutes-played stat)
                        #(swap! form-data assoc-in [:player-statistics idx :minutes-played] %)
                        :type "number"]
                       [:div {}
                        [common/button "Remover"
                         #(swap! form-data update :player-statistics
                                 (fn [stats]
                                   (vec (concat (subvec stats 0 idx) (subvec stats (inc idx))))))
                         :variant :danger]]]]))]
               )
               [common/button "Adicionar estatística"
                #(swap! form-data update :player-statistics conj {:player-id "" :goals "" :assists "" :minutes-played ""})
                :variant :outline
                :class "mt-3"
                :disabled (str/blank? (:championship-id @form-data))]]

              (when @form-error
                [common/error-message @form-error])
              [:div {:class "flex flex-wrap gap-2"}
               [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar Partida"))
                nil
                :type "submit"
                :disabled @submitting?
                :variant :primary]
               [common/button "Cancelar" #(rfe/push-state :matches) :variant :outline]]])]))})))
