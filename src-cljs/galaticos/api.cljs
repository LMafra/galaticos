(ns galaticos.api
  "API client for making HTTP requests to the backend"
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! go]]
            [goog.object :as gobj]))

(def token-storage-key "galaticos.auth.token")

(defn- safe-local-storage []
  (try
    (.-localStorage js/window)
    (catch :default _e nil)))

(defn load-token []
  (when-let [ls (safe-local-storage)]
    (.getItem ls token-storage-key)))

(defn save-token! [token]
  (when-let [ls (safe-local-storage)]
    (if token
      (.setItem ls token-storage-key token)
      (.removeItem ls token-storage-key))))

(defonce ^:private auth-token (atom (load-token)))

(defn set-token! [token]
  (reset! auth-token token)
  (save-token! token))

(defn clear-token! []
  (set-token! nil))

(defn current-token []
  @auth-token)

(def api-base-url
  ;; guard against undefined process in the browser
  (or (try
        (let [proc (gobj/get js/window "process")]
          (when proc
            (let [env (gobj/get proc "env")]
              (when env
                (gobj/get env "GALATICOS_API_URL")))))
        (catch :default _ nil))
      (try
        (gobj/get js/window "GALATICOS_API_URL")
        (catch :default _ nil))
      ""))

(when (or (nil? api-base-url) (= "" api-base-url))
  (js/console.warn "GALATICOS_API_URL não definido; usando mesma origem para chamadas da API."))

(defonce _api-base-url-logged (atom false))
(when (and (some? js/goog) (.-DEBUG js/goog) (compare-and-set! _api-base-url-logged false true))
  (let [display-url (if (or (nil? api-base-url) (= "" api-base-url))
                      (str (.-origin js/window.location))
                      (try (.-host (js/URL. api-base-url)) (catch :default _ api-base-url)))]
    (js/console.info "API base URL:" display-url)))

