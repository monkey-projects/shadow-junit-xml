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

(defonce state (atom nil))

;; Define report methods for all test types.  They all pass on to
;; the junit reporter.

(defmethod ct/report [::junit :begin-run-tests] [m]
  (reset! state {})
  (junit/reporter state m))

(defmethod ct/report [::junit :end-run-tests] [m]
  (junit/reporter state m)
  (if (ct/successful? m)
    (js/process.exit 0)
    (js/process.exit 1)))

(def event-types [:summary
                  :begin-test-ns :end-test-ns
                  :begin-test-var :end-test-var
                  :begin-test-vars :end-test-vars
                  :begin-test-all-vars :end-test-all-vars])

(doseq [et event-types]
  (defmethod ct/report [::junit et] [m]
    (junit/reporter state m)))

(def counter-types [:pass :error :fail])

(doseq [et counter-types]
  (defmethod ct/report [::junit et] [m]
    (junit/reporter state m)
    (ct/inc-report-counter! et)))

(defn run-tests [& args]
  (stn/reset-test-data!)
  (let [state (atom nil)]
    (st/run-all-tests (ct/empty-env ::junit) nil)))
