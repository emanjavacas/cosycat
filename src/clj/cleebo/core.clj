(ns cleebo.core
  (:require [com.stuartsierra.component :as component]
            [cleebo.handler :refer [app init destroy start-router!]]
            [org.httpkit.server :as http-kit]
            [clojure.tools.nrepl.server :as nrepl]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [cqp-clj.core :refer [make-cqi-client connect! disconnect!]]
            [cqp-clj.spec :refer [read-init]]))

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
  (let [server (http-kit/run-server app {:port port})]
    (timbre/info (str "Started server on port: " port))
    server))

(defn- stop-http-server [server]
  (when server
    (server :timeout 100)))

(defrecord Server [handler port]
  component/Lifecycle
  (start [component]
    (init)
    (assoc component :server (start-http-server handler port)))
  (stop [component]
    (destroy)
    (stop-http-server (:server component))
    (dissoc component :server)))

(defrecord CQiComponent [client init-file]
  component/Lifecycle
  (start [component]
    (let [client (:client (make-cqi-client (read-init init-file)))]
      (assoc component :client client)))
  (stop [component]
    (disconnect! component)
    (assoc component :client nil)))

(defn create-system [config-map]
  (let [{:keys [handler port init-file]} config-map]
    (component/system-map
     :web (map->Server {:handler handler
                        :port port})
     :cqi-client (map->CQiComponent {:init-file init-file}))))

(defn run []
  (let [system (create-system {:handler #'app
                               :port (or (env :port) 3000)
                               :init-file "dev-resources/cqpserver.init"})]
    (.addShutdownHook 
     (Runtime/getRuntime) 
     (Thread. (fn []
                (stop-nrepl)
                (.stop system))))
    (try
      (.start system)
      (catch Throwable t
        (.stop system)))))

(defn -main [& args]
  (start-nrepl)
  (start-router!)
  (run))

(defn main []
  (start-nrepl)
  (run))
