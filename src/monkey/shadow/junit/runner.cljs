(ns monkey.shadow.junit.runner
  (:require [cljs.test :as ct]
            [monkey.shadow.junit.reporter :as junit]
            [shadow.test :as st]
            [shadow.test.node :as stn]))

;;; The test runner cannot be tested because the node compilation will get
;;; into a waiting loop.  It cannot compile the test because it waits for
;;; compilation of this namespace, which waits on the `shadow.test.node`
;;; namespace.  So we put the `junit-reporter` in a different ns to break
;;; this loop.

(defn run-tests [& args]
  (stn/reset-test-data!)
  (let [state (atom nil)]
    (st/run-all-tests (-> (ct/empty-env)
                          (assoc :report-fn (partial junit/reporter state)))
                      nil)))
