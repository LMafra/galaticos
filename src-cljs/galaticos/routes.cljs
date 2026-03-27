(ns galaticos.routes
  "Frontend routing configuration"
  (:require [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(def routes
  [["/" {:name :home
         :title "Dashboard"}]
   ["/login" {:name :login
              :title "Login"}]
   ["/dashboard" {:name :dashboard
                  :title "Dashboard"}]
   ["/stats" {:name :stats
              :title "Estatísticas"}]
   ["/players" {:name :players
                :title "Players"}]
   ["/players/new" {:name :player-new
                    :title "New Player"}]
  ;; keep static `/players/new` first; allow broader ids (mongo ObjectId or other)
  ["/players/:id/edit" {:name :player-edit
                        :title "Edit Player"}]
  ["/players/:id" {:name :player-detail
                   :title "Player Details"}]
   ["/matches" {:name :matches
                :title "Matches"}]
   ["/matches/new" {:name :match-new
                    :title "New Match"}]
   ["/matches/by-championship/:championship-id/new" {:name :match-new-in-championship
                                                     :title "New Match"}]
   ["/matches/:id/edit" {:name :match-edit
                         :title "Edit Match"}]
   ["/matches/:id" {:name :match-detail
                    :title "Match Details"}]
   ["/championships" {:name :championships
                      :title "Championships"}]
   ["/championships/new" {:name :championship-new
                          :title "New Championship"}]
   ;; More specific paths before `/championships/:id` so Reitit matches season detail reliably.
   ["/championships/:id/seasons/:season-id" {:name :championship-season-detail
                                            :title "Temporada"}]
   ["/championships/:id/edit" {:name :championship-edit
                               :title "Edit Championship"}]
   ["/championships/:id" {:name :championship-detail
                          :title "Championship Details"}]
   ["/teams" {:name :teams
              :title "Teams"}]
   ["/teams/new" {:name :team-new
                  :title "New Team"}]
   ["/teams/:id/edit" {:name :team-edit
                       :title "Edit Team"}]
   ["/teams/:id" {:name :team-detail
                  :title "Team Details"}]])

(defn- send-log!
  "Lightweight debug logger that posts to the ingest endpoint.
   Fails silently if the service is not available."
  [_payload]
  ;; #region agent log
  ;; Disabled by default - uncomment and configure if logging service is available
  ;; (try
  ;;   (-> (js/fetch "http://127.0.0.1:7242/ingest/1c0f55b4-e5fd-4147-be12-c0c768e9871a"
  ;;                 (clj->js {:method "POST"
  ;;                           :headers {"Content-Type" "application/json"}
  ;;                           :body (.stringify js/JSON (clj->js payload))}))
  ;;       (.catch (fn [_] nil)))
  ;;   (catch :default _ nil))
  ;; #endregion
  nil)

(def router
  (let [_ (send-log! {:sessionId "debug-session"
                      :runId "run1"
                      :hypothesisId "H1"
                      :location "galaticos.routes.cljs:router"
                      :message "Building router with routes"
                      :data {:paths (map first routes)}
                      :timestamp (.now js/Date)})]
    ;; `conflicts` disabled because reitit 0.9 treats `/players/new`
    ;; and `/players/:id{...}` as conflicting even with regex constraints.
    ;; Static route still wins at runtime.
    (rf/router routes {:conflicts nil})))

(defn match-by-path
  "Match route by path"
  [path]
  (let [_ (send-log! {:sessionId "debug-session"
                      :runId "run1"
                      :hypothesisId "H2"
                      :location "galaticos.routes.cljs:match-by-path"
                      :message "Matching path"
                      :data {:path path}
                      :timestamp (.now js/Date)})]
    (rf/match-by-path router path)))

(defn href
  "Generate href for route"
  [route-name & params]
  (apply rfe/href route-name params))

