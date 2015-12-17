(ns cleebo.core
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [cleebo.http-server :refer [new-http-server]]
            [cleebo.handler :refer [new-handler]]
            [cleebo.system :refer [system]]
            [cleebo.db :refer [new-db]]
            [cleebo.cqp :refer [new-cqi-client]]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]))

(def config-map
  {:handler #'app
   :port (:port env)
   :database-url (:database-url env)
   :cqp-init-file (:cqp-init-file env)})

(defn create-system [config-map]
  (let [{:keys [handler port cqp-init-file database-url]} config-map]
    (component/system-map
     :http-server (component/using
                   (new-http-server {:port port})
                   [:components])
     :cqi-client (new-cqi-client {:init-file cqp-init-file})
     :db (new-db {:url database-url}))))

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
