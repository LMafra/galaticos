(ns galaticos.components.championships
  "Championship list and detail components"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.state :as state]
            [galaticos.components.common :as common]
            [galaticos.effects :as effects]
            [clojure.string :as str]
            ["lucide-react" :refer [Trophy]]))

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
                [(:name ch)
                 (:season ch)
                 (:format ch)
                 (let [raw-status (or (:status ch) "indefinido")
                       status (if (= raw-status "finished") "completed" raw-status)
                       variant (case status
                                 "active" :success
                                 "completed" :info
                                 "inactive" :warning
                                 "cancelled" :danger
                                 :info)]
                   [common/badge status :variant variant])
                 (or (:titles-count ch) 0)])
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
        enrolled-players (r/atom [])
        all-players (r/atom [])
        enroll-id (r/atom "")
        selected-winners (r/atom #{})
        titles-award-count (r/atom "1")
        finalizing? (r/atom false)
        loading? (r/atom true)
        error (r/atom nil)
        not-found? (r/atom false)
        id (:id params)
        load! (fn []
                (reset! error nil)
                (reset! not-found? false)
                (reset! loading? true)
                (api/get-championship id
                                      (fn [result]
                                        (reset! championship result)
                                        (reset! selected-winners (set (map str (:winner-player-ids result))))
                                        (reset! loading? false))
                                      (fn [err resp]
                                        (reset! loading? false)
                                        (if (and resp (= 404 (:status resp)))
                                          (do (reset! not-found? true)
                                              (reset! error "Campeonato não encontrado."))
                                          (do (reset! not-found? false)
                                              (reset! error (str "Erro ao carregar campeonato: " err))))))
                (api/get-matches {:championship-id id}
                                 (fn [result]
                                   (reset! matches result))
                                 (fn [err]
                                   (reset! error (str "Erro ao carregar partidas: " err))))
                (api/get-championship-players id
                                              (fn [result]
                                                (reset! enrolled-players result))
                                              (fn [err]
                                                (reset! error (str "Erro ao carregar inscritos: " err))))
                (api/get-players {}
                                 (fn [result]
                                   (reset! all-players result))
                                 (fn [err]
                                   (reset! error (str "Erro ao carregar jogadores: " err)))))]
    (r/create-class
     {:component-did-mount (fn [] (load!))
      :reagent-render
      (fn []
        (let [raw-status (or (:status @championship) "indefinido")
              status (if (= raw-status "finished") "completed" raw-status)
              winner-ids (set (map str (:winner-player-ids @championship)))
              winner-names (->> @enrolled-players
                                (filter #(contains? winner-ids (str (:_id %))))
                                (map :name))
              awarded-count (or (:titles-award-count @championship) 0)]
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
                 [:h2 {:class "text-2xl font-semibold text-slate-900"} (:name @championship)]
                 [common/badge status :variant :info :class "mt-2"]]]
               [:div {:class "flex flex-wrap gap-2"}
                [common/button "Editar" #(rfe/push-state :championship-edit {:id id}) :variant :outline]
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

              [:div {:class "grid gap-4 md:grid-cols-2"}
               [common/card
                [:h3 {:class "app-section-title"} "Detalhes"]
                [:div {:class "mt-3 space-y-2 text-sm text-slate-600"}
                 [:p [:span {:class "font-medium text-slate-800"} "Temporada: "] (:season @championship)]
                 [:p [:span {:class "font-medium text-slate-800"} "Formato: "] (:format @championship)]
                 [:p [:span {:class "font-medium text-slate-800"} "Títulos: "] (or (:titles-count @championship) 0)]
                 (when-let [max-players (:max-players @championship)]
                   [:p [:span {:class "font-medium text-slate-800"} "Limite de jogadores: "] max-players])
                 (when-let [location (:location @championship)]
                   [:p [:span {:class "font-medium text-slate-800"} "Local: "] location])
                 (when-let [notes (:notes @championship)]
                   [:p [:span {:class "font-medium text-slate-800"} "Notas: "] notes])]]
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

              [common/card
               [:h3 {:class "app-section-title"} "Inscrições"]
               [:div {:class "mt-3 space-y-3"}
                [:div {:class "flex flex-col gap-2 sm:flex-row sm:items-end"}
                 [common/select-field
                  "Adicionar jogador"
                  @enroll-id
                  (cons ["" "Selecione um jogador"]
                        (map (fn [player]
                               [(str (:_id player)) (:name player)])
                             @all-players))
                  #(reset! enroll-id %)
                  :container-class "min-w-[240px]"]
                 [common/button "Inscrever"
                  (fn []
                    (when-not (str/blank? @enroll-id)
                      (api/enroll-player-in-championship
                       id
                       @enroll-id
                       (fn [_result]
                         (reset! enroll-id "")
                         (api/get-championship-players id
                                                       #(reset! enrolled-players %)
                                                       #(reset! error (str "Erro ao carregar inscritos: " %))))
                       (fn [err]
                         (reset! error (str "Erro ao inscrever jogador: " err))))))
                  :variant :primary]]
                (if (seq @enrolled-players)
                  [:div {:class "space-y-2"}
                   (for [player @enrolled-players]
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
                      (for [player @enrolled-players]
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

