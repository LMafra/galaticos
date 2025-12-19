(ns galaticos.test-runner
  "CLI entrypoint for running ClojureScript tests under Node"
  (:require [cljs.test :refer-macros [run-all-tests]]))

(defn -main []
  (run-all-tests #"galaticos\\..*"))

