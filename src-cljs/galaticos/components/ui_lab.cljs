(ns galaticos.components.ui-lab
  "Dev showcase for UI kit primitives (UX-PLAN-18)."
  (:require [reagent.core :as r]
            [galaticos.components.common :as common]))

(defn ui-lab-page []
  (let [input-val (r/atom "")
        loading? (r/atom false)]
    (fn []
      [:div {:class "mx-auto max-w-3xl space-y-8 pb-12"}
       [:div
        [:p {:class "text-sm text-slate-500"} "UX-PLAN-18"]
        [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} "UI Lab"]
        [:p {:class "mt-1 text-sm text-slate-600 dark:text-slate-400"}
         "Variantes light/dark do kit — use o alternador de tema no cabeçalho (rotas autenticadas) ou no login."]]

       [common/card
        [:h3 {:class "app-section-title"} "Botões"]
        [:div {:class "mt-4 flex flex-wrap gap-2"}
         [common/button "Primário" #() :variant :primary]
         [common/button "Secundário" #() :variant :secondary]
         [common/button "Outline" #() :variant :outline]
         [common/button "Ghost" #() :variant :ghost]
         [common/button "Perigo" #() :variant :danger]
         [common/button "Alternar loading" #(swap! loading? not) :variant :outline]
         [common/button "Primário loading" #() :variant :primary :loading? @loading? :loading-label "A guardar…"]]
        [:p {:class "mt-2 text-xs text-slate-500"}
         "Estados: hover, focus-visible (ring), disabled, loading (spinner + aria-busy)."]]

       [common/card
        [:h3 {:class "app-section-title"} "Badges"]
        [:div {:class "mt-4 flex flex-wrap gap-2"}
         [common/badge "Sucesso" :variant :success]
         [common/badge "Aviso" :variant :warning]
         [common/badge "Perigo" :variant :danger]
         [common/badge "Info" :variant :info]
         [common/badge "Maroon" :variant :maroon]]]

       [common/card
        [:h3 {:class "app-section-title"} "Campos"]
        [:div {:class "mt-4 max-w-md space-y-4"}
         [common/input-field "Com hint" @input-val #(reset! input-val %)
          :hint "Texto de ajuda abaixo do campo."
          :placeholder "Exemplo"]
         [common/input-field "Com erro" @input-val #(reset! input-val %)
          :error "Valor inválido."
          :on-blur #()]]]

       [common/card
        [:h3 {:class "app-section-title"} "Skeleton"]
        [:div {:class "mt-4 space-y-3"}
         [common/skeleton-line :class "w-48"]
         [common/skeleton-table ["Col A" "Col B"] :rows 3]]]

       [common/card
        [:h3 {:class "app-section-title"} "Alertas"]
        [:div {:class "mt-4 space-y-2"}
         [common/alert "Erro de exemplo" :variant :error]
         [common/alert "Aviso de exemplo" :variant :warning]]]])))
