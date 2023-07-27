(ns monkey.shadow.junit.test.reporter-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as cs]
            [monkey.shadow.junit.reporter :as sut]))

(deftest update-report
  (testing "begin-run-tests"
    (let [r (sut/update-report {:type :begin-run-tests
                                ::sut/state :test-state})]
      (testing "writes `<testsuites>` tag"
        (is (cs/includes? (::sut/out r) "<testsuites>")))
    
      (testing "returns state"
        (is (= :test-state (::sut/state r))))))

  (testing "end-run-tests"
    (let [r (sut/update-report {:type :end-run-tests})]
      (testing "writes `<testsuites>` end tag"
        (is (cs/includes? (::sut/out r) "</testsuites>")))))

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
                                         :fail 1}
                                        {:name "third"
                                         :pass 1
                                         :error 2
                                         :fail 2
                                         :failures [{:message "test message"
                                                     :details "test description"}]}]}})]
      
      (testing "writes `<testsuite>` start tag for ns"
        (is (cs/includes? (::sut/out r) "<testsuite"))
        (is (cs/includes? (::sut/out r) "package=\"test.ns\"")))

      (testing "adds succes statistics to tag"
        (is (cs/includes? (::sut/out r) "tests=\"10\""))
        (is (cs/includes? (::sut/out r) "errors=\"2\""))
        (is (cs/includes? (::sut/out r) "failures=\"3\"")))

      (testing "adds `<testcase>` children"
        (is (cs/includes? (::sut/out r) "<testcase name=\"first\" classname=\"test.ns\">"))
        (is (cs/includes? (::sut/out r) "<testcase name=\"second\" classname=\"test.ns\">"))
        (is (cs/includes? (::sut/out r) "<testcase name=\"third\" classname=\"test.ns\">")))

      (testing "adds `<failure>` tags"
        (is (cs/includes? (::sut/out r) "<failure message=\"test message\">"))
        (is (cs/includes? (::sut/out r) "test description")))
      
      (testing "writes `<testsuite>` end tag"
        (is (cs/includes? (::sut/out r) "</testsuite>")))

      (testing "clears ns from state"
        (is (nil? (-> r ::sut/state :ns))))

      (testing "clears testcases from state"
        (is (nil? (-> r ::sut/state :test-cases))))))

  (testing "begin-test-var"
    (let [r (sut/update-report {:type :begin-test-var
                                :var "test var"})]
      (testing "sets var in state"
        (is (= "test var" (-> r ::sut/state :var))))))

  (testing "end-test-var"
    (let [r (sut/update-report {:type :end-test-var
                                :var #'clojure.core/str
                                ::sut/state {:var "test var"
                                             :pass 2
                                             :fail 1
                                             :error 3
                                             :failures [:fail]}})]
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

      (testing "resets test statistics"
        (is (empty? (-> r ::sut/state (select-keys [:pass :fail :error])))))))

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
