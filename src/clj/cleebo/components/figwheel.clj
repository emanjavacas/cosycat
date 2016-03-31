(ns cleebo.components.figwheel
  (:require [environ.core :refer [env]]
            [figwheel-sidecar.repl-api :as f-repl]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]))

(def host (get env :host))

(def figwheel-config
   {:figwheel-options {:css-dirs ["resources/public/css"]} ;server config
    :build-ids ["dev"]   ;; <-- a vector of build ids to start autobuilding
    :all-builds          ;; <-- supply your build configs here
    [{:id "dev"
      :source-paths ["src/cljs" "src/cljc"]
      :figwheel {:websocket-url (str "ws://" host ":3449/figwheel-ws") ;client config
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

