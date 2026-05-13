(ns galaticos.lazy-pages
  "Routed page components. Single release bundle (no shadow.lazy) to avoid flaky loads of /js/compiled/pages.js."
  (:require
   [galaticos.components.aggregations :as aggregations]
   [galaticos.components.championships :as championships]
   [galaticos.components.dashboard :as dashboard]
   [galaticos.components.matches :as matches]
   [galaticos.components.players :as players]
   [galaticos.components.teams :as teams]))

(def dashboard dashboard/dashboard)
(def aggregations-page aggregations/aggregations-page)
(def player-list players/player-list)
(def player-form players/player-form)
(def player-detail players/player-detail)
(def match-list matches/match-list)
(def match-form matches/match-form)
(def match-detail matches/match-detail)
(def championship-matches-page matches/championship-matches-page)
(def championship-list championships/championship-list)
(def championship-form championships/championship-form)
(def championship-detail championships/championship-detail)
(def championship-season-detail championships/championship-season-detail)
(def team-list teams/team-list)
(def team-form teams/team-form)
(def team-detail teams/team-detail)

(defn loadable-route
  "Render `comp` with optional `args`. Kept so `core.cljs` call sites stay stable."
  [comp & args]
  (into [comp] args))
