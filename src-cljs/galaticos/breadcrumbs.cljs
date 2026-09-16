(ns galaticos.breadcrumbs
  "Pure breadcrumb builders. Labels live here — components only render."
  (:require [clojure.string :as str]
            [galaticos.routes :as routes]))

(def route-labels
  "Static PT labels for hub routes (Reitit names stay English)."
  {:dashboard "Dashboard"
   :championships "Campeonatos"
   :players "Jogadores"
   :matches "Partidas"
   :teams "Times"
   :stats "Estatísticas"
   :championship-new "Novo"
   :championship-edit "Editar"
   :player-new "Novo"
   :player-edit "Editar"
   :team-new "Novo"
   :team-edit "Editar"
   :match-new "Nova partida"
   :match-edit "Editar partida"})

(defn label-for
  "Lookup static label; fallback to string name."
  [route-name]
  (or (get route-labels route-name)
      (some-> route-name name)))

(defn- blank-str? [s]
  (str/blank? (str s)))

(defn- crumb
  ([label] {:label (str label)})
  ([label route] {:label (str label) :route route})
  ([label route route-params]
   (cond-> {:label (str label) :route route}
     (map? route-params) (assoc :route-params route-params))))

(defn build-breadcrumbs
  "Pure trail for deep hubs. Returns vector of {:label :route? :route-params?}.
   Last crumb has no actionable :route (caller may still pass :route; UI ignores it).

   opts:
   - :entity-label — current resource display name (player, championship, match…)
   - :championship-label — parent championship name when needed
   - :season-label — season display name
   - :team-label — parent team name when linking team → player"
  [route-name path-params & {:keys [entity-label championship-label season-label team-label]
                             :as _opts}]
  (let [route-name (routes/canonical-route-name route-name)
        params (or path-params {})
        id (some-> (:id params) str)
        champ-id (some-> (or (:championship-id params) id) str)]
    (case route-name
      :championship-detail
      [(crumb (label-for :championships) :championships)
       (crumb (or entity-label championship-label "Campeonato"))]

      :championship-season-detail
      [(crumb (label-for :championships) :championships)
       (crumb (or championship-label "Campeonato")
              :championship-detail
              (when-not (blank-str? id) {:id id}))
       (crumb (or season-label entity-label "Temporada"))]

      :championship-edit
      [(crumb (label-for :championships) :championships)
       (crumb (or entity-label championship-label "Campeonato")
              :championship-detail
              (when-not (blank-str? id) {:id id}))
       (crumb (label-for :championship-edit))]

      :championship-new
      [(crumb (label-for :championships) :championships)
       (crumb (label-for :championship-new))]

      :player-detail
      (cond-> [(crumb (label-for :players) :players)]
        (and team-label (not (blank-str? (:team-id params))))
        (conj (crumb team-label :team-detail {:id (str (:team-id params))}))
        true
        (conj (crumb (or entity-label "Jogador"))))

      :player-edit
      [(crumb (label-for :players) :players)
       (crumb (or entity-label "Jogador")
              :player-detail
              (when-not (blank-str? id) {:id id}))
       (crumb (label-for :player-edit))]

      :player-new
      [(crumb (label-for :players) :players)
       (crumb (label-for :player-new))]

      :team-detail
      [(crumb (label-for :teams) :teams)
       (crumb (or entity-label "Time"))]

      :team-edit
      [(crumb (label-for :teams) :teams)
       (crumb (or entity-label "Time")
              :team-detail
              (when-not (blank-str? id) {:id id}))
       (crumb (label-for :team-edit))]

      :team-new
      [(crumb (label-for :teams) :teams)
       (crumb (label-for :team-new))]

      :match-detail
      (cond-> [(crumb (label-for :championships) :championships)
               (crumb (label-for :matches) :matches)]
        (not (blank-str? champ-id))
        (conj (crumb (or championship-label "Campeonato")
                     :matches-by-championship
                     {:championship-id champ-id}))
        true
        (conj (crumb (or entity-label "Partida"))))

      :match-edit
      (cond-> [(crumb (label-for :championships) :championships)
               (crumb (label-for :matches) :matches)]
        (not (blank-str? champ-id))
        (conj (crumb (or championship-label "Campeonato")
                     :matches-by-championship
                     {:championship-id champ-id}))
        true
        (conj (crumb (or entity-label (label-for :match-edit)))))

      (:match-new :match-new-in-championship)
      (cond-> [(crumb (label-for :championships) :championships)
               (crumb (label-for :matches) :matches)]
        (not (blank-str? champ-id))
        (conj (crumb (or championship-label "Campeonato")
                     :matches-by-championship
                     {:championship-id champ-id}))
        true
        (conj (crumb (or entity-label (label-for :match-new)))))

      :matches-by-championship
      [(crumb (label-for :championships) :championships)
       (crumb (label-for :matches) :matches)
       (crumb (or entity-label championship-label "Campeonato"))]

      ;; Unknown → empty (caller may fall back)
      [])))
