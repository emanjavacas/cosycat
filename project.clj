(defproject cleebo "0.1.0-SNAPSHOT"
  :description "Corpus query interface plus annotations"
  :license {:name "GNU v3.0"}  
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.novemberain/monger "3.0.0-rc2"]
                 [com.taoensso/timbre "4.1.4"]
                 [prismatic/schema "1.1.0"]
                 [prismatic/schema-generators "0.1.0"]
                 [binaryage/devtools "0.5.2"]
                 [prone "0.8.2"]
                 [environ "1.0.1"]
                 [selmer "0.9.3"]
                 [hiccup "1.0.5"]
                 [reagent "0.6.0-alpha"
                  :exclusions [cljsjs/react]]
                 [cljsjs/react-bootstrap "0.28.1-1"
                  :exclusions [org.webjars.bower/jquery cljsjs/react]]
                 [cljsjs/react-with-addons "0.14.3-0"] 
                 [re-frame "0.7.0-alpha"]
                 [secretary "1.2.3"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [ring-ttl-session "0.3.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [ring-transit "0.1.4"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [ring "1.4.0" :exclusions [ring/ring-jetty-adapter]]                 
                 [http-kit "2.1.18"]
                 [compojure "1.4.0"]
                 [jarohen/chord "0.7.0"]
                 [cljs-ajax "0.5.2"]
                 [buddy/buddy-auth "0.8.1"]
                 [buddy/buddy-hashers "0.9.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [cqp-clj "0.1.0-SNAPSHOT"]
                 [org.hackrslab/random-avatar "0.2.3"]
                 ; blacklab
                 [org.apache.lucene/lucene-core "4.2.1"]
                 [org.apache.lucene/lucene-analyzers-common "4.2.1"]
                 [org.apache.lucene/lucene-highlighter "4.2.1"]
                 [org.apache.lucene/lucene-queryparser "4.2.1"]
                 [org.apache.lucene/lucene-queries "4.2.1"]
                 [log4j/log4j "1.2.16"]
                 [junit/junit "4.8"]
                 [com.goldmansachs/gs-collections "6.1.0"]
                 [blacklab "1.2-SNAPSHOT"]]
  
  :repositories [["hackrslab-repository" "http://hackrslab.github.io/maven-repo"]]

  :main cleebo.main
  
  :jvm-opts ["-Xmx4000M"]
  
  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-environ "1.0.1"]
            [lein-asset-minifier "0.2.2"]
            [lein-figwheel "0.5.0-2"]]
  
  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :min-lein-version "2.5.0"

  :uberjar-name "cleebo-prod.jar"

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds
              {:app {:source-paths ["src/cljs" "src/cljc" "src/clj"]
                     :compiler {:main "cleebo.main"
                                :output-to "resources/public/js/compiled/app.js"
                                :output-dir "resources/public/js/compiled/out"
                                :asset-path "js/compiled/out"
                                :optimizations :none
                                :pretty-print true
                                :source-map-timestamp true}}}
              :min {:source-paths ["src/cljs" "src/cljc" "src/clj" "env/dev/cljs"]
                    :compiler {:output-to "resources/public/js/compiled/app.js"
                               :optimizations :advanced
                               :closure-defines {goog.DEBUG false}
                               :pretty-print false}}}
  
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                 :timeout 1200000})
