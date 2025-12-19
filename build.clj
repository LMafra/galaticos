(ns build
  "Shadow-cljs build orchestration for dev/prod."
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as server]))

(def build-id :app)

(defn- start-server! []
  ;; Idempotent start so scripts can run repeatedly.
  (server/start!))

(defn build-once!
  "Compile the CLJS bundle once. Use :prod for optimized release."
  [{:keys [mode] :or {mode :dev}}]
  (start-server!)
  (case mode
    :prod (do (println "Building shadow-cljs release for" build-id)
              (shadow/release build-id))
    :dev  (do (println "Building shadow-cljs dev compile for" build-id)
              (shadow/compile build-id))
    (println "Unknown mode" mode))
  (server/stop!)
  (shutdown-agents)
  (println "✓ CLJS build finished for" (name mode)))

(defn -main
  "Usage: clj -M:build:frontend [dev|prod]"
  [& args]
  (let [mode (keyword (or (first args) "dev"))]
    (build-once! {:mode mode})))

