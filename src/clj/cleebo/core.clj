(ns cleebo.core
  (:require [com.stuartsierra.component :as component]
            [cleebo.handler :refer [app init destroy]]
            [org.httpkit.server :as http-kit]
            [cqp-clj.core :refer [make-cqi-client connect! disconnect!]]
            [cqp-clj.spec :refer [read-init]]))

(defn parse-port [port]
  (when port
    (cond
      (string? port) (Integer/parseInt port)
      (number? port) port
      :else (throw (Exception. (str "Invalid port value: " port))))))

(defn- start-http-server [app port]
  (let [server (http-kit/run-server app {:port port})]
    (println (str "Started server on port: " port))
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
                               :port 3000
                               :init-file "dev-resources/cqpserver.init"})]
    (.addShutdownHook 
     (Runtime/getRuntime) 
     (Thread. (fn [] (.stop system))))
    (.start system)))

(defn -main [& args]
  (run))
