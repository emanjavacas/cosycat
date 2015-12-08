(defproject cleebo "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374"]
                 [reagent "0.5.1"]
                 [re-frame "0.5.0"]
                 [re-com "0.7.0"]
                 [secretary "1.2.3"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [ring "1.4.0" :exclusions [ring/ring-jetty-adapter]]
                 [compojure "1.4.0"]
                 [jarohen/chord "0.6.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [http-kit "2.1.18"]]

  :main cleebo.core
  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.4.1" :exclusions [cider/cider-nrepl]] ]
  :source-paths ["src/clj"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target" "test/js"]
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs"]
                :figwheel {:on-jsload cleebo.core/mount-root}
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
  :figwheel {:css-dirs ["resources/public/css"]
             :ring-handler cleebo.handler/app
             :http-server-root "public"
             :server-port 3449
             :nrepl-port 7888})
