(ns galaticos.analytics.player-stats-jobs-test
  (:require [clojure.test :refer [deftest is testing]]
            [galaticos.analytics.player-stats-job-store :as job-store]
            [galaticos.analytics.player-stats-jobs :as jobs]
            [galaticos.db.aggregations :as agg])
  (:import [org.bson.types ObjectId]))

(def ^:private sample-payload
  {:reason :after-match-test
   :op :create
   :match-id (ObjectId.)
   :affected-player-ids [(ObjectId.)]})

(deftest submit-never-throws-when-recompute-fails
  (testing "synchronous path (test binding) swallows exception from incremental update"
    (binding [jobs/*synchronous-refresh* true]
      (with-redefs [agg/update-incremental-player-stats! (fn [_] (throw (ex-info "boom" {})))]
        (is (nil? (jobs/submit-incremental-recalc-after-match! sample-payload)))))))

(deftest synchronous-full-recompute-returns-error-map
  (testing "when agg throws, returns :status :error without throwing"
    (with-redefs [agg/update-all-player-stats (fn [] (throw (Exception. "fail")))]
      (let [r (jobs/synchronous-full-recompute! :test)]
        (is (= :error (:status r)))
        (is (string? (:message r)))))))

(deftest submit-full-recompute-returns-job-id
  (let [done (promise)]
    (with-redefs [agg/update-all-player-stats
                  (fn []
                    (deliver done :ran)
                    {:status :success :updated 0})]
      (let [r (jobs/submit-full-recompute! :test-reason)]
        (is (= :ok (:status r)))
        (is (string? (:job-id r)))
        (is (= :ran (deref done 5000 :timeout)) "background worker should finish")))))

(deftest incremental-retry-succeeds-after-transient-failure
  (testing "second attempt succeeds; record-incremental-success called once"
    (let [calls (atom 0)
          recorded (atom nil)]
      (with-redefs [job-store/record-incremental-success! (fn [job-id reason payload recalc r _dur]
                                                            (reset! recorded {:recalc recalc :updated (:updated r)})
                                                            nil)
                    agg/update-incremental-player-stats! (fn [_]
                                                             (if (< (swap! calls inc) 2)
                                                               (throw (Exception. "transient"))
                                                               {:updated 2}))]
        (binding [jobs/*synchronous-refresh* true]
          (is (nil? (jobs/submit-incremental-recalc-after-match! sample-payload)))
          (is (= 2 @calls) "should retry once")
          (is (= {:recalc :incremental :updated 2} @recorded)))))))

(deftest incremental-success-not-recorded-on-total-failure
  (let [recorded? (atom false)]
    (with-redefs [job-store/record-incremental-success! (fn [& _] (reset! recorded? true) nil)
                  agg/update-incremental-player-stats! (fn [_] (throw (Exception. "hard")))]
      (binding [jobs/*synchronous-refresh* true]
        (is (nil? (jobs/submit-incremental-recalc-after-match! sample-payload)))
        (is (not @recorded?) "no success record when all attempts fail")))))
