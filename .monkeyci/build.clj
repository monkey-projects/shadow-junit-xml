(ns shadow-junit-xml.build
  (:require [monkey.ci.build.core :as bc]
            [monkey.ci.plugin
             [clj :as clj]
             [github :as gh]]
            [monkey.ci.ext.junit]))

(def test
  (bc/container-job
   "test"
   {:image "docker.io/monkeyci/clojure-node:1.11.4"
    :script
    ["npm install"
     ;; Run using clojure so we can specify the local mvn repo path
     "clojure -Sdeps '{:mvn/local-repo \".m2\"}' -M:test -m shadow.cljs.devtools.cli release test-ci"
     "node target/js/node-tests.js 1>junit.xml"]
    
    :caches
    [{:id "mvn-repo"
      :path ".m2"}
     {:id "node-modules"
      :path "node_modules"}]
    
    :save-artifacts
    [{:id "test-results"
      :path "junit.xml"}]

    :junit
    {:artifact-id "test-results"
     :path "junit.xml"}}))

(def deploy
  (clj/deps-publish {}))

(def release
  (gh/release-job {}))

;; Jobs
[test
 deploy
 release]
