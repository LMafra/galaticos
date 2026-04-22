(ns galaticos.state
  "Application state management"
  (:require [reagent.core :as r]
            [galaticos.i18n :as i18n]))

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

;; add/remove, not toggle("dark", force): some envs ignore 2nd arg and flip wrong.
(defn- apply-theme! [theme]
  (when-let [cl (.. js/document -documentElement -classList)]
    (if (= theme "dark")
      (.add cl "dark")
      (.remove cl "dark"))))

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
           :error nil
           :toasts []}))

(def ^:private default-ttl
  {:error   7000
   :success 4000
   :warning 5500
   :info    5000})

(defonce ^:private toast-counter (atom 0))

(defn- next-toast-id []
  (swap! toast-counter inc))

(defn dismiss-toast! [id]
  (swap! app-state update :toasts
         (fn [ts] (vec (remove #(= id (:id %)) ts)))))

(defn push-toast!
  "Adiciona um toast. Opts aceita :variant (:error|:success|:warning|:info),
   :ttl (ms; 0 ou nil = não auto-dismiss), :id (opcional)."
  [message & [opts]]
  (when-let [msg (some-> message str not-empty)]
    (let [{:keys [variant ttl id]} opts
          variant (or variant :error)
          id (or id (next-toast-id))
          ttl (if (contains? opts :ttl) ttl (get default-ttl variant 5000))
          toast {:id id :variant variant :message msg :ttl ttl}]
      (swap! app-state update :toasts (fnil conj []) toast)
      (when (and ttl (pos? ttl))
        (js/setTimeout #(dismiss-toast! id) ttl))
      id)))

(defn toast-error!   [msg & [opts]] (push-toast! (i18n/t msg) (assoc opts :variant :error)))
(defn toast-success! [msg & [opts]] (push-toast! (i18n/t msg) (assoc opts :variant :success)))
(defn toast-warning! [msg & [opts]] (push-toast! (i18n/t msg) (assoc opts :variant :warning)))
(defn toast-info!    [msg & [opts]] (push-toast! (i18n/t msg) (assoc opts :variant :info)))

(defn clear-toasts! []
  (swap! app-state assoc :toasts []))

(defn toast-field-errors!
  "Dispara um toast por mensagem de erro de campo (valores do map `errs`).
   Mantém cada string como está — já vêm em PT-BR."
  [errs]
  (doseq [msg (->> (vals errs) (remove nil?) distinct)]
    (toast-error! msg)))

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

(defn set-error!
  "Exibe um erro global como toast. `:error` no app-state permanece apenas
   para compatibilidade; a UI não renderiza mais inline."
  [error]
  (when error (toast-error! error))
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
    (swap! app-state assoc error err)
    (when err (toast-error! err))))

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

