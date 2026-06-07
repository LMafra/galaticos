(ns galaticos.routes
  "Frontend routing configuration

  UX-PLAN-24 (future PT hash migration — phase 1 = dual aliases, no breaking change):
  | Current route   | Proposed PT hash      |
  |-----------------|-----------------------|
  | /players        | /jogadores            |
  | /players/new    | /jogadores/novo       |
  | /players/:id    | /jogadores/:id        |
  | /matches        | /partidas             |
  | /matches/:id    | /partidas/:id         |
  | /championships  | /campeonatos          |
  | /championships/:id | /campeonatos/:id |
  | /teams          | /times                |
  | /stats          | /estatisticas         |
  | /dashboard      | /painel               |
  Risks: bookmarks, shared links, Lighthouse scripts — keep English routes as canonical until phase 2."
  (:require [reitit.core :as rc]
            [reitit.frontend :as rf]))

(def ^:private route-meta
  {"/" {:name :home :title "Dashboard"}
   "/login" {:name :login :title "Login"}
   "/dashboard" {:name :dashboard :title "Dashboard"}
   "/stats" {:name :stats :title "Estatísticas"}
   "/players" {:name :players :title "Players"}
   "/players/new" {:name :player-new :title "New Player"}
   "/players/:id/edit" {:name :player-edit :title "Edit Player"}
   "/players/:id" {:name :player-detail :title "Player Details"}
   "/matches" {:name :matches :title "Matches"}
   "/matches/new" {:name :match-new :title "New Match"}
   "/matches/championship/:championship-id" {:name :matches-by-championship
                                              :title "Partidas do Campeonato"}
   "/matches/by-championship/:championship-id/new" {:name :match-new-in-championship
                                                    :title "New Match"}
   "/matches/:id/edit" {:name :match-edit :title "Edit Match"}
   "/matches/:id" {:name :match-detail :title "Match Details"}
   "/championships" {:name :championships :title "Championships"}
   "/championships/new" {:name :championship-new :title "New Championship"}
   "/championships/:id/seasons/:season-id" {:name :championship-season-detail
                                            :title "Temporada"}
   "/championships/:id/edit" {:name :championship-edit :title "Edit Championship"}
   "/championships/:id" {:name :championship-detail :title "Championship Details"}
   "/teams" {:name :teams :title "Teams"}
   "/teams/new" {:name :team-new :title "New Team"}
   "/teams/:id/edit" {:name :team-edit :title "Edit Team"}
   "/teams/:id" {:name :team-detail :title "Team Details"}
   "/ui-lab" {:name :ui-lab :title "UI Lab"}})

;; Phase 1 PT hashes use unique Reitit names; canonicalize to EN names in `on-navigate!`.
(def ^:private pt-route-meta
  {"/entrar" {:name :entrar :title "Login"}
   "/painel" {:name :painel :title "Dashboard"}
   "/estatisticas" {:name :estatisticas :title "Estatísticas"}
   "/jogadores" {:name :jogadores :title "Players"}
   "/jogadores/novo" {:name :jogador-novo :title "New Player"}
   "/jogadores/:id/editar" {:name :jogador-editar :title "Edit Player"}
   "/jogadores/:id" {:name :jogador-detalhe :title "Player Details"}
   "/partidas" {:name :partidas :title "Matches"}
   "/partidas/nova" {:name :partida-nova :title "New Match"}
   "/partidas/campeonato/:championship-id" {:name :partidas-campeonato
                                            :title "Partidas do Campeonato"}
   "/partidas/campeonato/:championship-id/nova" {:name :partida-nova-campeonato
                                                 :title "New Match"}
   "/partidas/:id/editar" {:name :partida-editar :title "Edit Match"}
   "/partidas/:id" {:name :partida-detalhe :title "Match Details"}
   "/campeonatos" {:name :campeonatos :title "Championships"}
   "/campeonatos/novo" {:name :campeonato-novo :title "New Championship"}
   "/campeonatos/:id/temporadas/:season-id" {:name :campeonato-temporada
                                             :title "Temporada"}
   "/campeonatos/:id/editar" {:name :campeonato-editar :title "Edit Championship"}
   "/campeonatos/:id" {:name :campeonato-detalhe :title "Championship Details"}
   "/times/novo" {:name :time-novo :title "New Team"}
   "/times/:id/editar" {:name :time-editar :title "Edit Team"}})

