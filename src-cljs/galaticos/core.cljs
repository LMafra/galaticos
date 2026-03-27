(ns galaticos.core
  "Main entry point for the ClojureScript frontend application"
  (:require [reagent.core :as r]
            ["react-dom/client" :refer [createRoot]]
            [reitit.frontend.easy :as rfe]
            [galaticos.routes :as routes]
            [galaticos.components.layout :as layout]
            [galaticos.components.login :as login]
            [galaticos.components.dashboard :as dashboard]
            [galaticos.components.aggregations :as aggregations]
            [galaticos.components.players :as players]
            [galaticos.components.matches :as matches]
            [galaticos.components.championships :as championships]
            [galaticos.components.teams :as teams]
            [galaticos.effects :as effects]
            [galaticos.state :as state]))

(def current-match (r/atom nil))
(defonce app-started? (r/atom false))
(defonce react-root (atom nil))

(defn not-found-page []
  [:div
   [:h2 "404"]
   [:p "Página não encontrada."]])

(defn unauthenticated-page []
  [:div
   [:h2 "Não autenticado"]
   [:p "Sua sessão não está ativa. Faça login para continuar."]])

(def ^:private protected-routes
  #{:player-new :player-edit
    :match-new :match-new-in-championship :match-edit
    :championship-new :championship-edit
    :teams :team-detail :team-new :team-edit})

(defn current-page
  "Determine which page to render based on current route"
  [match]
  (let [{:keys [authenticated auth-loading? auth-checked?]} @state/app-state
        route-name (when match (get-in match [:data :name]))
        path-params (when match (:path-params match))]
    (cond
      ;; Show loading state while checking auth
      auth-loading? [:div {:style {:text-align "center" :padding "40px"}} "Carregando autenticação..."]
      
      ;; Login page - show regardless of auth status (component handles redirect)
      (= route-name :login) [login/login-page]
      
      ;; No match found
      (nil? match) [not-found-page]
      
      ;; Protected routes - require authentication
      (and auth-checked? (not authenticated) (contains? protected-routes route-name))
      (do
        (rfe/push-state :dashboard)
        [:div {:style {:text-align "center" :padding "40px"}} "Redirecionando..."])
      
      ;; Authenticated routes
      match (case route-name
              :home [dashboard/dashboard]
              :dashboard [dashboard/dashboard]
              :stats [aggregations/aggregations-page]
              :players [players/player-list]
              :player-new [players/player-form {}]
              :player-detail [players/player-detail path-params]
              :player-edit [players/player-form path-params]
              :matches [matches/match-list]
              :match-new [matches/match-form]
              :match-new-in-championship ^{:key (str "match-new-" (:championship-id path-params))}
              [matches/match-form path-params]
              :match-edit [matches/match-form path-params]
              :match-detail [matches/match-detail path-params]
              :championships [championships/championship-list]
              :championship-new [championships/championship-form {}]
              :championship-edit [championships/championship-form path-params]
              :championship-detail ^{:key (:id path-params)} [championships/championship-detail path-params]
              :championship-season-detail
              ^{:key (str (:id path-params) "-" (:season-id path-params))}
              [championships/championship-season-detail path-params]
              :teams [teams/team-list]
              :team-new [teams/team-form {}]
              :team-edit [teams/team-form path-params]
              :team-detail [teams/team-detail path-params]
              [not-found-page])
      
      :else [:div {:style {:text-align "center" :padding "40px"}} "Loading..."])))

(defn app
  "Main application component"
  []
  (let [route-name (when @current-match (get-in @current-match [:data :name]))]
    (if (= route-name :login)
      (when @current-match
        [current-page @current-match])
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

(defn init
  "Initialize the application (called from shadow-cljs init-fn)."
  []
  (when (compare-and-set! app-started? false true)
    (effects/ensure-auth!)
    (rfe/start!
     routes/router
     (fn [m _]
       (reset! current-match m)
       (swap! state/app-state assoc :route-match m)
        (let [route-name (when m (get-in m [:data :name]))
             {:keys [authenticated auth-checked?]} @state/app-state]
         ;; Redirect to login only for write routes.
         (when (and auth-checked?
                    (not authenticated)
                    (contains? protected-routes route-name))
           (rfe/push-state :dashboard))
         (effects/on-route! m)))
     {:use-fragment true
      :default :dashboard})
    (mount-root)))

(defn ^:dev/after-load reload []
  (mount-root))

