(ns cleebo.figwheel
  (:require [config.core :refer [env]]
            [figwheel-sidecar.repl-api :as f-repl]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]))

(def figwheel-config
  {:figwheel-options {:css-dirs ["resources/public/css"]} ;server config
   :build-ids ["dev"]
   :all-builds [{:id "dev"
                 :source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                 :figwheel {:websocket-url (str "ws://" (env :host) ":3449/figwheel-ws")
                            :on-jsload "cleebo.core/mount-root"
                            :websocket-host :js-client-host}
                 :compiler {:main "cleebo.main"
                            :asset-path "js/compiled/out"
                            :output-to "resources/public/js/compiled/app.js"
                            :output-dir "resources/public/js/compiled/out"
                            :source-map-timestamp true}}]})

(defrecord Figwheel []
  component/Lifecycle
  (start [component]
    (timbre/info "Starting figwheel component")
    (f-repl/start-figwheel! component)
    component)
  (stop [component]
    (try
      (f-repl/stop-figwheel!)
      (catch Exception e
        (timbre/info "Exception when closing figwheel" (.getMessage e)))
      (finally component))))

(defn new-figwheel []
  (map->Figwheel figwheel-config))

