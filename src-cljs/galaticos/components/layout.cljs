(ns galaticos.components.layout
  "Main layout component with navigation (UX-PLAN-23)."
  (:require [clojure.string :as str]
            [galaticos.state :as state]
            [galaticos.routes :as routes]
            [galaticos.api :as api]
            [galaticos.components.common :as common]
            [galaticos.components.toast :as toast]
            [reitit.frontend.easy :as rfe]
            [galaticos.effects :as effects]
            ["lucide-react" :refer [LayoutDashboard Users CalendarDays Trophy Shield Menu Sun Moon BarChart2 Loader2]]))

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
    (:match-new :match-new-in-championship :match-edit :match-detail :matches-by-championship) :matches
    r))

(defn- route-title [route]
  (case route
    :dashboard "Dashboard"
    :stats "Estatísticas"
    :players "Jogadores"
    :matches "Partidas"
    :championships "Campeonatos"
    :teams "Times"
    "Galáticos"))

(defn- championship-name-by-id [championships id]
  (when-not (str/blank? (str id))
    (some->> championships
             (filter #(= (str (:_id %)) (str id)))
             first
             :name)))

(defn- derive-page-context [current-route route-match championships page-override]
  (or page-override
      (let [ch-name (when (#{:matches-by-championship :match-new-in-championship} current-route)
                      (championship-name-by-id championships
                                               (get-in route-match [:path-params :championship-id])))
            title (case current-route
                    :match-new "Nova Partida"
                    :match-new-in-championship "Nova Partida"
                    :match-edit "Editar Partida"
                    :match-detail "Partida"
                    nil)]
        (when (or ch-name title)
          {:badge ch-name :title title}))))

(defn- primary-nav-items []
  [{:route :dashboard :label "Dashboard" :icon LayoutDashboard}
   {:route :stats :label "Estatísticas" :icon BarChart2}
   {:route :players :label "Jogadores" :icon Users}
   {:route :matches :label "Partidas" :icon CalendarDays}
   {:route :championships :label "Campeonatos" :icon Trophy}])

(defn- nav-items [authenticated?]
  (cond-> (primary-nav-items)
    authenticated? (conj {:route :teams :label "Times" :icon Shield})))

(defn- mobile-tab-items []
  [{:route :dashboard :label "Início" :icon LayoutDashboard}
   {:route :matches :label "Partidas" :icon CalendarDays}
   {:route :players :label "Jogadores" :icon Users}
   {:route :championships :label "Campeonatos" :icon Trophy}])

(defn- drawer-nav-items [authenticated?]
  (cond-> [{:route :stats :label "Estatísticas" :icon BarChart2}]
    authenticated? (conj {:route :teams :label "Times" :icon Shield})))

(defn- nav-link
  [current-route {:keys [route label icon]} & {:keys [show-label?]}]
  (let [active? (= (nav-route current-route) route)
        label-class (if show-label?
                      "inline"
                      "hidden lg:inline")]
    [:a {:href (routes/href route)
         :title label
         :aria-label label
         :aria-current (when active? "page")
         :class (common/merge-classes
                 "group flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition"
                 (when-not show-label? "md:justify-center md:px-2 lg:justify-start lg:px-3")
                 (if active?
                   "bg-brand-maroon/10 text-brand-maroon"
                   "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"))}
     [:> icon {:size 18
               :aria-hidden true
               :class (common/merge-classes
                       "shrink-0 text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-300"
                       (when active? "text-brand-maroon"))}]
     [:span {:class label-class} label]]))

(defn- top-progress-bar []
  (let [{:keys [auth-loading?]} @state/app-state]
    (when (or auth-loading? (effects/syncing?))
      [:div {:class "pointer-events-none fixed inset-x-0 top-0 z-[100] h-0.5 overflow-hidden bg-brand-maroon/15"
             :role "progressbar"
             :aria-label "A carregar"}
       [:div {:class "h-full w-1/3 animate-pulse bg-brand-maroon"}]])))

(defn- sidebar [current-route authenticated?]
  [:aside {:class "hidden md:flex md:w-16 lg:w-64 shrink-0 flex-col border-r border-slate-200 bg-white px-2 py-6 dark:border-slate-700 dark:bg-slate-900 lg:px-4"}
   [:div {:class "flex items-center justify-center gap-2 px-1 lg:justify-start lg:px-2"}
    [:div {:class "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-maroon text-white font-bold"
           :title "Galáticos"}
     "G"]
    [:div {:class "hidden min-w-0 lg:block"}
     [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} "Galáticos"]
     [:p {:class "text-xs text-slate-500 dark:text-slate-400"} "Gestão de elenco"]]]
   [:nav {:class "mt-8 space-y-1"}
    (for [item (nav-items authenticated?)]
      ^{:key (:route item)} [nav-link current-route item])]])

(defn- mobile-bottom-tab-bar [current-route]
  (let [sidebar-open? (get-in @state/app-state [:ui :sidebar-open?])]
    [:nav {:class "fixed inset-x-0 bottom-0 isolate z-[200] border-t border-slate-200 bg-white/95 shadow-[0_-4px_12px_rgba(15,23,42,0.08)] backdrop-blur dark:border-slate-700 dark:bg-slate-900/95 md:hidden pointer-events-auto"
           :aria-label "Navegação principal"}
     [:ul {:class "mx-auto flex max-w-lg items-stretch justify-around px-1 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-2"}
      (for [{:keys [route label icon]} (mobile-tab-items)]
        (let [active? (= (nav-route current-route) route)]
          ^{:key route}
          [:li {:class "flex-1 min-w-0"}
           [:a {:href (routes/href route)
                :aria-label label
                :aria-current (when active? "page")
                :class (common/merge-classes
                         "flex min-h-[48px] flex-col items-center justify-center gap-0.5 rounded-lg px-1 py-1 text-[11px] font-medium leading-tight transition"
                         (if active?
                           "text-brand-maroon"
                           "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"))}
            [:> icon {:size 20 :aria-hidden true}]
            [:span {:class "max-w-full truncate"} label]]]))
      [:li {:class "flex-1 min-w-0"}
       [:button {:type "button"
                 :aria-label "Mais opções"
                 :aria-expanded (when sidebar-open? "true")
                 :aria-controls "mobile-drawer-nav"
                 :class (common/merge-classes
                          "flex min-h-[48px] w-full flex-col items-center justify-center gap-0.5 rounded-lg px-1 py-1 text-[11px] font-medium leading-tight transition"
                          (if sidebar-open?
                            "text-brand-maroon bg-brand-maroon/5"
                            "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"))
                 :on-click state/toggle-sidebar!}
        [:> Menu {:size 20 :aria-hidden true}]
        [:span "Mais"]]]]]))

(defn- mobile-drawer-logout! []
  (api/logout
   (fn [_]
     (state/clear-auth!)
     (api/clear-token!)
     (state/close-sidebar!)
     (rfe/push-state :login))
   (fn [err]
     (js/console.error "Logout error:" err)
     (state/clear-auth!)
     (api/clear-token!)
     (state/close-sidebar!)
     (rfe/push-state :login))))

(defn- mobile-sidebar [current-route authenticated?]
  (let [open? (get-in @state/app-state [:ui :sidebar-open?])
        {:keys [authenticated user]} @state/app-state]
    (when open?
      [:div {:class "fixed inset-0 z-40 md:hidden"}
       [:div {:class "absolute inset-0 bg-slate-900/40" :on-click state/close-sidebar!}]
       [:div {:id "mobile-drawer-nav"
              :class "absolute left-0 top-0 flex h-full w-72 flex-col bg-white px-4 py-6 shadow-xl dark:bg-slate-900 dark:shadow-slate-950/50"}
        [:div {:class "flex items-center justify-between px-2"}
         [:div {:class "flex items-center gap-2"}
          [:div {:class "flex h-10 w-10 items-center justify-center rounded-xl bg-brand-maroon text-white font-bold"} "G"]
          [:div
           [:p {:class "text-sm font-semibold text-slate-900 dark:text-slate-100"} "Galáticos"]
           [:p {:class "text-xs text-slate-500 dark:text-slate-400"} "Mais opções"]]]
         [common/button "×" state/close-sidebar! :variant :ghost :class "text-lg" :aria-label "Fechar menu"]]
        [:nav {:class "mt-8 flex-1 space-y-1"}
         (for [item (drawer-nav-items authenticated?)]
           ^{:key (:route item)}
           [:div {:on-click state/close-sidebar!}
            [nav-link current-route item :show-label? true]])]
        [:div {:class "mt-4 space-y-2 border-t border-slate-200 pt-4 dark:border-slate-700"}
         (if authenticated
           [common/button "Sair" mobile-drawer-logout! :variant :outline :class "w-full"]
           [common/button "Entrar"
            #(do (state/close-sidebar!) (rfe/push-state :login))
            :variant :primary
            :class "w-full"])
         (when authenticated
           [:p {:class "text-center text-xs text-slate-500 dark:text-slate-400"}
            (user-label user)])]]])))

(defn- header [current-route]
  (let [{:keys [authenticated auth-loading? user championships]} @state/app-state
        route-match (:route-match @state/app-state)
        page-override (get-in @state/app-state [:ui :page-context])
        page-ctx (derive-page-context current-route route-match championships page-override)
        header-title (or (:title page-ctx) (route-title (nav-route current-route)))
        theme (get-in @state/app-state [:ui :theme])]
    [:header {:class "sticky top-0 z-20 flex items-center justify-between border-b border-slate-200 bg-white/90 px-4 py-4 backdrop-blur dark:border-slate-700 dark:bg-slate-900/90 md:pl-6 lg:px-8"}
     [:div {:class "flex min-w-0 items-center gap-3"}
      [:h1 {:class "truncate text-base font-semibold text-slate-900 dark:text-slate-100"}
       header-title]
      (when-let [badge (:badge page-ctx)]
        [common/badge badge :variant :info :class "hidden shrink-0 sm:inline-flex"])]
     [:div {:class "flex shrink-0 items-center gap-3"}
      (when (effects/syncing?)
        [:span {:class "inline-flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400"
                :title "Sincronizando"}
         [:> Loader2 {:size 14 :class "animate-spin" :aria-hidden true}]
         [:span {:class "hidden sm:inline"} "Sincronizando"]])
      [:button {:type "button"
                :class "inline-flex rounded-lg p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                :on-click #(state/set-theme! (if (= theme "dark") "light" "dark"))
                :aria-label "Alternar tema claro/escuro"}
       (if (= theme "dark")
         [:> Sun {:size 18 :aria-hidden true}]
         [:> Moon {:size 18 :aria-hidden true}])]
      (cond
        auth-loading? [:span {:class "text-xs text-slate-400 dark:text-slate-500" :aria-live "polite"} "Verificando sessão…"]
        authenticated [:div {:class "hidden items-center gap-3 md:flex"}
                      [:span {:class "inline-flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300"}
                       [:span {:class "h-2 w-2 rounded-full bg-emerald-500"
                               :title "Sessão ativa"
                               :aria-hidden "true"}]
                       (user-label user)]
                      [common/button "Sair" mobile-drawer-logout! :variant :outline]]
        :else [:div {:class "hidden md:block"}
               [common/button "Entrar" #(rfe/push-state :login) :variant :outline]])]]))

(defn footer []
  [:footer {:class "mt-10 border-t border-slate-200 py-6 text-center text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400"}
   "© 2025 Galáticos - Sistema de Gestão de Elenco Esportivo"])

(defn layout [current-route content]
  (let [authenticated? (:authenticated @state/app-state)]
    [:div {:class "min-h-screen bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100"}
     [top-progress-bar]
     [mobile-sidebar current-route authenticated?]
     [:div {:class "flex min-h-screen"}
      [sidebar current-route authenticated?]
      [:div {:class "relative z-0 flex min-h-screen w-full flex-col"}
       [header current-route]
       [:main#main-content {:class "relative z-0 flex-1 px-4 pb-32 pt-6 md:pb-10 lg:px-8"}
        (if (:loading @state/app-state)
          [common/loading-spinner]
          content)]
       [footer]]]
     [mobile-bottom-tab-bar current-route]
     [toast/toast-container]]))
