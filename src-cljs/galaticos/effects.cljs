(ns galaticos.effects
  "Route-driven side effects and guarded data fetching"
  (:require [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [galaticos.api :as api]
            [galaticos.routes :as routes]
            [galaticos.state :as state]))

(defonce requests-in-flight (r/atom #{}))
(defonce list-scroll-listener (atom nil))
(defonce list-scroll-save-timer (atom nil))
(defonce list-scroll-restore-gen (atom 0))

(defn- scroll-storage-key [route-name]
  (str "galaticos.scroll." (name route-name)))

(defn- current-scroll-y []
  (max (or (.-scrollY js/window) 0)
       (or (.-scrollTop (.-documentElement js/document)) 0)))

(defn- document-max-scroll-y []
  (let [sh (max (.-scrollHeight js/document.body)
                (.-scrollHeight (.-documentElement js/document)))]
    (max 0 (- sh (.-innerHeight js/window)))))

(defn save-list-scroll!
  "Persist window scroll for list routes (sessionStorage). Keeps the deepest Y seen."
  [route-name]
  (try
    (let [key (scroll-storage-key route-name)
          y (current-scroll-y)
          prev (some-> (.getItem js/sessionStorage key) js/parseInt)
          prev-y (when (number? prev) (if (js/isNaN prev) nil prev))
          next-y (if (and (number? prev-y) (> prev-y y)) prev-y y)]
      (.setItem js/sessionStorage key (str next-y)))
    (catch :default _ nil)))

(defn- clear-list-scroll-listener! [route-name]
  (swap! list-scroll-restore-gen inc)
  (when-let [handler @list-scroll-listener]
    (.removeEventListener js/window "scroll" handler)
    (reset! list-scroll-listener nil))
  (when-let [tid @list-scroll-save-timer]
    (js/clearTimeout tid)
    (reset! list-scroll-save-timer nil))
  (when route-name
    (save-list-scroll! route-name)))

(defn- attach-list-scroll-listener! [route-name]
  (clear-list-scroll-listener! nil)
  (let [handler (fn [_]
                  (when-let [tid @list-scroll-save-timer]
                    (js/clearTimeout tid))
                  (reset! list-scroll-save-timer
                          (js/setTimeout #(save-list-scroll! route-name) 100)))]
    (.addEventListener js/window "scroll" handler #js {:passive true})
    (reset! list-scroll-listener handler)))

(defn- try-restore-scroll! [y attempt gen]
  (when (= gen @list-scroll-restore-gen)
    (let [max-y (document-max-scroll-y)]
      (cond
        (zero? y) nil
        (>= max-y y)
        (js/window.scrollTo #js {:top y :behavior "auto"})

        (>= attempt 80)
        nil

        :else
        (js/setTimeout #(try-restore-scroll! y (inc attempt) gen) 100)))))

(defn restore-list-scroll!
  "Restore scroll after returning to a list route."
  [route-name]
  (try
    (when-let [stored (.getItem js/sessionStorage (scroll-storage-key route-name))]
      (let [y (js/parseInt stored 10)
            gen (swap! list-scroll-restore-gen inc)]
        (when (and (not (js/isNaN y)) (pos? y))
          (js/requestAnimationFrame
           (fn [_]
             (js/requestAnimationFrame
              (fn [_]
                (js/setTimeout (fn [] (try-restore-scroll! y 0 gen)) 50))))))))
    (catch :default _ nil)))

(def ^:private match-return-key "galaticos.match-return")

(defn set-match-return!
  "After match form, navigate to this route instead of the matches hub (UX-PLAN-14)."
  [route-name params]
  (try
    (.setItem js/sessionStorage match-return-key
              (js/JSON.stringify #js {:route (name route-name)
                                      :params (clj->js params)}))
    (catch :default _ nil)))

(defn consume-match-return!
  "Read and clear stored match return route, if any."
  []
  (try
    (when-let [raw (.getItem js/sessionStorage match-return-key)]
      (.removeItem js/sessionStorage match-return-key)
      (let [parsed (.parse js/JSON raw)
            route (keyword (aget parsed "route"))
            params (js->clj (aget parsed "params") :keywordize-keys true)]
        (when route {:route route :params params})))
    (catch :default _ nil)))

(defn- mark-in-flight! [k]
  (swap! requests-in-flight conj k))

(defn- clear-in-flight! [k]
  (swap! requests-in-flight disj k))

(defn- in-flight? [k]
  (contains? @requests-in-flight k))

(defn syncing?
  "True when any guarded API fetch is in flight (UX-PLAN-20 header indicator)."
  []
  (boolean (seq @requests-in-flight)))

(defn- guarded-fetch!
  "Prevent duplicate fetches for a given key and track loading/error state."
  [k loaded? fetch-fn on-success]
  (when (and (not loaded?) (not (in-flight? k)))
    (mark-in-flight! k)
    (state/set-resource-error! k nil)
    (state/set-resource-loading! k true)
    (let [on-success* (fn [result]
                        (on-success result)
                        (state/set-resource-loading! k false)
                        (clear-in-flight! k))
          on-error* (fn [error]
                      (state/set-resource-error! k (str "Erro ao carregar " (name k) ": " error))
                      (state/set-resource-loading! k false)
                      (clear-in-flight! k))]
      (fetch-fn on-success* on-error*))
    nil))

(defn ensure-player-insights! [player-id & [{:keys [force?]}]]
  (when-let [pid (some-> player-id str not-empty)]
    (let [{:keys [player-insights]} @state/app-state
          same? (= pid (some-> (:player-id player-insights) str))
          loaded? (and same? (some? (:data player-insights)) (not force?))]
      (when (and (not loaded?) (not (in-flight? :player-insights)))
        (mark-in-flight! :player-insights)
        (state/dispatch! [:analytics/insights-request {:player-id pid}])
        (api/get-player-insights
         pid
         (fn [data]
           (state/dispatch! [:analytics/insights-success {:data data}])
           (clear-in-flight! :player-insights))
         (fn [err _resp]
           (state/dispatch! [:analytics/insights-error {:error (str err)}])
           (clear-in-flight! :player-insights)))))))

(defn ensure-dashboard! [& [{:keys [force?]}]]
  (let [{:keys [dashboard-loaded?]} @state/app-state
        loaded? (and dashboard-loaded? (not force?))]
    (guarded-fetch! :dashboard loaded?
                    api/get-dashboard-stats
                    state/set-dashboard-stats!)))

(defn ensure-players! [& [{:keys [force?]}]]
  (let [{:keys [players-loaded?]} @state/app-state
        loaded? (and players-loaded? (not force?))]
    (guarded-fetch! :players loaded?
                    #(api/get-players {} %1 %2)
                    state/set-players!)))

(defn ensure-championships! [& [{:keys [force?]}]]
  (let [{:keys [championships-loaded?]} @state/app-state
        loaded? (and championships-loaded? (not force?))]
    (guarded-fetch! :championships loaded?
                    #(api/get-championships {} %1 %2)
                    state/set-championships!)))

(defn ensure-matches! [& [{:keys [force?]}]]
  (let [{:keys [matches-loaded?]} @state/app-state
        loaded? (and matches-loaded? (not force?))]
    (guarded-fetch! :matches loaded?
                    #(api/get-matches {} %1 %2)
                    state/set-matches!)))

(defn ensure-teams! [& [{:keys [force?]}]]
  ;; GET /api/teams requires Bearer auth; skip when logged out to avoid 401 noise in the console.
  (when (some? (api/current-token))
    (let [{:keys [teams-loaded?]} @state/app-state
          loaded? (and teams-loaded? (not force?))]
      (guarded-fetch! :teams loaded?
                      #(api/get-teams %1 %2)
                      state/set-teams!))))

(defn maybe-redirect-unauthenticated!
  "Defer navigation out of protected routes once auth is resolved."
  []
  (let [route-name (some-> @state/app-state :route-match (get-in [:data :name]))
        {:keys [authenticated auth-checked?]} @state/app-state]
    (when (and auth-checked?
               (not authenticated)
               (routes/protected-route? route-name))
      (js/setTimeout #(rfe/push-state :dashboard) 0))))

(defn- finish-auth-check! []
  (clear-in-flight! :auth)
  (maybe-redirect-unauthenticated!))

(defn ensure-auth! []
  (let [{:keys [auth-checked? auth-loading? authenticated]} @state/app-state
        token (api/current-token)]
    (when (and (not (in-flight? :auth))
               (not auth-loading?)
               (or (not auth-checked?) (not authenticated)))
      (if (and (not auth-checked?) (nil? token))
        ;; No token and not checked yet - mark as not authenticated without making HTTP call
        (do
          (state/clear-auth!)
          (finish-auth-check!))
        ;; Has token or already checked - verify with server
        (do
          (state/set-auth-loading! true)
          (mark-in-flight! :auth)
          (api/check-auth
           (fn [user]
             (if (some? user)
               (state/set-user! user (api/current-token))
               (do (state/clear-auth!)
                   (api/clear-token!)))
             (finish-auth-check!))
           (fn [_error]
             (state/clear-auth!)
             (api/clear-token!)
             (finish-auth-check!))))))))

(defn on-route!
  "Trigger route-specific effects such as fetching data."
  [match]
  (when match
    (let [route-name (get-in match [:data :name])
          prev-route (get-in @state/app-state [:ui :last-route])
          scroll-routes #{:players :matches :championships :teams}]
      ;; UX-PLAN-02: hash routes don't restore scroll on browser back; stash Y per list route.
      (when (contains? scroll-routes prev-route)
        (clear-list-scroll-listener! prev-route))
      (swap! state/app-state assoc-in [:ui :last-route] route-name)
      (state/clear-page-context!)
      (when (contains? scroll-routes route-name)
        (attach-list-scroll-listener! route-name)
        (restore-list-scroll! route-name))
      ;; Don't check auth or fetch data for login page
      (when (not= route-name :login)
        (ensure-auth!)
        (case route-name
          :home (do (ensure-dashboard!) (ensure-matches!))
          :dashboard (do (ensure-dashboard!) (ensure-matches!))
          :stats (ensure-championships!)
          :players nil
          :championships (ensure-championships!)
          :matches (do
                     (ensure-championships!)
                     (ensure-matches!))
          :matches-by-championship (do
                                     (ensure-championships!)
                                     (ensure-teams!))
          :match-new (do
                       (ensure-championships!)
                       (ensure-teams!))
          :match-new-in-championship (do
                                        (ensure-championships!)
                                        (ensure-teams!))
          :match-edit (do
                        (ensure-championships!)
                        (ensure-teams!))
          :teams (do (ensure-teams!) (ensure-matches!))
          :team-new (ensure-teams!)
          :team-detail (ensure-teams!)
          :team-edit (ensure-teams!)
          :player-detail (when-let [id (get-in match [:path-params :id])]
                           (ensure-player-insights! id))
          nil)))))

