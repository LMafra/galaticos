(ns galaticos.effects
  "Route-driven side effects and guarded data fetching"
  (:require [reagent.core :as r]
            [galaticos.api :as api]
            [galaticos.state :as state]))

(defonce requests-in-flight (r/atom #{}))

(defn- mark-in-flight! [k]
  (swap! requests-in-flight conj k))

(defn- clear-in-flight! [k]
  (swap! requests-in-flight disj k))

(defn- in-flight? [k]
  (contains? @requests-in-flight k))

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
          (clear-in-flight! :auth))
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
             (clear-in-flight! :auth))
           (fn [_error]
             (state/clear-auth!)
             (api/clear-token!)
             (clear-in-flight! :auth))))))))

(defn on-route!
  "Trigger route-specific effects such as fetching data."
  [match]
  (when match
    (let [route-name (get-in match [:data :name])]
      ;; Don't check auth or fetch data for login page
      (when (not= route-name :login)
        (ensure-auth!)
        (case route-name
          :home (ensure-dashboard!)
          :dashboard (ensure-dashboard!)
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
          :teams (ensure-teams!)
          :team-new (ensure-teams!)
          :team-detail (ensure-teams!)
          :team-edit (ensure-teams!)
          nil)))))

