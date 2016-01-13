(defproject cleebo "0.1.0-SNAPSHOT"
  :description "Corpus query interface plus annotations"
  :license {:name "GNU v3.0"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374"]
                 [com.novemberain/monger "3.0.0-rc2"]
                 [com.taoensso/timbre "4.1.4"] 
                 [prone "0.8.2"]
                 [environ "1.0.1"]
                 [selmer "0.9.3"]
                 [hiccup "1.0.5"]
                 [reagent "0.5.1"]
                 [re-frame "0.5.0"]
                 [re-com "0.7.0"]
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
                 [cqp-clj "0.1.0-SNAPSHOT"]]

  :main cleebo.core
  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-environ "1.0.1"]
            [lein-figwheel "0.5.0-2"]]
  :source-paths ["src/clj"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target" "test/js"]
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs"]                
                :compiler {:main cleebo.core
                           :output-to "resources/public/js/compiled/app.js"
                           :output-dir "resources/public/js/compiled/out"
                           :asset-path "js/compiled/out"
                           :source-map-timestamp true}}
               {:id "test"
                :source-paths ["src/cljs" "test/cljs"]
                :notify-command ["phantomjs" "test/unit-test.js" "test/unit-test.html"]
                :compiler {:optimizations :whitespace
                           :pretty-print true
                           :output-to "test/js/app_test.js"
                           :warnings {:single-segment-namespace false}}}
               {:id "min"
                :source-paths ["src/cljs"]
                :compiler {:main cleebo.core
                           :output-to "resources/public/js/compiled/app.js"
                           :optimizations :advanced
                           :closure-defines {goog.DEBUG false}
                           :pretty-print false}}]}
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                 :timeout 120000})