(def success-statuses #{200 201 202 203 204})

(defn- extract-error [response]
  (let [body (:body response)]
    (cond
      (string? body) body
      (map? body) (or (:error body) (:message body) "Unknown error")
      :else (str "Request failed: " (:status response)))))

(defn handle-response
  "Handle API response and extract data or error.
   on-error is called with (message response); response includes :status for 404 etc."
  [response on-success on-error]
  (if (contains? success-statuses (:status response))
    (let [body (:body response)]
      (cond
        (nil? body) (on-success nil)
        (:success body) (on-success (:data body))
        (map? body) (on-error (or (:error body) (:message body) "Unknown error") response)
        :else (on-success body)))
    (on-error (extract-error response) response)))

(defn- auth-headers []
  (if-let [token (current-token)]
    {"Authorization" (str "Bearer " token)}
    {}))

(defn- default-opts [extra]
  (merge {:with-credentials? false
          :headers (auth-headers)}
         extra))

(defn get-request
  "Make a GET request"
  [url params on-success on-error]
  (go
    (try
      (let [response (<! (http/get (str api-base-url url) (default-opts {:query-params params})))]
        (handle-response response on-success on-error))
      (catch :default e
        (on-error (str "Network error: " (.-message e)) nil)))))

(defn post-request
  "Make a POST request"
  [url data on-success on-error]
  (go
    (try
      (let [response (<! (http/post (str api-base-url url) (default-opts {:json-params data})))]
        (handle-response response on-success on-error))
      (catch :default e
        (on-error (str "Network error: " (.-message e)) nil)))))

(defn put-request
  "Make a PUT request"
  [url data on-success on-error]
  (go
    (try
      (let [response (<! (http/put (str api-base-url url) (default-opts {:json-params data})))]
        (handle-response response on-success on-error))
      (catch :default e
        (on-error (str "Network error: " (.-message e)) nil)))))

(defn delete-request
  "Make a DELETE request"
  [url on-success on-error]
  (go
    (try
      (let [response (<! (http/delete (str api-base-url url) (default-opts {})))]
        (handle-response response on-success on-error))
      (catch :default e
        (on-error (str "Network error: " (.-message e)) nil)))))

;; Auth API
(defn login [username password on-success on-error]
  (post-request "/api/auth/login" {:username username :password password}
                (fn [data]
                  (when-let [token (:token data)]
                    (set-token! token))
                  (on-success data))
                on-error))

(defn logout [on-success on-error]
  (post-request "/api/auth/logout" {}
                (fn [data]
                  (clear-token!)
                  (on-success data))
                on-error))

(defn check-auth [on-success on-error]
  (get-request "/api/auth/check" {}
               (fn [data]
                 (when-let [token (:token data)]
                   (set-token! token))
                 (on-success (or (:user data) data)))
               on-error))

;; Players API
(defn get-players [params on-success on-error]
  (get-request "/api/players" params on-success on-error))

(defn get-player [id on-success on-error]
  (get-request (str "/api/players/" id) {} on-success on-error))

(defn create-player [data on-success on-error]
  (post-request "/api/players" data on-success on-error))

(defn update-player [id data on-success on-error]
  (put-request (str "/api/players/" id) data on-success on-error))

(defn delete-player [id on-success on-error]
  (delete-request (str "/api/players/" id) on-success on-error))

;; Championships API
(defn get-championships [params on-success on-error]
  (get-request "/api/championships" params on-success on-error))

(defn get-championship [id on-success on-error]
  (get-request (str "/api/championships/" id) {} on-success on-error))

(defn get-championship-players [id on-success on-error]
  (get-request (str "/api/championships/" id "/players") {} on-success on-error))

(defn create-championship [data on-success on-error]
  (post-request "/api/championships" data on-success on-error))

(defn update-championship [id data on-success on-error]
  (put-request (str "/api/championships/" id) data on-success on-error))

(defn delete-championship [id on-success on-error]
  (delete-request (str "/api/championships/" id) on-success on-error))

(defn enroll-player-in-championship [championship-id player-id on-success on-error]
  (post-request (str "/api/championships/" championship-id "/enroll/" player-id) {} on-success on-error))

(defn unenroll-player-from-championship [championship-id player-id on-success on-error]
  (delete-request (str "/api/championships/" championship-id "/unenroll/" player-id) on-success on-error))

(defn finalize-championship [championship-id winner-player-ids titles-award-count on-success on-error]
  (post-request (str "/api/championships/" championship-id "/finalize")
                {:winner-player-ids winner-player-ids
                 :titles-award-count (if (number? titles-award-count) titles-award-count (js/parseInt (str titles-award-count) 10))}
                on-success
                on-error))

;; Matches API
(defn get-matches [params on-success on-error]
  (get-request "/api/matches" params on-success on-error))

(defn get-match [id on-success on-error]
  (get-request (str "/api/matches/" id) {} on-success on-error))

(defn create-match [data on-success on-error]
  (post-request "/api/matches" data on-success on-error))

(defn update-match [id data on-success on-error]
  (put-request (str "/api/matches/" id) data on-success on-error))

(defn delete-match [id on-success on-error]
  (delete-request (str "/api/matches/" id) on-success on-error))

;; Teams API
(defn get-teams [on-success on-error]
  (get-request "/api/teams" {} on-success on-error))

(defn get-team [id on-success on-error]
  (get-request (str "/api/teams/" id) {} on-success on-error))

(defn create-team [data on-success on-error]
  (post-request "/api/teams" data on-success on-error))

(defn update-team [id data on-success on-error]
  (put-request (str "/api/teams/" id) data on-success on-error))

(defn delete-team [id on-success on-error]
  (delete-request (str "/api/teams/" id) on-success on-error))

(defn add-player-to-team [team-id player-id on-success on-error]
  (post-request (str "/api/teams/" team-id "/players/" player-id) {} on-success on-error))

(defn remove-player-from-team [team-id player-id on-success on-error]
  (delete-request (str "/api/teams/" team-id "/players/" player-id) on-success on-error))

;; Aggregations API
(defn get-dashboard-stats [on-success on-error]
  (get-request "/api/aggregations/stats" {} on-success on-error))

(defn reconcile-stats [on-success on-error]
  (post-request "/api/aggregations/stats/reconcile" {} on-success on-error))

(defn get-player-stats-by-championship [championship-id on-success on-error]
  (get-request (str "/api/aggregations/players/stats/" championship-id) {} on-success on-error))

(defn get-top-players [params on-success on-error]
  (get-request "/api/aggregations/players/top" params on-success on-error))

(defn search-players [params on-success on-error]
  (get-request "/api/aggregations/players/search" params on-success on-error))

(defn get-championship-comparison [on-success on-error]
  (get-request "/api/aggregations/championships/comparison" {} on-success on-error))

(defn get-player-evolution [player-id on-success on-error]
  (get-request (str "/api/aggregations/players/" player-id "/evolution") {} on-success on-error))

(defn get-avg-goals-by-position [championship-id on-success on-error]
  (get-request (str "/api/aggregations/positions/" championship-id) {} on-success on-error))

