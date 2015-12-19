(ns cleebo.core
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.tools.nrepl.server :refer [start-server stop-server]]
            [cleebo.http-server :refer [new-http-server]]
            [cleebo.system :refer [system]]
            [cleebo.db :refer [new-db]]
            [cleebo.cqp :refer [new-cqi-client]]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]))

(def config-map
  {:port (:port env)
   :database-url (:database-url env)
   :cqp-init-file (:cqp-init-file env)})

(defonce nrepl-server (atom nil))

(defn parse-port [port]
  (when port
    (cond
      (string? port) (Integer/parseInt port)
      (number? port) port
      :else (throw (Exception. (str "Invalid port value: " port))))))

(defn stop-nrepl []
  (when-let [server @nrepl-server]
    (stop-server server)))

(defn start-nrepl []
  (if @nrepl-server
    (timbre/error "nREPL is already running!")
    (when-let [port (env :nrepl-port)]
      (try
        (->> port
             parse-port
             (start-server :port)
             (reset! nrepl-server))
        (timbre/info "nREPL server started on port" port)
        (catch Throwable t
          (timbre/error t "failed to start nREPL"))))))

(defn create-system [config-map]
  (let [{:keys [handler port cqp-init-file database-url]} config-map]
    (-> (component/system-map
         :cqi-client (new-cqi-client {:init-file cqp-init-file})
         :db (new-db {:url database-url})
         :http-server (new-http-server {:port port :components [:cqi-client :db]}))
        (component/system-using
         {:http-server [:cqi-client :db]}))))

(defn init []
  (alter-var-root #'system (constantly (create-system config-map))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system #(when % component/stop)))

(defn run []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'cleebo.core/run))

(defn -main [& args]
  (let [system (create-system config-map)]
    (.addShutdownHook 
     (Runtime/getRuntime) 
     (Thread. (fn []
                (stop-nrepl)
                (.stop system))))
    (.start system)))
