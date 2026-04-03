(ns galaticos.lazy-pages
  "Code-split page modules (shadow.lazy). Heavy UI lives in the :pages chunk."
  (:require
   [reagent.core :as r]
   [shadow.lazy :as lazy]
   [galaticos.components.common :as common]))

(def dashboard (lazy/loadable galaticos.components.dashboard/dashboard))
(def aggregations-page (lazy/loadable galaticos.components.aggregations/aggregations-page))
(def player-list (lazy/loadable galaticos.components.players/player-list))
(def player-form (lazy/loadable galaticos.components.players/player-form))
(def player-detail (lazy/loadable galaticos.components.players/player-detail))
(def match-list (lazy/loadable galaticos.components.matches/match-list))
(def match-form (lazy/loadable galaticos.components.matches/match-form))
(def match-detail (lazy/loadable galaticos.components.matches/match-detail))
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
                                        (reset! err (or (.-message e) (str e)))))))
                        (catch :default e
                          (reset! err (str e)))))
                    0))]
    (cond
      @err [common/error-message (str "Falha ao carregar módulo: " @err)]
      @ready (into [@loadable] args)
      :else [common/loading-spinner])))
