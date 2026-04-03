(ns galaticos.middleware.gzip
  "Gzip Ring responses when Accept-Encoding allows it.
  Vendored from org.clj-commons/ring-gzip-middleware 0.1.9 (Apache 2.0) — ring-core does not ship wrap-gzip."
  (:require [clojure.java.io :as io]
            clojure.reflect)
  (:import (java.util.zip GZIPOutputStream)
           (java.io InputStream
                    OutputStream
                    Closeable
                    File
                    PipedInputStream
                    PipedOutputStream)))

(def ^:private flushable-gzip?
  (delay (->> (clojure.reflect/reflect GZIPOutputStream)
              :members
              (some (comp '#{[java.io.OutputStream boolean]} :parameter-types)))))

(defn- piped-gzipped-input-stream*
  []
  (proxy [PipedInputStream] []))

(defmethod @#'io/do-copy [(class (piped-gzipped-input-stream*)) OutputStream]
  [^InputStream input ^OutputStream output opts]
  (let [buffer (make-array Byte/TYPE (or (:buffer-size opts) 1024))]
    (loop []
      (let [size (.read input buffer)]
        (when (pos? size)
          (.write output buffer 0 size)
          (.flush output)
          (recur))))))

(defn piped-gzipped-input-stream [in]
  (let [pipe-in (piped-gzipped-input-stream*)
        pipe-out (PipedOutputStream. pipe-in)]
    (future
      (with-open [out (if @flushable-gzip?
                        (GZIPOutputStream. pipe-out true)
                        (GZIPOutputStream. pipe-out))]
        (if (seq? in)
          (doseq [string in]
            (io/copy (str string) out)
            (.flush out))
          (io/copy in out)))
      (when (instance? Closeable in)
        (.close ^Closeable in)))
    pipe-in))

(defn set-response-headers
  [headers]
  (-> headers
      (assoc "Content-Encoding" "gzip")
      (dissoc "Content-Length")))

(defn gzipped-response [resp]
  (-> resp
      (update :headers set-response-headers)
      (update :body piped-gzipped-input-stream)))

(defn accepts-gzip?
  [{:keys [headers]}]
  (let [accepts (or (get headers "accept-encoding")
                    (get headers "Accept-Encoding")
                    "")
        match (re-find #"(gzip|\*)(;q=((0|1)(.\d+)?))?" accepts)]
    (and match (not (contains? #{"0" "0.0" "0.00" "0.000"} (match 3))))))

(def ^:private default-status 200)

(def supported-status? #{200 201 202 203 204 205})

(def min-length 200)

(defn content-encoding?
  [{:keys [headers]}]
  (or (get headers "Content-Encoding")
      (get headers "content-encoding")))

(defn supported-response?
  [{:keys [body status] :as resp}]
  (and (supported-status? (or status default-status))
       (not (content-encoding? resp))
       (or
        (and (string? body) (> (count body) min-length))
        (instance? InputStream body)
        (instance? File body)
        (and (seq? body) @flushable-gzip?))))

(defn gzip-response [req resp]
  (if (and (supported-response? resp)
           (accepts-gzip? req))
    (gzipped-response resp)
    resp))

(defn wrap-gzip
  [handler]
  (fn
    ([request]
     (gzip-response request (handler request)))
    ([request respond raise]
     (handler
      request
      (fn [response]
        (respond (gzip-response request response)))
      raise))))
