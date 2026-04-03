(ns galaticos.middleware.gzip-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.middleware.gzip :as gzip])
  (:import [java.io ByteArrayInputStream File]
           [java.util.zip GZIPInputStream]))

(defn- big-string []
  (apply str (repeat 250 \a)))

(deftest accepts-gzip?-test
  (is (true? (gzip/accepts-gzip? {:headers {"accept-encoding" "gzip"}})))
  (is (true? (gzip/accepts-gzip? {:headers {"Accept-Encoding" "gzip, deflate"}})))
  (is (false? (gzip/accepts-gzip? {:headers {"accept-encoding" "gzip;q=0"}})))
  (is (not (gzip/accepts-gzip? {:headers {}}))))

(deftest supported-response?-test
  (is (true? (gzip/supported-response? {:status 200 :headers {} :body (big-string)})))
  (is (false? (gzip/supported-response? {:status 200 :headers {} :body "short"})))
  (is (false? (gzip/supported-response? {:status 200 :headers {"Content-Encoding" "gzip"}
                                         :body (big-string)})))
  (is (true? (gzip/supported-response? {:status 200 :headers {}
                                         :body (ByteArrayInputStream. (.getBytes (big-string)))})))
  (let [f (doto (File/createTempFile "galaticos-gzip" ".txt")
            .deleteOnExit)]
    (spit f (big-string))
    (is (true? (gzip/supported-response? {:status 200 :headers {} :body f})))))

(deftest gzip-response-test
  (testing "gzips long string when client accepts"
    (let [req {:headers {"accept-encoding" "gzip"}}
          body (big-string)
          resp (gzip/gzip-response req {:status 200 :headers {} :body body})]
      (is (= "gzip" (get-in resp [:headers "Content-Encoding"])))
      (is (nil? (get-in resp [:headers "Content-Length"])))
      (is (instance? java.io.InputStream (:body resp)))
      (with-open [in (GZIPInputStream. (:body resp))]
        (is (= body (slurp in :encoding "UTF-8"))))))
  (testing "leaves response unchanged when no gzip"
    (let [req {:headers {}}
          r {:status 200 :headers {} :body (big-string)}]
      (is (= r (gzip/gzip-response req r)))))
  (testing "leaves short body unchanged"
    (let [req {:headers {"accept-encoding" "gzip"}}
          r {:status 200 :headers {} :body "tiny"}]
      (is (= r (gzip/gzip-response req r))))))

(deftest wrap-gzip-test
  (let [handler (fn [_] {:status 200 :headers {} :body (big-string)})
        wrapped (gzip/wrap-gzip handler)
        resp (wrapped {:headers {"accept-encoding" "gzip"}})]
    (is (= 200 (:status resp)))
    (is (= "gzip" (get-in resp [:headers "Content-Encoding"])))))
