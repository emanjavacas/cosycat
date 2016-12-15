(defproject cosycat "0.1.5-alpha"
  :description "Corpus query interface plus annotations"
  :license {:name "GNU v3.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.cli "0.3.3"]
                 [com.novemberain/monger "3.0.0-rc2"]
                 [org.slf4j/slf4j-nop "1.7.12"] ;deactivate MongoDB logging
                 [com.taoensso/timbre "4.1.4"]
                 [binaryage/devtools "0.5.2"]
                 [prismatic/schema "1.1.0"]
                 [prismatic/schema-generators "0.1.0"]
                 [prone "0.8.2"]
                 [yogthos/config "0.8"]
                 [hiccup "1.0.5"]
                 [reagent "0.6.0-alpha" :exclusions [cljsjs/react]]
                 [cljsjs/react-bootstrap "0.28.1-1"
                  :exclusions [org.webjars.bower/jquery cljsjs/react]]
                 [cljsjs/react-with-addons "0.14.3-0"]
                 [cljsjs/react-autosuggest "3.5.1-0"]
                 [cljsjs/react-date-range "0.2.4-0"
                  :exclusions [cljsjs/react-with-addons cljsjs/react]]
                 [re-frame "0.7.0-alpha"]
                 [secretary "1.2.3"]
                 [ring "1.4.0" :exclusions [ring/ring-jetty-adapter]]     
                 [http-kit "2.1.18"]
                 [compojure "1.4.0"]
                 [cljs-ajax "0.5.4"]
                 [keybind "2.0.0"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [ring-ttl-session "0.3.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [ring-transit "0.1.4"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [buddy/buddy-auth "0.8.1"]
                 [buddy/buddy-hashers "0.9.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [gravatar "1.1.0"]
                 [clj-http "2.3.0"]
                 [org.hackrslab/random-avatar "0.2.3"]]
  
  :repositories [["hackrslab-repository" "http://hackrslab.github.io/maven-repo"]]

  :main cosycat.main
  
  :jvm-opts ["-Xmx4000M" "-Djava.awt.headless=true" "-XX:-OmitStackTraceInFastThrow"]
  
  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-environ "1.0.1"]
            [lein-asset-minifier "0.2.2"]
            [lein-figwheel "0.5.0-2"]]

  :env {:dynamic-resource-path "app-resources/"
        :avatar-path "img/avatars/"}
  
  :resource-path "resources/"
  
  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :min-lein-version "2.5.0"

  :profiles {:uberjar
             {:source-paths ["env/prod/clj"]
              :hooks [leiningen.cljsbuild]
              :prep-tasks ["compile" ["cljsbuild" "once"]]
              :omit-source true
              :aot :all
              :cljsbuild {:jar true
                          :builds {:app {:source-paths ["env/prod/cljs"]
                                         :compiler {:optimizations :simple
                                                    :closure-defines {goog.DEBUG false}
                                                    :pretty-print true}}}}}}

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds
              {:app {:source-paths ["src/cljs" "src/cljc" "src/clj"]
                     :compiler {:main "cosycat.main"
                                :output-to "resources/public/js/compiled/app.js"
                                :output-dir "resources/public/js/compiled/out"
                                :asset-path "js/compiled/out"
                                :optimizations :none
                                :pretty-print true
                                :source-map-timestamp true}}}
              :min {:source-paths ["src/cljs" "src/cljc" "src/clj"]
                    :compiler {:output-to "resources/public/js/compiled/app.js"
                               :optimizations :advanced
                               :closure-defines {goog.DEBUG false}
                               :pretty-print false}}}
  
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                 :timeout 1200000})
