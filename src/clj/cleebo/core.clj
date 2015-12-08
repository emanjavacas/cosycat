(ns cleebo.core
  (:require [com.stuartsierra.component :as component]
            [siberet.handler :refer [app init destroy]]
            [org.httpkit.server :as http-kit]))

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

(defrecord Siberet []
  component/Lifecycle
  (start [this]
    (init)
    (assoc this :server (start-http-server #'app 3000)))
  (stop [this]
    (destroy)
    (stop-http-server (:server this))
    (dissoc this :server)))

(defn create-system []
  (Siberet.))

(defn -main [& args]
  (let [system (create-system)]
    (.addShutdownHook 
     (Runtime/getRuntime) 
     (Thread. (fn [] (.stop system))))
    (.start system)))

