(ns galaticos.handlers.exports
  "Request handlers for CSV exports."
  (:require [clojure.string :as str]
            [galaticos.export.csv :as export-csv]
            [galaticos.db.championships :as championships-db]
            [galaticos.util.response :as resp]
            [ring.util.response :refer [response status header content-type]]
            [clojure.tools.logging :as log]))

(defn- csv-download-response [csv filename]
  (-> (response csv)
      (status 200)
      (content-type "text/csv; charset=utf-8")
      (header "Content-Disposition" (str "attachment; filename=\"" filename "\""))))

(defn- safe-filename-fragment [value]
  (-> (or value "")
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"^-+|-+$" "")))

(defn export-dashboard-csv
  [_request]
  (try
    (csv-download-response (export-csv/dashboard-csv) "galaticos-dashboard.csv")
    (catch Exception e
      (log/error e "Error exporting dashboard CSV")
      (resp/server-error "Failed to export dashboard CSV"))))

(defn export-championship-csv
  [request]
  (try
    (let [id (get-in request [:params :id])]
      (if-let [championship (championships-db/find-by-id id)]
        (if-let [csv (export-csv/championship-csv id)]
          (let [name-fragment (safe-filename-fragment (:name championship))
                filename (str (if (str/blank? name-fragment) "championship" name-fragment) ".csv")]
            (csv-download-response csv filename))
          (resp/not-found "Championship not found"))
        (resp/not-found "Championship not found")))
    (catch Exception e
      (log/error e "Error exporting championship CSV")
      (resp/server-error "Failed to export championship CSV"))))
