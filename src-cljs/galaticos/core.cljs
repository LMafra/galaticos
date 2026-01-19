(ns galaticos.core
  "Main entry point for the ClojureScript frontend application"
  (:require [reagent.core :as r]
            ["react-dom/client" :refer [createRoot]]
            [reitit.frontend.easy :as rfe]
            [galaticos.routes :as routes]
            [galaticos.components.layout :as layout]
            [galaticos.components.login :as login]
            [galaticos.components.dashboard :as dashboard]
            [galaticos.components.players :as players]
            [galaticos.components.matches :as matches]
            [galaticos.components.championships :as championships]
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
      (and auth-checked? (not authenticated))
      (do
        ;; Redirect to login if trying to access protected route
        (when (not= route-name :login)
          (rfe/push-state :login))
        [login/login-page])
      
      ;; Authenticated routes
      match (case route-name
              :dashboard [dashboard/dashboard]
              :players [players/player-list]
              :player-new [players/player-form {}]
              :player-detail [players/player-detail path-params]
              :player-edit [players/player-form path-params]
              :matches [matches/match-list]
              :match-new [matches/match-form]
              :championships [championships/championship-list]
              :championship-detail [championships/championship-detail path-params]
              [not-found-page])
      
      :else [:div {:style {:text-align "center" :padding "40px"}} "Loading..."])))

(defn app
  "Main application component"
  []
  (let [route-name (when @current-match (get-in @current-match [:data :name]))]
    ;; Don't show layout for login page
    (if (= route-name :login)
      (when @current-match
        [current-page @current-match])
      [layout/layout
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
       (effects/on-route! m))
     {:use-fragment true})
    (mount-root)))

(defn ^:dev/after-load reload []
  (mount-root))

