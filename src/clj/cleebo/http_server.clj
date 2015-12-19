(ns cleebo.http-server
  (:require [cleebo.handler :refer [make-handler]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [org.httpkit.server :as kit]))

(defn- start-http-server [app port]
  (let [server (kit/run-server app {:port port})]
    (timbre/info (str "Started server on port: " port))
    server))

(defn- stop-http-server [server]
  (when server
    (server :timeout 100)))

(defrecord HttpServer [port http-server components]
  component/Lifecycle
  (start [component]
    (if http-server
      component
      (assoc
       component :http-server
       (start-http-server (make-handler component) port))))
  (stop [component]
    (let [the-server (:http-server component)]
      (if (not the-server)
        component
        (assoc
         component :http-server
         (stop-http-server the-server))))))

(defn new-http-server [{:keys [port components]}]
  (map->HttpServer {:port port :components components}))

