(ns cleebo.figwheel
  (:require [figwheel-sidecar.repl-api :as f-repl]
            [com.stuartsierra.component :as component]))

(def figwheel-config
   {:figwheel-options {:css-dirs ["resources/public/css"]} ;server config
    :build-ids ["dev"]   ;; <-- a vector of build ids to start autobuilding
    :all-builds          ;; <-- supply your build configs here
    [{:id "dev"
      :source-paths ["src/cljs"]
      :figwheel {:websocket-url "ws://146.175.15.30:3449/figwheel-ws" ;client config
                 :on-jsload "cleebo.core/mount-root"
                 :websocket-host :js-client-host}
      :compiler {:main "cleebo.core"
                 :asset-path "js/compiled/out"
                 :output-to "resources/public/js/compiled/app.js"
                 :output-dir "resources/public/js/compiled/out"
                 :source-map-timestamp true}}]})

(defrecord Figwheel []
  component/Lifecycle
  (start [component]
    (f-repl/start-figwheel! component)
;    (f-repl/cljs-repl)
    component)
  (stop [component]
    (f-repl/stop-figwheel!)
    component))

(defn new-figwheel []
  (map->Figwheel figwheel-config))

