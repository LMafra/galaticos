(ns galaticos.core
  "Main entry point for the ClojureScript frontend application"
  (:require [reagent.core :as r]
            ["react-dom/client" :refer [createRoot]]
            [reitit.frontend.easy :as rfe]
            [galaticos.routes :as routes]
            [galaticos.components.layout :as layout]
            [galaticos.components.login :as login]
            [galaticos.components.common :as common]
            [galaticos.components.toast :as toast]
            [galaticos.components.ui-lab :as ui-lab]
            [galaticos.lazy-pages :as lazy-p]
            [galaticos.effects :as effects]
            [galaticos.state :as state]))

(def current-match (r/atom nil))
(defonce app-started? (r/atom false))
(defonce react-root (atom nil))

(defn not-found-page []
  [:div {:class "mx-auto max-w-lg space-y-4 py-8"}
   [:h2 {:class "text-2xl font-semibold text-slate-900 dark:text-slate-100"} "Página não encontrada"]
   [:p {:class "text-slate-600 dark:text-slate-400"}
    "O endereço não existe ou foi alterado. Use os atalhos abaixo para voltar ao app."]
   [:div {:class "flex flex-wrap gap-2"}
    [common/button "Dashboard" #(rfe/push-state :dashboard) :variant :primary]
    [common/button "Jogadores" #(rfe/push-state :players) :variant :outline]
    [common/button "Partidas" #(rfe/push-state :matches) :variant :outline]]])

(defn unauthenticated-page []
  [:div
   [:h2 "Não autenticado"]
   [:p "Sua sessão não está ativa. Faça login para continuar."]])

(defn- auth-gate-panel [auth-checked?]
  [:div {:class "mx-auto max-w-md space-y-3 py-16 text-center"}
   [:div {:class "mx-auto h-0.5 w-48 overflow-hidden rounded-full bg-brand-maroon/20"}
    [:div {:class "h-full w-1/2 animate-pulse bg-brand-maroon"}]]
   [:p {:class "text-sm text-slate-500 dark:text-slate-400" :aria-live "polite"}
    (if auth-checked?
      "A redirecionar…"
      "A verificar sessão…")]])

(defn current-page
  "Determine which page to render based on current route"
  [match]
  (let [{:keys [authenticated auth-loading? auth-checked?]} @state/app-state
        route-name (when match (get-in match [:data :name]))
        path-params (when match (:path-params match))]
    (cond
      auth-loading? [auth-gate-panel false]

      (= route-name :login) [login/login-page]

      (= route-name :ui-lab) [ui-lab/ui-lab-page]

      (nil? match) [not-found-page]

      (and (routes/protected-route? route-name)
           (or (not auth-checked?) (not authenticated)))
      [auth-gate-panel auth-checked?]

      match (case route-name
              :home [lazy-p/loadable-route lazy-p/dashboard]
              :dashboard [lazy-p/loadable-route lazy-p/dashboard]
              :stats [lazy-p/loadable-route lazy-p/aggregations-page]
              :players [lazy-p/loadable-route lazy-p/player-list]
              :player-new [lazy-p/loadable-route lazy-p/player-form {}]
              :player-detail [lazy-p/loadable-route lazy-p/player-detail path-params]
              :player-edit [lazy-p/loadable-route lazy-p/player-form path-params]
              :matches [lazy-p/loadable-route lazy-p/match-list]
              :matches-by-championship ^{:key (str "matches-champ-" (:championship-id path-params))}
              [lazy-p/loadable-route lazy-p/championship-matches-page path-params]
              :match-new [lazy-p/loadable-route lazy-p/match-form]
              :match-new-in-championship ^{:key (str "match-new-" (:championship-id path-params))}
              [lazy-p/loadable-route lazy-p/match-form path-params]
              :match-edit [lazy-p/loadable-route lazy-p/match-form path-params]
              :match-detail [lazy-p/loadable-route lazy-p/match-detail path-params]
              :championships [lazy-p/loadable-route lazy-p/championship-list]
              :championship-new [lazy-p/loadable-route lazy-p/championship-form {}]
              :championship-edit [lazy-p/loadable-route lazy-p/championship-form path-params]
              :championship-detail ^{:key (:id path-params)}
              [lazy-p/loadable-route lazy-p/championship-detail path-params]
              :championship-season-detail
              ^{:key (str (:id path-params) "-" (:season-id path-params))}
              [lazy-p/loadable-route lazy-p/championship-season-detail path-params]
              :teams [lazy-p/loadable-route lazy-p/team-list]
              :team-new [lazy-p/loadable-route lazy-p/team-form {}]
              :team-edit [lazy-p/loadable-route lazy-p/team-form path-params]
              :team-detail [lazy-p/loadable-route lazy-p/team-detail path-params]
              [not-found-page])

      :else [:div {:style {:text-align "center" :padding "40px"}} "Loading..."])))

(defn app
  "Main application component"
  []
  (let [route-name (when @current-match (get-in @current-match [:data :name]))]
    (if (#{:login :ui-lab} route-name)
      (when @current-match
        ;; Login / UI lab render outside `layout`; still mount toasts for API errors.
        [:<> [current-page @current-match] [toast/toast-container]])
      [layout/layout route-name
       (when @current-match
         [current-page @current-match])])))

(defn mount-root []
  (when-let [root-element (.getElementById js/document "app")]
    (if-let [root @react-root]
      ;; React 18: use root.render for updates
      (.render root (r/as-element [app]))
      ;; React 18: create root on first mount
      (let [root-obj (createRoot root-element)]
        (reset! react-root root-obj)
        (.render root-obj (r/as-element [app]))))))

(defn- canonicalize-match [m]
  (some-> m (update-in [:data :name] routes/canonical-route-name)))

(defn- on-navigate! [m _]
  (let [m (canonicalize-match m)]
    (reset! current-match m)
    (swap! state/app-state assoc :route-match m)
    ;; Defer route effects so React finishes mounting before atom-driven re-renders.
    (js/setTimeout
     (fn []
       (effects/maybe-redirect-unauthenticated!)
       (effects/on-route! m))
     0)))

(defn init
  "Initialize the application (called from shadow-cljs init-fn)."
  []
  (when (compare-and-set! app-started? false true)
    (rfe/start! routes/router on-navigate! {:use-fragment true :default :dashboard})
    (mount-root)
    (js/setTimeout #(effects/ensure-auth!) 0)))

(defn ^:dev/after-load reload []
  (mount-root))
