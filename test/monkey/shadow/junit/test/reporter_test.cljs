(ns monkey.shadow.junit.test.reporter-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as cs]
            [monkey.shadow.junit.reporter :as sut]
            ["xml" :as xml]))

(deftest update-report
  (testing "begin-run-tests"
    (let [r (sut/update-report {:type :begin-run-tests
                                ::sut/state :test-state})]
      (testing "writes `<testsuites>` tag"
        (is (cs/includes? (::sut/out r) "<testsuites>")))
    
      (testing "returns state"
        (is (= :test-state (::sut/state r))))))

  (testing "end-run-tests"
    (let [r (sut/update-report {:type :end-run-tests
                                ::sut/state :test-state})]
      (testing "writes `<testsuites>` end tag"
        (is (cs/includes? (::sut/out r) "</testsuites>")))
      
      (testing "returns state"
        (is (= :test-state (::sut/state r))))))

  (testing "begin-test-ns"
    (let [r (sut/update-report {:type :begin-test-ns
                                :ns "test.ns"})]
      (testing "sets ns in state"
        (is (= "test.ns" (-> r ::sut/state :ns))))))

  (testing "end-test-ns"
    (let [r (sut/update-report
             {:type :end-test-ns
              :ns "test.ns"
              ::sut/state {:ns "test.ns"
                           :var :test-var
                           :test-cases [{:name "first"
                                         :pass 2
                                         :error 0
                                         :fail 0}
                                        {:name "second"
                                         :pass 2
                                         :error 0
                                         :fail 1
                                         :time 0.2}
                                        {:name "third"
                                         :pass 1
                                         :error 2
                                         :fail 2
                                         :failures [{:message "test message"
                                                     :details "test description"}]}]}})
          out (::sut/out r)]
      
      (testing "writes `<testsuite>` start tag for ns"
        (is (= :testsuite (first (keys out))))
        (is (= {:_attr {:name "test.ns"
                        :package "test.ns"
                        :errors 2
                        :failures 3
                        :tests 10
                        :time 0.2}}
               (first (:testsuite out)))))

      (testing "adds `<testcase>` without failures"
        (is (= {:testcase [{:_attr {:name "first"
                                    :classname "test.ns"}}]}
               (-> out :testsuite second))))

      (testing "adds time when specified"
        (is (= 0.2
               (-> out
                   :testsuite
                   (nth 2)
                   :testcase
                   first
                   :_attr
                   :time))))

      (testing "adds `<failure>` tags"
        (is (= {:testcase [{:_attr
                            {:name "third"
                             :classname "test.ns"}}
                           {:failure
                            {:_attr {:message "test message"}
                             :_cdata "test description"}}]}
               (-> out :testsuite (nth 3)))))
      
      (testing "clears ns from state"
        (is (nil? (-> r ::sut/state :ns))))

      (testing "clears testcases from state"
        (is (nil? (-> r ::sut/state :test-cases))))))

  (testing "begin-test-var"
    (let [r (sut/update-report {:type :begin-test-var
                                :var "test var"})]
      (testing "sets var in state"
        (is (= "test var" (-> r ::sut/state :var))))

      (testing "sets current time"
        (is (number? (-> r ::sut/state :time))))))

  (testing "end-test-var"
    (let [r (sut/update-report {:type :end-test-var
                                :var #'clojure.core/str
                                ::sut/state {:var "test var"
                                             :pass 2
                                             :fail 1
                                             :error 3
                                             :failures [:fail]
                                             :time 100}})]
      (testing "clears var from state"
        (is (nil? (-> r ::sut/state :var))))

      (testing "adds testcase to state"
        (is (= "str" (-> r
                         ::sut/state
                         :test-cases
                         (first)
                         :name))))

      (testing "adds test statistics to case"
        (is (= {:pass 2
                :fail 1
                :error 3
                :failures [:fail]}
               (-> r
                   ::sut/state
                   :test-cases
                   (first)
                   (select-keys [:pass :fail :error :failures])))))

      (testing "calculates elapsed time in seconds"
        (is (number? (-> r ::sut/state :test-cases first :time))))

      (testing "resets test statistics"
        (is (empty? (-> r ::sut/state (select-keys [:pass :fail :error :time])))))))

  (testing "pass"
    (testing "sets pass in state"
      (is (= 1 (-> (sut/update-report {:type :pass})
                   ::sut/state
                   :pass))))
      
    (testing "increases pass in state"
      (is (= 2 (-> (sut/update-report {:type :pass
                                       ::sut/state {:pass 1}})
                   ::sut/state
                   :pass)))))

  (testing "fail"
    (testing "sets fail in state"
      (is (= 1 (-> (sut/update-report {:type :fail})
                   ::sut/state
                   :fail))))
      
    (testing "increases fail in state"
      (is (= 2 (-> (sut/update-report {:type :fail
                                       ::sut/state {:fail 1}})
                   ::sut/state
                   :fail))))

    (testing "adds error message"
      (let [r (sut/update-report {:type :fail
                                  :expected "a"
                                  :actual "b"
                                  :message "Test error"
                                  :file "test.cljs"
                                  :line 100})
            f (-> r ::sut/state :failures first)]
        (is (= "Test error\nExpected: a\nActual: b" (:details f)))
        (is (= "File: test.cljs, line: 100" (:message f))))))

  (testing "error"
    (testing "sets error in state"
      (is (= 1 (-> (sut/update-report {:type :error})
                   ::sut/state
                   :error))))
      
    (testing "increases error in state"
      (is (= 2 (-> (sut/update-report {:type :error
                                       ::sut/state {:error 1}})
                   ::sut/state
                   :error))))

    (testing "adds error message"
      (let [r (sut/update-report {:type :error
                                  :expected "a"
                                  :actual "b"
                                  :message "Test error"
                                  :file "test.cljs"
                                  :line 100})
            f (-> r ::sut/state :failures first)]
        (is (= "Test error\nExpected: a\nActual: b" (:details f)))
        (is (= "File: test.cljs, line: 100" (:message f)))))))

(deftest xml-test
  (testing "xml exists"
    (is (exists? xml)))

  (testing "creates xml from structure"
    (is (= "<test-tag></test-tag>"
           (xml (clj->js {:test-tag []}))))
    (is (= "<head title=\"test head\"/>"
           (xml (clj->js {:head {:_attr {:title "test head"}}}))))))
