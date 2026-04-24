(ns galaticos.components.players
  "Player list and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.components.charts :as charts]
            [galaticos.effects :as effects]
            [clojure.string :as str]
            ["lucide-react" :refer [Grid2X2 ListFilter ChartColumn]]))

(defn- normalize-id [v]
  (cond
    (string? v) v
    (map? v) (or (get v "$oid") (get v :$oid))
    :else nil))

(defn- looks-like-object-id? [s]
  (and (string? s)
       (= 24 (count s))
       (boolean (re-matches #"^[0-9a-fA-F]{24}$" s))))

(defn- display-team-name [name-from-player name-from-teams]
  (let [raw (or name-from-player name-from-teams)]
    (when-not (looks-like-object-id? raw)
      raw)))

(defn- position-options [players]
  (->> players
       (map :position)
       (filter some?)
       set
       sort
       (map (fn [pos] [pos pos]))
       (cons ["" "Todas as posições"])))

(defn player-list []
  (let [view-mode (r/atom :table)
        search (r/atom "")
        position (r/atom "")
        page (r/atom 1)
        page-size 25
        position-catalog (r/atom [])
        search-backend!
        (fn []
          (state/set-resource-loading! :players true)
          (let [params (cond-> {:page @page
                                :limit page-size}
                         (not (str/blank? @search)) (assoc :q @search)
                         (not (str/blank? @position)) (assoc :position @position))]
            (api/search-players
             params
             (fn [result]
               (state/set-players! result))
             (fn [err _resp]
               (state/set-resource-error! :players err)))))]
    (r/create-class
     {:display-name "galaticos.player-list"
      :component-did-mount
      (fn [_]
        (when-let [q (get-in @state/app-state [:route-match :query-params :q])]
          (when-not (str/blank? (str q))
            (reset! page 1)
            (reset! search (str q))))
        (api/get-players {}
                         (fn [result]
                           (reset! position-catalog (api/coerce-player-list result)))
                         (fn [_err _resp]
                           (reset! position-catalog [])))
        (search-backend!))
      :reagent-render
      (fn [_]
        (let [{:keys [authenticated players players-loading?]} @state/app-state
              positions (position-options (if (seq @position-catalog) @position-catalog players))]
          [:div {:class "space-y-6"}
           [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
            [:div
             [:p {:class "text-sm text-slate-500"} "Gestão do elenco"]
             [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} "Jogadores"]]
            (when authenticated
              [:div {:class "flex flex-wrap gap-2"}
               [common/button "Novo Jogador" #(rfe/push-state :player-new) :variant :primary]])]

           [common/card
            [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
             [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-center"}
              [:input {:type "text"
                       :value @search
                       :placeholder "Buscar jogador..."
                       :on-change (fn [e]
                                    (reset! page 1)
                                    (reset! search (-> e .-target .-value))
                                    (search-backend!))
                       :class "w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm focus:border-brand-maroon focus:outline-none focus:ring-2 focus:ring-brand-maroon/20 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 sm:w-64"}]
              [common/select-field "Posição" @position positions (fn [v]
                                                                   (reset! page 1)
                                                                   (reset! position v)
                                                                   (search-backend!))
               :container-class "min-w-[200px]"]]
             [:div {:class "flex items-center gap-2"}
              [:button {:class (common/merge-classes "rounded-lg border px-3 py-2 text-sm"
                                                    (if (= @view-mode :table)
                                                      "bg-brand-maroon text-white border-brand-maroon"
                                                      "border-slate-200 text-slate-600 hover:bg-slate-100"))
                        :on-click #(reset! view-mode :table)}
               [:> ListFilter {:size 16}]]
              [:button {:class (common/merge-classes "rounded-lg border px-3 py-2 text-sm"
                                                    (if (= @view-mode :cards)
                                                      "bg-brand-maroon text-white border-brand-maroon"
                                                      "border-slate-200 text-slate-600 hover:bg-slate-100"))
                        :on-click #(reset! view-mode :cards)}
               [:> Grid2X2 {:size 16}]]]]

            (cond
              players-loading? [common/loading-spinner]
              (seq players)
              [:div {:class "space-y-4"}
               (if (= @view-mode :cards)
                 [:div {:class "mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"}
                  (for [player players]
                    (let [id (normalize-id (or (:_id player) (:id player)))]
                      ^{:key id}
                      [:div {:class "app-card p-4 transition hover:shadow-md"
                             :on-click #(when id (rfe/push-state :player-detail {:id id}))}
                       [:div {:class "flex items-start gap-3"}
                        [:div {:class "h-12 w-12 overflow-hidden rounded-xl bg-slate-100"}
                         (when-let [photo (:photo-url player)]
                           [:img {:src photo :alt (:name player) :class "h-full w-full object-cover"}])]
                        [:div {:class "flex-1"}
                         [:p {:class "text-base font-semibold text-slate-900 dark:text-slate-100"} (:name player)]
                         [:p {:class "text-xs text-slate-500"} (or (:nickname player) "-")]
                         [common/badge (:position player) :variant :info :class "mt-2"]]]
                       [:div {:class "mt-4 grid grid-cols-3 gap-2 text-center text-xs text-slate-600"}
                        [:div
                         [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} (get-in player [:aggregated-stats :total :games] 0)]
                         [:p "Partidas"]]
                        [:div
                         [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} (get-in player [:aggregated-stats :total :goals] 0)]
                         [:p "Gols"]]
                        [:div
                         [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} (get-in player [:aggregated-stats :total :assists] 0)]
                         [:p "Assistências"]]]]))]
                 [common/table
                  ["Nome" "Apelido" "Posição" "Partidas" "Gols" "Assistências"]
                  (map (fn [player]
                         [(:name player)
                          (:nickname player)
                          [common/badge (:position player) :variant :info]
                          (get-in player [:aggregated-stats :total :games] 0)
                          (get-in player [:aggregated-stats :total :goals] 0)
                          (get-in player [:aggregated-stats :total :assists] 0)])
                       players)
                  :on-row-click (fn [player]
                                  (if-let [id (normalize-id (or (:_id player) (:id player)))]
                                    (rfe/push-state :player-detail {:id id})
                                    (state/set-error! "ID do jogador ausente; não foi possível abrir detalhes.")))
                  :row-data players
                  :sortable? true
                  :show-search? false])
               (when-not players-loading?
                 (let [full-page? (= (count players) page-size)]
                   [:div {:class "flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-4"}
                    [:p {:class "text-sm text-slate-500"} (str "Página " @page " • " (count players) " jogadores")]
                    [:div {:class "flex flex-wrap gap-2"}
                     [common/button "Anterior"
                      (fn []
                        (when (> @page 1)
                          (swap! page dec)
                          (search-backend!)))
                      :variant :outline
                      :disabled (<= @page 1)]
                     [common/button "Próxima"
                      (fn []
                        (when full-page?
                          (swap! page inc)
                          (search-backend!)))
                      :variant :outline
                      :disabled (not full-page?)]]]))]
              :else [:p {:class "app-muted"} "Nenhum jogador encontrado"])]]))})))

(defn- format-evolution-period [id]
  (when (map? id)
    (let [y (:year id)
          m (:month id)
          w (:week id)]
      (cond
        (and y m) (str y "-" (if (< m 10) (str "0" m) m))
        (and y w) (str y " (sem. " w ")")
        y (str y)
        :else "-"))))

(defn player-detail [params]
  (let [player (r/atom nil)
        loading? (r/atom true)
        error (r/atom nil)
        not-found? (r/atom false)
        deleting? (r/atom false)
        active-tab (r/atom :info)
        evolution (r/atom nil)
        evolution-loading? (r/atom false)
        id (:id params)
        load-player! (fn []
                       (reset! error nil)
                       (reset! not-found? false)
                       (reset! loading? true)
                       (reset! evolution nil)
                       (api/get-player-detail-bundle
                        id
                        (fn [bundle]
                          (reset! player (:player bundle))
                          (reset! evolution (or (:evolution bundle) []))
                          (reset! loading? false)
                          (reset! evolution-loading? false))
                        (fn [err resp]
                          (reset! loading? false)
                          (reset! evolution-loading? false)
                          (if (and resp (= 404 (:status resp)))
                            (do (reset! not-found? true)
                                (reset! error "Jogador não encontrado."))
                            (let [msg (str "Erro ao carregar jogador: " err)]
                              (reset! not-found? false)
                              (reset! error msg)
                              (state/toast-error! msg))))))
        delete-player! (fn []
                         (when (js/confirm "Tem certeza que deseja deletar este jogador?")
                           (reset! deleting? true)
                           (api/delete-player id
                                             (fn [_result]
                                               (reset! deleting? false)
                                               (rfe/push-state :players))
                                             (fn [err]
                                               (reset! deleting? false)
                                               (let [msg (str "Erro ao deletar jogador: " err)]
                                                 (reset! error msg)
                                                 (state/toast-error! msg))))))]
    (r/create-class
     {:component-did-mount (fn []
                             (effects/ensure-teams!)
                             (load-player!))
      :reagent-render
      (fn []
        (cond
          @error (if @not-found?
                   [common/not-found-resource @error #(rfe/push-state :players)]
                   [:div {:class "space-y-4"}
                    [common/button "Tentar novamente" load-player! :variant :outline]])
          @loading? [common/loading-spinner]
          @player (let [{:keys [authenticated teams]} @state/app-state
                        player-stats (get-in @player [:aggregated-stats :total] {})
                        team-id (normalize-id (:team-id @player))
                        team-name (display-team-name
                                   (:team-name @player)
                                   (some->> teams
                                            (filter #(= (normalize-id (or (:_id %) (:id %))) team-id))
                                            first
                                            :name))
                        by-champ (get-in @player [:aggregated-stats :by-championship])]
                    [:div {:class "space-y-6"}
                     [:div {:class "flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"}
                      [:div {:class "flex items-center gap-4"}
                       [:div {:class "h-16 w-16 overflow-hidden rounded-2xl bg-slate-100"}
                        (when-let [photo (:photo-url @player)]
                          [:img {:src photo :alt (:name @player) :class "h-full w-full object-cover"}])]
                       [:div
                        [:p {:class "text-sm text-slate-500"} "Jogador"]
                        [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} (:name @player)]
                        [common/badge (:position @player) :variant :info :class "mt-2"]]]
                      (when authenticated
                        [:div {:class "flex flex-wrap gap-2"}
                         [common/button "Editar" #(rfe/push-state :player-edit {:id id}) :variant :outline]
                         [common/button "Deletar" delete-player! :variant :danger :disabled @deleting?]])]

                     [:div {:class "grid gap-4 md:grid-cols-2 xl:grid-cols-4"}
                      [common/stat-card "Partidas" (get player-stats :games 0) :icon [:> ChartColumn {:size 18}]]
                      [common/stat-card "Gols" (get player-stats :goals 0) :icon [:> ChartColumn {:size 18}]]
                      [common/stat-card "Assistências" (get player-stats :assists 0) :icon [:> ChartColumn {:size 18}]]
                      [common/stat-card "Títulos" (get player-stats :titles 0) :icon [:> ChartColumn {:size 18}]]]

                     [common/card
                      [:div {:class "flex flex-wrap items-center justify-between gap-2"}
                       [:div {:class "flex gap-2"}
                        [common/button "Informações" #(reset! active-tab :info) :variant (if (= @active-tab :info) :primary :outline)]
                        [common/button "Estatísticas" #(reset! active-tab :stats) :variant (if (= @active-tab :stats) :primary :outline)]]]
                      (case @active-tab
                        :info
                        [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
                         [:div {:class "space-y-2 text-sm text-slate-600"}
                          [:p [:span {:class "font-medium text-slate-800"} "Apelido: "] (or (:nickname @player) "-")]
                          [:p [:span {:class "font-medium text-slate-800"} "Time: "] (or team-name "Time nao informado")]
                          [:p [:span {:class "font-medium text-slate-800"} "Data de Nascimento: "] (or (:birth-date @player) "-")]
                          [:p [:span {:class "font-medium text-slate-800"} "Nacionalidade: "] (or (:nationality @player) "-")]]
                         [:div {:class "space-y-2 text-sm text-slate-600"}
                          [:p [:span {:class "font-medium text-slate-800"} "Altura: "] (or (:height @player) "-")]
                          [:p [:span {:class "font-medium text-slate-800"} "Peso: "] (or (:weight @player) "-")]
                          [:p [:span {:class "font-medium text-slate-800"} "Pé Preferido: "] (or (:preferred-foot @player) "-")]
                          [:p [:span {:class "font-medium text-slate-800"} "Número: "] (or (:shirt-number @player) "-")]]]
                        :stats
                        [:div {:class "mt-4 space-y-6"}
                         [common/card
                          [:h3 {:class "app-section-title"} "Performance por campeonato"]
                          (if (seq by-champ)
                            [charts/bar-chart {:data (map (fn [entry] {:name (:championship-name entry) :value (:goals entry)})
                                                          by-champ)
                                               :x-key "name"
                                               :y-key "value"
                                               :fill "#3B82F6"}]
                            [:p {:class "app-muted"} "Nenhuma estatística por campeonato"])]
                         (if (seq by-champ)
                           [common/table
                            ["Campeonato" "Partidas" "Gols" "Assistências"]
                            (map (fn [ch-stats]
                                   [(:championship-name ch-stats)
                                    (:games ch-stats)
                                    (:goals ch-stats)
                                    (:assists ch-stats)])
                                 by-champ)
                            :sortable? true
                            :dense? true]
                           [:p {:class "app-muted"} "Nenhuma estatística por campeonato"])
                         [common/card
                          [:h3 {:class "app-section-title"} "Evolução por período"]
                          (cond
                            @evolution-loading? [common/loading-spinner]
                            (seq @evolution)
                            [common/table
                             ["Período" "Partidas" "Gols" "Assistências" "Gols/Partida"]
                             (map (fn [row]
                                    [(format-evolution-period (:_id row))
                                     (:games row)
                                     (:goals row)
                                     (:assists row)
                                     (if (number? (:goals-per-game row))
                                       (.toFixed (:goals-per-game row) 2)
                                       "-")])
                                   @evolution)
                             :sortable? true
                             :dense? true]
                            :else [:p {:class "app-muted"} "Nenhum dado de evolução"])]]
                        [:p {:class "app-muted"} "Selecione uma aba"])
                      ]
                     ])
          :else [:p {:class "app-muted"} "Jogador não encontrado"]))
      })))

(defn player-form [params]
  (let [id (:id params)
        is-edit? (some? id)
        teams (r/atom [])
        teams-loading? (r/atom true)
        player-loading? (r/atom is-edit?)
        form-data (r/atom {:name ""
                          :position ""
                          :nickname ""
                          :team-id ""
                          :birth-date ""
                          :nationality ""
                          :height ""
                          :weight ""
                          :preferred-foot ""
                          :shirt-number ""
                          :email ""
                          :phone ""
                          :photo-url ""
                          :notes ""})
        submitting? (r/atom false)
        form-error (r/atom nil)
        field-errors (r/atom {})
        valid-form? (fn []
                      (let [errs (cond-> {}
                                   (str/blank? (:name @form-data)) (assoc :name "Nome é obrigatório")
                                   (str/blank? (:position @form-data)) (assoc :position "Posição é obrigatória")
                                   (and (not (str/blank? (:height @form-data)))
                                        (js/isNaN (js/parseFloat (:height @form-data)))) (assoc :height "Altura deve ser um número")
                                   (and (not (str/blank? (:weight @form-data)))
                                        (js/isNaN (js/parseFloat (:weight @form-data)))) (assoc :weight "Peso deve ser um número")
                                   (and (not (str/blank? (:shirt-number @form-data)))
                                        (js/isNaN (js/parseInt (:shirt-number @form-data) 10))) (assoc :shirt-number "Número da camisa deve ser um número"))]
                        (when (seq errs) errs)))
        prepare-payload (fn []
                         (let [base {:name (str/trim (:name @form-data))
                                    :position (str/trim (:position @form-data))}
                               optional (fn [k]
                                         (when-let [v (get @form-data k)]
                                           (when-not (str/blank? v)
                                             {k (str/trim v)})))]
                           (merge base
                                  (optional :nickname)
                                  (when-not (str/blank? (:team-id @form-data))
                                    {:team-id (str/trim (:team-id @form-data))})
                                  (optional :birth-date)
                                  (optional :nationality)
                                  (when-let [h (:height @form-data)]
                                    (when-not (str/blank? h)
                                      {:height (js/parseFloat h)}))
                                  (when-let [w (:weight @form-data)]
                                    (when-not (str/blank? w)
                                      {:weight (js/parseFloat w)}))
                                  (optional :preferred-foot)
                                  (when-let [sn (:shirt-number @form-data)]
                                    (when-not (str/blank? sn)
                                      {:shirt-number (js/parseInt sn 10)}))
                                  (optional :email)
                                  (optional :phone)
                                  (optional :photo-url)
                                  (optional :notes))))
        load-data! (fn []
                    (api/get-teams
                     (fn [result]
                       (let [normalized (cond
                                          (vector? result) result
                                          (sequential? result) (vec result)
                                          (map? result) [result]
                                          (nil? result) []
                                          :else [])]
                         (reset! teams normalized))
                       (reset! teams-loading? false))
                     (fn [err]
                       (let [msg (str "Erro ao carregar times: " err)]
                         (reset! form-error msg)
                         (state/toast-error! msg))
                       (reset! teams-loading? false)))
                    (when is-edit?
                      (api/get-player id
                                     (fn [result]
                                       (reset! form-data {:name (or (:name result) "")
                                                          :position (or (:position result) "")
                                                          :nickname (or (:nickname result) "")
                                                          :team-id (if (:team-id result) (str (:team-id result)) "")
                                                          :birth-date (or (:birth-date result) "")
                                                          :nationality (or (:nationality result) "")
                                                          :height (if (:height result) (str (:height result)) "")
                                                          :weight (if (:weight result) (str (:weight result)) "")
                                                          :preferred-foot (or (:preferred-foot result) "")
                                                          :shirt-number (if (:shirt-number result) (str (:shirt-number result)) "")
                                                          :email (or (:email result) "")
                                                          :phone (or (:phone result) "")
                                                          :photo-url (or (:photo-url result) "")
                                                          :notes (or (:notes result) "")})
                                       (reset! player-loading? false))
                                     (fn [err]
                                       (let [msg (str "Erro ao carregar jogador: " err)]
                                         (reset! form-error msg)
                                         (state/toast-error! msg))
                                       (reset! player-loading? false)))))]
    (r/create-class
     {:component-did-mount load-data!
      :reagent-render
      (fn []
        [:div {:class "space-y-6"}
         [:div
          [:p {:class "text-sm text-slate-500"} "Cadastro"]
          [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} (if is-edit? "Editar Jogador" "Novo Jogador")]]
         (if (or @player-loading? @teams-loading?)
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
                                                      (effects/ensure-players! {:force? true})
                                                      (rfe/push-state :players))
                                          on-error (fn [error]
                                                    (reset! submitting? false)
                                                    (let [msg (str "Erro ao " (if is-edit? "atualizar" "criar") " jogador: " error)]
                                                      (reset! form-error msg)
                                                      (state/toast-error! msg)))]
                                      (if is-edit?
                                        (api/update-player id payload on-success on-error)
                                        (api/create-player payload on-success on-error))))))}
            [common/card
             [:h3 {:class "app-section-title"} "Informações básicas"]
             [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
              [common/input-field "Nome" (:name @form-data) #(swap! form-data assoc :name %) :placeholder "Nome completo" :required? true :error (:name @field-errors)]
              [common/input-field "Apelido" (:nickname @form-data) #(swap! form-data assoc :nickname %)]
              [common/input-field "Posição" (:position @form-data) #(swap! form-data assoc :position %) :placeholder "Ex: Atacante, Meia, Zagueiro" :required? true :error (:position @field-errors)]
              (let [teams-seq @teams
                    options (cons ["" "Selecione um time"]
                                  (map (fn [team]
                                         [(str (:_id team)) (:name team)])
                                       teams-seq))]
                [common/select-field
                 "Time"
                 (:team-id @form-data)
                 options
                 #(swap! form-data assoc :team-id %)])]]

            [common/card
             [:h3 {:class "app-section-title"} "Dados pessoais"]
             [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
              [common/input-field "Data de Nascimento" (:birth-date @form-data) #(swap! form-data assoc :birth-date %) :type "date"]
              [common/input-field "Nacionalidade" (:nationality @form-data) #(swap! form-data assoc :nationality %)]
              [common/input-field "Altura (cm)" (:height @form-data) #(swap! form-data assoc :height %) :type "number" :error (:height @field-errors)]
              [common/input-field "Peso (kg)" (:weight @form-data) #(swap! form-data assoc :weight %) :type "number" :error (:weight @field-errors)]]]

            [common/card
             [:h3 {:class "app-section-title"} "Contato e observações"]
             [:div {:class "mt-4 grid gap-4 md:grid-cols-2"}
              [common/input-field "Pé Preferido" (:preferred-foot @form-data) #(swap! form-data assoc :preferred-foot %) :placeholder "Esquerdo, Direito"]
              [common/input-field "Número da Camisa" (:shirt-number @form-data) #(swap! form-data assoc :shirt-number %) :type "number" :error (:shirt-number @field-errors)]
              [common/input-field "Email" (:email @form-data) #(swap! form-data assoc :email %) :type "email"]
              [common/input-field "Telefone" (:phone @form-data) #(swap! form-data assoc :phone %) :type "tel"]
              [common/input-field "URL da Foto" (:photo-url @form-data) #(swap! form-data assoc :photo-url %) :type "url" :container-class "md:col-span-2"]
              [common/input-field "Notas" (:notes @form-data) #(swap! form-data assoc :notes %) :placeholder "Observações adicionais" :container-class "md:col-span-2"]]]

            [:div {:class "flex flex-wrap gap-2"}
             [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar"))
              nil
              :type "submit"
              :disabled @submitting?
              :variant :primary]
             [common/button "Cancelar" #(rfe/push-state :players) :variant :outline]]])])})))

