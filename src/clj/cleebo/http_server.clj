(ns cleebo.http-server
  (:require [clojure.tools.nrepl.server :as nrepl]
            [cleebo.handler :refer [make-handler]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [org.httpkit.server :as kit]
            [environ.core :refer [env]]))

(defonce nrepl-server (atom nil))
(defn parse-port [port]
  (when port
    (cond
      (string? port) (Integer/parseInt port)
      (number? port) port
      :else (throw (Exception. (str "Invalid port value: " port))))))

(defn stop-nrepl []
  (when-let [server @nrepl-server]
    (nrepl/stop-server server)))

(defn start-nrepl []
  (if @nrepl-server
    (timbre/error "nREPL is already running!")
    (when-let [port (env :nrepl-port)]
      (try
        (->> port
             parse-port
             (nrepl/start-server :port)
             (reset! nrepl-server))
        (timbre/info "nREPL server started on port" port)
        (catch Throwable t
          (timbre/error t "failed to start nREPL"))))))

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
       (start-http-server (make-handler components) port))))
  (stop [component]
    (let [the-server (:http-server component)]
      (if (not the-server)
        component
        (assoc
         component :http-server
         (stop-http-server the-server))))))

(defn new-http-server [{:keys [port components]}]
  (map->HttpServer {:port port :components components}))
