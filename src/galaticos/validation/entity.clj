(ns galaticos.validation.entity
  "Entity body validation: allowed/required fields and normalization at the HTTP boundary."
  (:require [galaticos.util.response :as resp]
            [clojure.string :as str]))

(def allowed-championship-fields
  #{:name :season :status :format :start-date :end-date :location :notes :titles-count
    :max-players :enrolled-player-ids :winner-player-ids :finished-at})

(def required-championship-fields
  #{:name :season :titles-count})

(def allowed-match-fields
  #{:championship-id :season-id :home-team-id :away-team-id :date :location :round
    :status :player-statistics :notes :opponent :venue :result :away-score})

(def required-match-fields
  #{:championship-id :player-statistics})

(def allowed-player-stat-fields
  #{:player-id :player-name :position :team-id :goals :assists :yellow-cards :red-cards
    :minutes-played})

(def required-player-stat-fields
  #{:player-id :team-id})

(defn- normalize-championship-body [body]
  (let [max-players (:max-players body)]
    (cond-> body
      (some? max-players)
      (update :max-players (fn [value]
                             (cond
                               (number? value) value
                               (string? value) (let [trimmed (str/trim value)]
                                                 (when (not (str/blank? trimmed))
                                                   (Long/parseLong trimmed)))
                               :else value)))
      (:enrolled-player-ids body)
      (update :enrolled-player-ids (fn [ids]
                                     (mapv resp/->object-id ids))))))

(defn validate-championship-body
  ([body] (validate-championship-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-championship-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-championship-fields)))
           normalized (normalize-championship-body body)
           max-players (:max-players normalized)]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         (and (some? max-players) (or (not (number? max-players)) (neg? max-players)))
         {:error "max-players must be a non-negative number"}
         :else {:data normalized})))))

(defn validate-player-stats [stats]
  (cond
    (not (sequential? stats)) {:error "player-statistics must be a non-empty vector"}
    (empty? stats) {:error "player-statistics must be a non-empty vector"}
    :else
    (let [validated (mapv (fn [stat]
                            (when-not (map? stat)
                              (throw (ex-info "Each player-statistics entry must be an object" {:status 400})))
                            (let [unknown (seq (remove allowed-player-stat-fields (keys stat)))
                                  missing (seq (filter #(nil? (get stat %)) required-player-stat-fields))]
                              (when unknown
                                (throw (ex-info (str "Unknown player-statistics fields: "
                                                     (str/join ", " unknown)) {:status 400})))
                              (when missing
                                (throw (ex-info (str "Missing required player-statistics fields: "
                                                     (str/join ", " missing)) {:status 400})))
                              (cond-> stat
                                (:player-id stat) (update :player-id resp/->object-id)
                                (:team-id stat) (update :team-id resp/->object-id))))
                          stats)]
      {:data validated})))

(def allowed-player-fields
  #{:name :nickname :position :team-id :birth-date :nationality :height :weight
    :preferred-foot :shirt-number :active :email :phone :number :photo-url :notes})

(def required-player-fields
  #{:name :position})

(def allowed-team-fields
  #{:name :abbreviation :category :city :coach :stadium :founded-year :logo-url
    :active-player-ids :notes})

(def required-team-fields
  #{:name})

(def allowed-season-fields
  #{:season :status :format :start-date :end-date :titles-count
    :enrolled-player-ids :winner-player-ids})

(def required-season-fields
  #{:season})

(defn validate-player-body
  ([body] (validate-player-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-player-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-player-fields)))]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else {:data (cond-> body
                        (:team-id body) (update :team-id resp/->object-id))})))))

(defn validate-team-body
  ([body] (validate-team-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-team-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-team-fields)))
           normalized (cond-> body
                        (:active-player-ids body)
                        (update :active-player-ids #(mapv resp/->object-id %)))]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else {:data normalized})))))

(defn- normalize-season-body [body]
  (cond-> body
    (:enrolled-player-ids body) (update :enrolled-player-ids #(mapv resp/->object-id %))
    (:winner-player-ids body) (update :winner-player-ids #(mapv resp/->object-id %))))

(defn validate-season-body
  ([body] (validate-season-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-season-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-season-fields)))
           normalized (normalize-season-body body)]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else {:data normalized})))))

(defn validate-match-body
  ([body] (validate-match-body body true))
  ([body require-required?]
   (cond
     (not (map? body)) {:error "Invalid request body"}
     :else
     (let [unknown (seq (remove allowed-match-fields (keys body)))
           missing (when require-required?
                     (seq (filter #(nil? (get body %)) required-match-fields)))
           stats-present? (contains? body :player-statistics)]
       (cond
         unknown {:error (str "Unknown fields: " (str/join ", " unknown))}
         missing {:error (str "Missing required fields: " (str/join ", " missing))}
         :else
         (try
           (let [{stats :data stats-error :error}
                 (when stats-present?
                   (validate-player-stats (:player-statistics body)))]
             (if stats-error
               {:error stats-error}
               {:data (cond-> body
                        (:championship-id body) (update :championship-id resp/->object-id)
                        (:season-id body) (update :season-id resp/->object-id)
                        (:home-team-id body) (update :home-team-id resp/->object-id)
                        (:away-team-id body) (update :away-team-id resp/->object-id)
                        stats (assoc :player-statistics stats))}))
           (catch Exception e
             {:error (.getMessage e)})))))))
