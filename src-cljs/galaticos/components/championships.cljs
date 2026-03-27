(ns galaticos.components.championships
  "Championship list and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.components.player-picker :as player-picker]
            [galaticos.effects :as effects]
            [clojure.string :as str]
            ["lucide-react" :refer [Trophy]]))

(defn- api-get
  "Read API map field with keyword or string key (JSON interop)."
  [m k]
  (when m
    (or (get m k) (get m (name k)))))

(defn- leaderboard-mini
  "Small ordered list for one metric (rows from /leaderboards API)."
  [label metric-key rows]
  [common/card
   [:h4 {:class "text-sm font-semibold text-slate-800"} label]
   (if (seq rows)
     [:ol {:class "mt-2 list-decimal list-inside space-y-1 text-sm text-slate-700"}
      (for [row rows]
        ^{:key (str (:player-id row) "-" (:name row))}
        [:li
         [:span (:name row)]
         " — "
         [:span {:class "font-medium tabular-nums"} (str (get row metric-key 0))]])]
     [:p {:class "app-muted text-sm mt-2"} "Sem dados"])])

(defn championship-list []
  (let [{:keys [championships championships-loading? championships-error]} @state/app-state]
    [:div {:class "space-y-6"}
     [:div {:class "flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"}
      [:div
       [:p {:class "text-sm text-slate-500"} "Gestão de competições"]
       [:h2 {:class "text-2xl font-semibold text-slate-900"} "Campeonatos"]]
      [:div {:class "flex flex-wrap gap-2"}
       [common/button "Novo Campeonato" #(rfe/push-state :championship-new) :variant :primary]
       [common/button "Atualizar" #(effects/ensure-championships! {:force? true}) :variant :outline]]]
     [common/card
      (cond
        championships-error [common/error-message championships-error]
        championships-loading? [common/loading-spinner]
        (seq championships)
        [common/table
         ["Nome" "Temporada" "Formato" "Status" "Títulos"]
         (map (fn [ch]
                [(api-get ch :name)
                 (api-get ch :season)
                 (api-get ch :format)
                 (let [raw-status (or (api-get ch :status) "indefinido")
                       status (if (= raw-status "finished") "completed" raw-status)
                       variant (case status
                                 "active" :success
                                 "completed" :info
                                 "inactive" :warning
                                 "cancelled" :danger
                                 :info)]
                   [common/badge status :variant variant])
                 (or (api-get ch :titles-count) 0)])
              championships)
         :on-row-click (fn [ch]
                         (if-let [id (:_id ch)]
                           (rfe/push-state :championship-detail {:id id})
                           (state/set-error! "ID do campeonato ausente; não foi possível abrir detalhes.")))
         :row-data championships
         :sortable? true]
        :else [:p {:class "app-muted"} "Nenhum campeonato encontrado"])]]))

(defn championship-detail [params]
  (let [championship (r/atom nil)
        matches (r/atom [])
        seasons (r/atom [])
        enrolled-players (r/atom [])
        all-players (r/atom [])
        players-catalog-ready? (r/atom false)
        selected-winners (r/atom #{})
        titles-award-count (r/atom "1")
        new-season-label (r/atom "")
        finalizing? (r/atom false)
        loading? (r/atom true)
        error (r/atom nil)
        not-found? (r/atom false)
        leaderboards (r/atom nil)
        id (:id params)
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
                                                               (reset! error (str "Erro ao carregar partidas: " err))))))
                                        (reset! loading? false))
                                      (fn [err resp]
                                        (reset! loading? false)
                                        (if (and resp (= 404 (:status resp)))
                                          (do (reset! not-found? true)
                                              (reset! error "Campeonato não encontrado."))
                                          (do (reset! not-found? false)
                                              (reset! error (str "Erro ao carregar campeonato: " err))))))
                (api/get-championship-seasons id
                                              (fn [result]
                                                (reset! seasons result))
                                              (fn [err]
                                                (reset! error (str "Erro ao carregar temporadas: " err))))
                (api/get-championship-players id
                                              (fn [result]
                                                (reset! enrolled-players result))
                                              (fn [err]
                                                (reset! error (str "Erro ao carregar inscritos: " err))))
                (api/get-players {}
                                 (fn [result]
                                   (reset! all-players (api/coerce-player-list result))
                                   (reset! players-catalog-ready? true))
                                 (fn [err]
                                   (reset! players-catalog-ready? true)
                                   (reset! error (str "Erro ao carregar jogadores: " err)))))]
    (r/create-class
     {:component-did-mount (fn [] (load!))
      :reagent-render
      (fn []
        (let [ch @championship
              raw-status (or (api-get ch :status) "indefinido")
              status (if (= raw-status "finished") "completed" raw-status)
              status-variant (fn [v]
                               (case (or v "inactive")
                                 "active" :success
                                 "completed" :info
                                 "inactive" :warning
                                 "cancelled" :danger
                                 :info))
              winner-ids (set (map str (or (api-get ch :winner-player-ids) [])))
              winner-names (->> @enrolled-players
                                (filter #(contains? winner-ids (str (:_id %))))
                                (map :name)
                                (sort))
              enrolled-sorted (sort-by #(str/lower-case (str (:name %))) @enrolled-players)
              enrolled-exclude-ids (into #{} (keep player-picker/player-id @enrolled-players))
              awarded-count (or (api-get ch :titles-award-count) 0)
              active-season-id (api-get ch :active-season-id)
              season-options (map (fn [s]
                                    [(str (:_id s))
                                     (str (:season s) " (" (or (:status s) "active") ")")])
                                  @seasons)]
          [:div {:class "space-y-6"}
           (cond
             @error
             (if @not-found?
               [common/not-found-resource @error #(rfe/push-state :championships)]
               [:div
                [common/error-message @error]
                [common/button "Tentar novamente" load! :variant :outline]])

             @loading?
             [common/loading-spinner]

             @championship
             [:<>
              [:div {:class "flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"}
               [:div {:class "flex items-center gap-3"}
                [:div {:class "rounded-xl bg-brand-maroon/10 p-3 text-brand-maroon"}
                 [:> Trophy {:size 20}]]
                [:div
                 [:p {:class "text-sm text-slate-500"} "Campeonato"]
                 [:h2 {:class "text-2xl font-semibold text-slate-900"} (api-get ch :name)]
                 [common/badge status :variant :info :class "mt-2"]]]
               [:div {:class "flex flex-wrap gap-2"}
                [common/button "Editar" #(rfe/push-state :championship-edit {:id id}) :variant :outline]
                [common/button "Exportar CSV"
                 #(api/download-csv! (str "/api/exports/championships/" id ".csv")
                                     (str (or (api-get ch :name) "campeonato") ".csv")
                                     (fn [] nil)
                                     (fn [err] (js/alert err)))
                 :variant :outline]
                [common/button "Deletar"
                 (fn []
                   (when (js/confirm "Tem certeza que deseja deletar este campeonato?")
                     (api/delete-championship id
                                              (fn [_result]
                                                (effects/ensure-championships! {:force? true})
                                                (rfe/push-state :championships))
                                              (fn [err]
                                                (reset! error (str "Erro ao deletar campeonato: " err))))))
                 :variant :danger]]]

              (when (seq @seasons)
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
                       (reset! error
                               (str "Erro ao ativar temporada: " err)))))
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
                 (when-let [max-players (api-get ch :max-players)]
                   [:p [:span {:class "font-medium text-slate-800"} "Limite de jogadores: "] max-players])
                 (when-let [location (api-get ch :location)]
                   [:p [:span {:class "font-medium text-slate-800"} "Local: "] location])
                 (when-let [notes (api-get ch :notes)]
                   [:p [:span {:class "font-medium text-slate-800"} "Notas: "] notes])
                 [:div {:class "mt-4 space-y-3 border-t border-slate-200 pt-4"}
                  [:h4 {:class "text-sm font-semibold text-slate-800"} "Temporadas"]
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
                            (reset! error (str "Erro ao criar temporada: " err)))))))
                   :variant :outline]
                  (when (seq @seasons)
                    [:div {:class "mt-2"}
                     [common/table
                      ["Temporada" "Detalhes" "Status" "Inscritos" "Partidas" "Títulos" "Ações"]
                      (map (fn [s]
                             (let [sid (str (:_id s))
                                   s-status (or (:status s) "inactive")
                                   enrolled-count (count (or (:enrolled-player-ids s) []))
                                   match-n (count (or (:match-ids s) []))
                                   titles (or (:titles-count s) 0)
                                   is-active? (= sid (str active-season-id))
                                   detail-parts (filter seq
                                                     [(or (:format s) "")
                                                      (when (:start-date s) (str "Início: " (:start-date s)))
                                                      (when (:end-date s) (str "Fim: " (:end-date s)))])]
                               [(:season s)
                                [:span {:class "text-xs text-slate-600"} (if (seq detail-parts)
                                                                           (str/join " · " detail-parts)
                                                                           "—")]
                                [common/badge s-status :variant (status-variant s-status)]
                                enrolled-count
                                match-n
                                titles
                                [:div {:class "flex flex-wrap gap-1 justify-end"}
                                 [common/button
                                  "Ver"
                                  #(rfe/push-state :championship-season-detail {:id id :season-id sid})
                                  :variant :outline]
                                 (if is-active?
                                   [common/badge "Ativa" :variant :success]
                                   [common/button
                                    "Ativar"
                                    (fn []
                                      (api/activate-season
                                       sid
                                       (fn [_] (load!))
                                       (fn [err]
                                         (reset! error (str "Erro ao ativar temporada: " err)))))
                                    :variant :outline])]]))
                           (sort-by (fn [s] (str (:season s))) @seasons))
                      :dense? true
                      :show-search? false
                      :sortable? true]])]]]
               [common/card
                [:h3 {:class "app-section-title"} "Partidas"]
                (if (seq @matches)
                  [common/table
                   ["Data" "Adversário" "Local" "Resultado"]
                   (map (fn [match]
                          [(.toLocaleDateString (js/Date. (:date match)))
                           (:opponent match)
                           (:venue match)
                           (common/format-match-result (:result match))])
                        @matches)
                   :sortable? true
                   :dense? true]
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
               [:h3 {:class "app-section-title"} "Inscrições"]
               [:div {:class "mt-3 space-y-3"}
                [player-picker/player-search-add-panel
                 {:label "Adicionar jogador"
                  :players @all-players
                  :players-loading? (not @players-catalog-ready?)
                  :exclude-ids enrolled-exclude-ids
                  :action-label "Inscrever"
                  :on-pick-player
                  (fn [player]
                    (when-let [pid (player-picker/player-id player)]
                      (api/enroll-player-in-championship
                       id pid
                       (fn [_result]
                         (api/get-championship-players id
                                                       #(reset! enrolled-players %)
                                                       #(reset! error (str "Erro ao carregar inscritos: " %))))
                       (fn [err]
                         (reset! error (str "Erro ao inscrever jogador: " err))))))
                  :on-quick-create
                  (fn [name ok err]
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
                                 (reset! enrolled-players rows)
                                 (ok created))
                               (fn [e]
                                 (reset! error (str "Erro ao carregar inscritos: " e))
                                 (err (str "Erro ao carregar inscritos: " e)))))
                            (fn [e]
                              (reset! error (str "Erro ao inscrever novo jogador: " e))
                              (err (str "Erro ao inscrever novo jogador: " e)))))
                         (err "Jogador criado sem ID retornado.")))
                     (fn [e]
                       (err (str "Erro ao criar jogador: " e)))))}]
                (if (seq @enrolled-players)
                  [:div {:class "space-y-2"}
                   (for [player enrolled-sorted]
                     ^{:key (:_id player)}
                     [:div {:class "flex items-center justify-between rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm"}
                      [:div {:class "text-slate-700"} (:name player)]
                      [common/button "Remover"
                       (fn []
                         (api/unenroll-player-from-championship
                          id
                          (str (:_id player))
                          (fn [_result]
                            (api/get-championship-players id
                                                          #(reset! enrolled-players %)
                                                          #(reset! error (str "Erro ao carregar inscritos: " %))))
                          (fn [err]
                            (reset! error (str "Erro ao desinscrever jogador: " err)))))
                       :variant :danger]])]
                  [:p {:class "app-muted"} "Nenhum jogador inscrito"])]
               [:div {:class "mt-4 border-t border-slate-200 pt-4"}
                [:h4 {:class "text-sm font-semibold text-slate-800"} "Finalização do campeonato"]
                (cond
                  (not= status "active")
                  [:p {:class "mt-2 text-xs text-slate-500"} "Apenas campeonatos ativos podem ser finalizados."]

                  (seq winner-ids)
                  [:p {:class "mt-2 text-xs text-slate-500"}
                   (str "Vencedores: " (if (seq winner-names) (str/join ", " winner-names) "—")
                        (when (pos? awarded-count) (str " • Títulos concedidos: " awarded-count)))]

                  :else
                  [:div {:class "mt-3 space-y-2"}
                   [common/input-field "Títulos a conceder" @titles-award-count #(reset! titles-award-count %)
                    :type "number" :placeholder "1"]
                   (if (seq @enrolled-players)
                     [:div {:class "space-y-2"}
                      (for [player enrolled-sorted]
                        ^{:key (:_id player)}
                        [:label {:class "flex items-center gap-2 text-xs text-slate-700"}
                         [:input {:type "checkbox"
                                  :checked (contains? @selected-winners (str (:_id player)))
                                  :on-change #(swap! selected-winners
                                                     (fn [current]
                                                       (let [pid (str (:_id player))]
                                                         (if (contains? current pid)
                                                           (disj current pid)
                                                           (conj current pid)))))}]
                         [:span (:name player)]])]
                     [:p {:class "text-xs text-slate-500"} "Inscreva jogadores antes de finalizar."])
                   (let [count-num (let [v @titles-award-count]
                                     (if (str/blank? v) 0 (js/parseInt v 10)))
                         need-winners? (and (number? count-num) (pos? count-num))
                         can-submit? (or (not need-winners?) (seq @selected-winners))]
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
                             (load!))
                           (fn [err]
                             (reset! finalizing? false)
                             (reset! error (str "Erro ao finalizar campeonato: " err))))))
                      :variant :primary
                      :disabled (or @finalizing? (not can-submit?))])])]]]

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
        status-variant (fn [v]
                         (case (or v "inactive")
                           "active" :success
                           "completed" :info
                           "inactive" :warning
                           "cancelled" :danger
                           :info))
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
                                                                               (reset! error (str "Erro ao carregar partidas: " err)))))
                                                          (fn [err]
                                                            (reset! loading? false)
                                                            (reset! error (str "Erro ao carregar inscritos: " err)))))
                                (fn [err resp]
                                  (reset! loading? false)
                                  (if (and resp (= 404 (:status resp)))
                                    (reset! error "Temporada não encontrada.")
                                    (reset! error (str "Erro ao carregar temporada: " err))))))]
    (r/create-class
     {:component-did-mount (fn [] (load!))
      :reagent-render
      (fn []
        (let [s-status (or (when @season (:status @season)) "inactive")
              raw (or (when @season (:status @season)) "indefinido")
              disp-status (if (= raw "finished") "completed" raw)
              enrolled-sorted (sort-by #(str/lower-case (str (:name %))) @players)]
          [:div {:class "space-y-6"}
           [common/button "Voltar ao campeonato"
            #(rfe/push-state :championship-detail {:id cid})
            :variant :outline]
           (cond
             @error
             [:div [common/error-message @error]
              [common/button "Tentar novamente" load! :variant :outline]]

             @loading?
             [common/loading-spinner]

             @season
             [:<>
              [:div
               [:p {:class "text-sm text-slate-500"} "Temporada"]
               [:h2 {:class "text-2xl font-semibold text-slate-900"}
                (str (:championship-name @season) " · " (:season @season))]
               [common/badge disp-status :variant (status-variant s-status) :class "mt-2"]]
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
                    ^{:key (:_id p)}
                    [:li {:class "py-2"} (:name p)])]
                 [:p {:class "app-muted"} "Nenhum jogador inscrito nesta temporada."])]
              [common/card
               [:h3 {:class "app-section-title"} "Partidas"]
               (if (seq @matches)
                 [common/table
                  ["Data" "Adversário" "Local" "Resultado"]
                  (map (fn [match]
                         [(if-let [d (:date match)]
                            (.toLocaleDateString (js/Date. d))
                            "—")
                          (:opponent match)
                          (:venue match)
                          (common/format-match-result (:result match))])
                       @matches)
                  :sortable? true
                  :dense? true]
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
                                                     (reset! form-error (str "Erro ao carregar campeonato: " err))
                                                     (reset! championship-loading? false)))))]
    (r/create-class
     {:component-did-mount load-championship!
      :reagent-render
      (fn []
        [:div {:class "space-y-6"}
         [:div
          [:p {:class "text-sm text-slate-500"} "Cadastro"]
          [:h2 {:class "text-2xl font-semibold text-slate-900"} (if is-edit? "Editar Campeonato" "Novo Campeonato")]]
         (if @championship-loading?
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
                                                      (effects/ensure-championships! {:force? true})
                                                      (rfe/push-state :championships))
                                          on-error (fn [error]
                                                    (reset! submitting? false)
                                                    (reset! form-error (str "Erro ao " (if is-edit? "atualizar" "criar") " campeonato: " error)))]
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

            (when @form-error
              [common/error-message @form-error])
            [:div {:class "flex flex-wrap gap-2"}
             [common/button (if @submitting? "Salvando..." (if is-edit? "Atualizar" "Criar"))
              nil
              :type "submit"
              :disabled @submitting?
              :variant :primary]
             [common/button "Cancelar" #(rfe/push-state :championships) :variant :outline]]])])})))

