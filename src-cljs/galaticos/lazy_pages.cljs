(ns galaticos.lazy-pages
  "Code-split page modules (shadow.lazy). Heavy UI lives in the :pages chunk."
  (:require
   [reagent.core :as r]
   [shadow.lazy :as lazy]
   [galaticos.components.common :as common]
   [galaticos.state :as state]))

(def dashboard (lazy/loadable galaticos.components.dashboard/dashboard))
(def aggregations-page (lazy/loadable galaticos.components.aggregations/aggregations-page))
(def player-list (lazy/loadable galaticos.components.players/player-list))
(def player-form (lazy/loadable galaticos.components.players/player-form))
(def player-detail (lazy/loadable galaticos.components.players/player-detail))
(def match-list (lazy/loadable galaticos.components.matches/match-list))
(def match-form (lazy/loadable galaticos.components.matches/match-form))
(def match-detail (lazy/loadable galaticos.components.matches/match-detail))
(def championship-matches-page (lazy/loadable galaticos.components.matches/championship-matches-page))
(def championship-list (lazy/loadable galaticos.components.championships/championship-list))
(def championship-form (lazy/loadable galaticos.components.championships/championship-form))
(def championship-detail (lazy/loadable galaticos.components.championships/championship-detail))
(def championship-season-detail (lazy/loadable galaticos.components.championships/championship-season-detail))
(def team-list (lazy/loadable galaticos.components.teams/team-list))
(def team-form (lazy/loadable galaticos.components.teams/team-form))
(def team-detail (lazy/loadable galaticos.components.teams/team-detail))

(defn- safe-loadable-ready?
  "`lazy/ready?` uses shadow.loader before `getModuleInfo` exists (e.g. first paint, HMR) — avoid TypeError."
  [loadable]
  (try
    (boolean (lazy/ready? loadable))
    (catch :default _
      false)))

(defn loadable-route
  "Loads the shadow chunk once, then renders `(@loadable)` with optional `args`."
  [loadable & args]
  (r/with-let [ready (r/atom (safe-loadable-ready? loadable))
               err (r/atom nil)
               ;; Defer load to next macrotask so `shadow.loader.init` (end of app.js) has run.
               _ (when-not @ready
                   (js/setTimeout
                    (fn []
                      (try
                        (if (safe-loadable-ready? loadable)
                          (reset! ready true)
                          (-> (lazy/load loadable)
                              (.then (fn [] (reset! ready true)))
                              (.catch (fn [e]
                                        (let [m (or (.-message e) (str e))]
                                          (reset! err m)
                                          (state/toast-error! (str "Falha ao carregar módulo: " m)))))))
                        (catch :default e
                          (let [m (str e)]
                            (reset! err m)
                            (state/toast-error! (str "Falha ao carregar módulo: " m))))))
                    0))]
    (cond
      @err [common/button "Tentar novamente" #(reset! err nil) :variant :outline]
      @ready (into [@loadable] args)
      :else [common/loading-spinner])))
