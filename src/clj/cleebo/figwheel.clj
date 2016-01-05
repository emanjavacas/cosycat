(ns cleebo.figwheel
  (:require [figwheel-sidecar.repl-api :as f-repl]
            [com.stuartsierra.component :as component]))

(def figwheel-config
   {:figwheel-options ;{}
    {:css-dirs ["resources/public/css"]
     ;; :ring-handler cleebo.handler/web-app
     :http-server-root "public"
     :server-ip "146.175.15.30"
     :server-port 3449
     :nrepl-port 7888
     :repl false}    
    :build-ids ["dev"]   ;; <-- a vector of build ids to start autobuilding
    :all-builds          ;; <-- supply your build configs here
    [{:id "dev"
      :figwheel {:on-jsload "cleebo.core/mount-root"
                 :websocket-host :js-client-host}
      :source-paths ["src/cljs"]
      :compiler
      {:main "cleebo.core"
       :output-to "resources/public/js/compiled/app.js"
       :output-dir "resources/public/js/compiled/out"
       :asset-path "js/compiled/out"
       :source-map-timestamp true}}]})

(defrecord Figwheel []
  component/Lifecycle
  (start [component]
    (f-repl/start-figwheel! component)
    (f-repl/cljs-repl)
    component)
  (stop [component]
    (f-repl/stop-figwheel!)
    component))

(defn new-figwheel []
  (map->Figwheel figwheel-config))

