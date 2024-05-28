(ns monkey.shadow.junit.reporter
  (:require [clojure.string :as cs]))

(defn printerr [msg & args]
  (binding [*print-fn* *print-err-fn*]
    (apply println msg args)))

(defmulti update-report :type)

(defmethod update-report :default [ctx]
  ;; Noop
  (printerr "Unsupported test tag type:" (:type ctx))
  (printerr "All properties:" ctx)
  ctx)

(defn- add-out [ctx msg]
  (assoc ctx ::out msg))

(defn- update-state [ctx f & args]
  (apply update ctx ::state f args))

(defmethod update-report :begin-run-tests [ctx]
  (add-out ctx "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites>"))

(defmethod update-report :end-run-tests [ctx]
  (add-out ctx "</testsuites>"))

(defmethod update-report :begin-test-ns [ctx]
  (update-state ctx assoc :ns (:ns ctx)))

(defmethod update-report :end-test-ns [ctx]
  (let [{:keys [test-cases ns]} (::state ctx)
        calc-total (fn [k]
                     (reduce (fn [r tc]
                               (+ r (get tc k 0)))
                             0
                             test-cases))
        [pass fail error] (mapv calc-total [:pass :fail :error])
        header (str "  <testsuite package=\"" ns "\" "
                    "tests=\"" (+ pass fail error) "\" "
                    "failures=\"" fail "\" "
                    "errors=\"" error "\">")
        format-failure (fn [{:keys [message details]}]
                         ;; TODO Escaping
                         (str "      <failure message=\"" message "\">"
                              "<![CDATA[" details "]]>"))
        format-testcase (fn [{:keys [name failures]}]
                          (cs/join "\n"
                                   (concat
                                    [(str "    <testcase name=\"" name "\" classname=\"" ns "\">")]
                                    (map format-failure failures)
                                    ["    </testcase>"])))]
    (-> ctx
        (add-out (cs/join "\n" (concat [header]
                                       (map format-testcase test-cases)
                                       ["  </testsuite>"])))
        (update-state dissoc :ns :test-cases))))

(defmethod update-report :begin-test-var [{:keys [var] :as ctx}]
  (assoc-in ctx [::state :var] var))

(defmethod update-report :end-test-var [{:keys [var] :as ctx}]
  (-> ctx
      (update-state (fn [s]
                      (update s :test-cases conj (-> {:name (name (:name (meta var)))}
                                                     (merge (select-keys s [:pass :fail :error :failures]))))))
      (update-state dissoc :var :pass :fail :error :failures)))

(defmethod update-report :pass [ctx]
  (update-state ctx update :pass (fnil inc 0)))

(defn- build-error-details [ctx]
  (->> [:message :expected :actual]
       (map (partial get ctx))
       (remove nil?)
       (zipmap ["" "Expected: " "Actual: "])
       (map (partial apply str))
       (cs/join "\n")))

(defn- build-error-msg [{:keys [file line]}]
  (str "File: " file ", line: " line))

(defn- handle-error [ctx id]
  (-> ctx
      (update-state update id (fnil inc 0))
      (update-state update :failures conj
                    {:message (build-error-msg ctx)
                     :details (build-error-details ctx)})))

(defmethod update-report :fail [ctx]
  (handle-error ctx :fail))

(defmethod update-report :error [ctx]
  (handle-error ctx :error))

(defmethod update-report :summary [{:keys [pass fail error test] :as ctx}]
  (printerr "Test executed:" test "total," pass "passed," fail "failed," error "error(s)")
  ctx)

(defn reporter [state {:keys [type] :as ctx}]
  ;; Not for parallel execution, but that's ok in node
  (let [{:keys [::out] new-state ::state :as new-ctx} (update-report (assoc ctx ::state @state))]
    (reset! state new-state)
    ;; Print output, if any
    (when out
      (println out))))
