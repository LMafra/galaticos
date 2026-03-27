(ns galaticos.state
  "Application state management"
  (:require [reagent.core :as r]))

(defn- system-prefers-dark? []
  (try
    (boolean (.. js/window
                 (matchMedia "(prefers-color-scheme: dark)")
                 -matches))
    (catch :default _ false)))

(defn- initial-theme []
  (let [stored-theme (some-> js/window .-localStorage (.getItem "galaticos.theme"))]
    (cond
      (#{"light" "dark"} stored-theme) stored-theme
      (system-prefers-dark?) "dark"
      :else "light")))

(defn- apply-theme! [theme]
  (when-let [root (.-documentElement js/document)]
    (.toggle (.-classList root) "dark" (= theme "dark"))))

(defn- persist-theme! [theme]
  (try
    (some-> js/window .-localStorage (.setItem "galaticos.theme" theme))
    (catch :default _ nil)))

(defonce app-state
  (r/atom {:user nil
           :token nil
           :authenticated false
           :auth-loading? false
           :auth-checked? false
           :route-match nil
           :ui {:sidebar-open? false
                :theme (initial-theme)}
           :players []
           :players-loaded? false
           :players-loading? false
           :players-error nil
           :championships []
           :championships-loaded? false
           :championships-loading? false
           :championships-error nil
           :matches []
           :matches-loaded? false
           :matches-loading? false
           :matches-error nil
           :teams []
           :teams-loaded? false
           :teams-loading? false
           :teams-error nil
           :dashboard-stats nil
           :dashboard-loaded? false
           :dashboard-loading? false
           :dashboard-error nil
           :loading false
           :error nil}))

(defn toggle-sidebar! []
  (swap! app-state update-in [:ui :sidebar-open?] not))

(defn close-sidebar! []
  (swap! app-state assoc-in [:ui :sidebar-open?] false))

(defn set-theme! [theme]
  (when (#{"light" "dark"} theme)
    (apply-theme! theme)
    (persist-theme! theme)
    (swap! app-state assoc-in [:ui :theme] theme)))

(apply-theme! (get-in @app-state [:ui :theme]))

(defn set-user! [user token]
  (swap! app-state assoc :user user :token token :authenticated (some? user) :auth-loading? false :auth-checked? true))

(defn clear-auth! []
  (swap! app-state assoc :user nil :token nil :authenticated false :auth-loading? false :auth-checked? true))

(defn set-auth-loading! [loading]
  (swap! app-state assoc :auth-loading? loading))

(defn set-loading! [loading]
  (swap! app-state assoc :loading loading))

(defn set-error! [error]
  (swap! app-state assoc :error error))

(defn clear-error! []
  (swap! app-state assoc :error nil))

(defn- resource-keys [k]
  {:loading? (keyword (str (name k) "-loading?"))
   :loaded? (keyword (str (name k) "-loaded?"))
   :error (keyword (str (name k) "-error"))})

(defn set-resource-loading! [k loading]
  (let [{:keys [loading?]} (resource-keys k)]
    (swap! app-state assoc loading? loading)))

(defn set-resource-error! [k err]
  (let [{:keys [error]} (resource-keys k)]
    (swap! app-state assoc error err)))

(defn set-players! [players]
  (swap! app-state assoc :players players :players-loaded? true :players-loading? false :players-error nil))

(defn set-championships! [championships]
  (swap! app-state assoc :championships championships :championships-loaded? true :championships-loading? false :championships-error nil))

(defn set-matches! [matches]
  (swap! app-state assoc :matches matches :matches-loaded? true :matches-loading? false :matches-error nil))

(defn set-teams! [teams]
  (swap! app-state assoc :teams teams :teams-loaded? true :teams-loading? false :teams-error nil))

(defn set-dashboard-stats! [stats]
  (swap! app-state assoc :dashboard-stats stats :dashboard-loaded? true :dashboard-loading? false :dashboard-error nil))