(def route-name-aliases
  "Map PT Reitit names (phase 1) to canonical EN names used in handlers/effects."
  {:entrar :login
   :painel :dashboard
   :estatisticas :stats
   :jogadores :players
   :jogador-novo :player-new
   :jogador-detalhe :player-detail
   :jogador-editar :player-edit
   :partidas :matches
   :partida-nova :match-new
   :partidas-campeonato :matches-by-championship
   :partida-nova-campeonato :match-new-in-championship
   :partida-detalhe :match-detail
   :partida-editar :match-edit
   :campeonatos :championships
   :campeonato-novo :championship-new
   :campeonato-detalhe :championship-detail
   :campeonato-editar :championship-edit
   :campeonato-temporada :championship-season-detail
   :time-novo :team-new
   :time-editar :team-edit})

(defn canonical-route-name
  [route-name]
  (get route-name-aliases route-name route-name))

(defn- route-entries [path meta]
  [[path meta]])

(def routes
  (let [en-paths ["/"
                  "/login"
                  "/dashboard"
                  "/stats"
                  "/players"
                  "/players/new"
                  "/players/:id/edit"
                  "/players/:id"
                  "/matches"
                  "/matches/new"
                  "/matches/championship/:championship-id"
                  "/matches/by-championship/:championship-id/new"
                  "/matches/:id/edit"
                  "/matches/:id"
                  "/championships"
                  "/championships/new"
                  "/championships/:id/seasons/:season-id"
                  "/championships/:id/edit"
                  "/championships/:id"
                  "/teams"
                  "/teams/new"
                  "/teams/:id/edit"
                  "/teams/:id"
                  "/ui-lab"]
        pt-paths ["/entrar"
                  "/painel"
                  "/estatisticas"
                  "/jogadores"
                  "/jogadores/novo"
                  "/jogadores/:id/editar"
                  "/jogadores/:id"
                  "/partidas"
                  "/partidas/nova"
                  "/partidas/campeonato/:championship-id"
                  "/partidas/campeonato/:championship-id/nova"
                  "/partidas/:id/editar"
                  "/partidas/:id"
                  "/campeonatos"
                  "/campeonatos/novo"
                  "/campeonatos/:id/temporadas/:season-id"
                  "/campeonatos/:id/editar"
                  "/campeonatos/:id"
                  "/times/novo"
                  "/times/:id/editar"]]
    (vec (concat
          (mapcat #(route-entries % (get route-meta %)) en-paths)
          (mapcat #(route-entries % (get pt-route-meta %)) pt-paths)))))

(defn- send-log!
  "Lightweight debug logger that posts to the ingest endpoint.
   Fails silently if the service is not available."
  [_payload]
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
  "Generate hash href for route. Uses static `router` so links work before `rfe/start!`."
  [route-name & [path-params query-params]]
  (let [canonical (canonical-route-name route-name)
        match (if path-params
                (rc/match-by-name router canonical path-params)
                (rc/match-by-name router canonical))
        path (if query-params
               (rc/match->path match query-params)
               (:path match))]
    (str "#" path)))

(def protected-routes
  "Write/admin routes that require an authenticated session."
  #{:player-new :player-edit
    :match-new :match-new-in-championship :match-edit
    :championship-new :championship-edit
    :teams :team-detail :team-new :team-edit
    :jogador-novo :jogador-editar
    :partida-nova :partida-nova-campeonato :partida-editar
    :campeonato-novo :campeonato-editar
    :time-novo :time-editar})

(defn protected-route?
  [route-name]
  (contains? protected-routes (canonical-route-name route-name)))
