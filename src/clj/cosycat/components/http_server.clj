(ns cosycat.components.http-server
  (:require [cosycat.handler :refer [make-handler]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [org.httpkit.server :as kit]))

(defn start-http-server [app port]
  (kit/run-server app {:port port}))

(defn stop-http-server [server]
  (let [timeout 10]
    (when server
      (do (timbre/debug "Closing connection to server in " timeout)
          (server :timeout 10)))))

(defrecord HttpServer [port debug http-server components]
  component/Lifecycle
  (start [component]
    (timbre/info "Starting web server in port " port)
    (if http-server
      component
      (assoc component :http-server (start-http-server (make-handler component :debug debug) port))))
  (stop [component]
    (timbre/info "Shutting down web server")
    (let [the-server (:http-server component)]
      (if (not the-server)
        component
        (assoc component :http-server (stop-http-server the-server))))))

(defn new-http-server [{:keys [port components debug]}]
  (map->HttpServer {:port port :debug debug :components components}))

