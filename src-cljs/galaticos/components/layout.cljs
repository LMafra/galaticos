(ns galaticos.components.layout
  "Main layout component with navigation"
  (:require [galaticos.state :as state]
            [galaticos.routes :as routes]
            [galaticos.api :as api]
            [galaticos.components.common :as common]
            [galaticos.components.toast :as toast]
            [reitit.frontend.easy :as rfe]
            ["lucide-react" :refer [LayoutDashboard Users CalendarDays Trophy Shield Menu Sun Moon BarChart2]]))

(defn- user-label [user]
  (cond
    (map? user) (or (:username user) (:name user) (:email user))
    (string? user) user
    :else "Usuário"))

(defn- nav-route
  "Collapse match create/edit routes so Partidas stays active in the sidebar."
  [r]
  (case r
    :home :dashboard
    (:match-new :match-new-in-championship :match-edit :match-detail) :matches
    r))

(defn- nav-items [authenticated?]
  (cond-> [{:route :dashboard :label "Dashboard" :icon LayoutDashboard}
           {:route :stats :label "Estatísticas" :icon BarChart2}
           {:route :players :label "Jogadores" :icon Users}
           {:route :matches :label "Partidas" :icon CalendarDays}
           {:route :championships :label "Campeonatos" :icon Trophy}]
    authenticated? (conj {:route :teams :label "Times" :icon Shield})))

(defn- nav-link [current-route {:keys [route label icon]}]
  (let [active? (= (nav-route current-route) route)]
    [:a {:href (routes/href route)
         :class (common/merge-classes
                 "group flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition"
                 (if active?
                   "bg-brand-maroon/10 text-brand-maroon"
                   "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"))}
     [:> icon {:size 18
               :class (common/merge-classes
                       "text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-300"
                       (when active? "text-brand-maroon"))}]
     label]))

(defn- sidebar [current-route authenticated?]
  [:aside {:class "hidden w-64 shrink-0 border-r border-slate-200 bg-white px-4 py-6 dark:border-slate-700 dark:bg-slate-900 lg:block"}
   [:div {:class "flex items-center gap-2 px-2"}
    [:div {:class "flex h-10 w-10 items-center justify-center rounded-xl bg-brand-maroon text-white font-bold"} "G"]
    [:div
     [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} "Galáticos"]
     [:p {:class "text-xs text-slate-500 dark:text-slate-400"} "Gestão de elenco"]]]
   [:nav {:class "mt-8 space-y-1"}
    (for [item (nav-items authenticated?)]
      ^{:key (:route item)} [nav-link current-route item])]])

(defn- mobile-sidebar [current-route authenticated?]
  (let [open? (get-in @state/app-state [:ui :sidebar-open?])]
    (when open?
      [:div {:class "fixed inset-0 z-40 lg:hidden"}
       [:div {:class "absolute inset-0 bg-slate-900/40" :on-click state/close-sidebar!}]
       [:div {:class "absolute left-0 top-0 h-full w-72 bg-white px-4 py-6 shadow-xl dark:bg-slate-900 dark:shadow-slate-950/50"}
        [:div {:class "flex items-center justify-between px-2"}
         [:div {:class "flex items-center gap-2"}
          [:div {:class "flex h-10 w-10 items-center justify-center rounded-xl bg-brand-maroon text-white font-bold"} "G"]
          [:div
           [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} "Galáticos"]
           [:p {:class "text-xs text-slate-500 dark:text-slate-400"} "Gestão de elenco"]]]
         [common/button "×" state/close-sidebar! :variant :ghost :class "text-lg" :aria-label "Fechar menu"]]
        [:nav {:class "mt-8 space-y-1"}
         (for [item (nav-items authenticated?)]
           ^{:key (:route item)}
           [:div {:on-click state/close-sidebar!}
            [nav-link current-route item]])]]])))

(defn- header [current-route]
  (let [{:keys [authenticated auth-loading? user]} @state/app-state
        theme (get-in @state/app-state [:ui :theme])]
    [:header {:class "sticky top-0 z-20 flex items-center justify-between border-b border-slate-200 bg-white/90 px-4 py-4 backdrop-blur dark:border-slate-700 dark:bg-slate-900/90 lg:px-8"}
     [:div {:class "flex items-center gap-3"}
      [:button {:class "rounded-lg p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800 lg:hidden"
                :on-click state/toggle-sidebar!
                :aria-label "Abrir menu"}
       [:> Menu {:size 20}]]
      [:h1 {:class "text-base font-semibold text-slate-900 dark:text-slate-100"}
       (case (nav-route current-route)
         :dashboard "Dashboard"
         :stats "Estatísticas"
         :players "Jogadores"
         :matches "Partidas"
         :championships "Campeonatos"
         :teams "Times"
         "Galáticos")]]
     [:div {:class "flex items-center gap-3"}
      [:button {:type "button"
                :class "inline-flex rounded-lg p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                :on-click #(state/set-theme! (if (= theme "dark") "light" "dark"))
                :aria-label "Alternar tema claro/escuro"}
       (if (= theme "dark")
         [:> Sun {:size 18}]
         [:> Moon {:size 18}])]
      (cond
        auth-loading? [:span {:class "text-xs text-slate-400 dark:text-slate-500"} "Verificando sessão..."]
        authenticated [:div {:class "flex items-center gap-3"}
                      [:span {:class "text-sm text-slate-600 dark:text-slate-300"} (user-label user)]
                      [common/button "Sair"
                       (fn [_e]
                         (api/logout
                          (fn [_]
                            (state/clear-auth!)
                            (api/clear-token!)
                            (rfe/push-state :login))
                          (fn [err]
                            (js/console.error "Logout error:" err)
                            (state/clear-auth!)
                            (api/clear-token!)
                            (rfe/push-state :login))))
                       :variant :outline]]
        :else [common/button "Entrar" #(rfe/push-state :login) :variant :outline])]]))

(defn footer []
  [:footer {:class "mt-10 border-t border-slate-200 py-6 text-center text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400"}
   "© 2025 Galáticos - Sistema de Gestão de Elenco Esportivo"])

(defn layout [current-route content]
  (let [authenticated? (:authenticated @state/app-state)]
    [:div {:class "min-h-screen bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100"}
     [mobile-sidebar current-route authenticated?]
     [:div {:class "flex min-h-screen"}
      [sidebar current-route authenticated?]
      [:div {:class "flex min-h-screen w-full flex-col"}
       [header current-route]
       [:main#main-content {:class "flex-1 px-4 pb-10 pt-6 lg:px-8"}
        (if (:loading @state/app-state)
          [common/loading-spinner]
          content)]
       [footer]]]
     [toast/toast-container]]))

