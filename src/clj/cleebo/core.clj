(ns cleebo.core
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [cleebo.http-server :refer [new-http-server]]
            [cleebo.db :refer [new-db]]
            [cleebo.cqp :refer [new-cqi-client]]
            [cleebo.figwheel :refer [new-figwheel]]
            [cleebo.routes.ws :refer [new-ws]]
            [clojure.pprint :as pprint]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]))

(def config-map
  {:port (:port env)
   :database-url (:database-url env)
   :cqp-init-file (:cqp-init-file env)})

(defn create-system [config-map]
  (let [{:keys [handler port cqp-init-file database-url]} config-map]
    (-> (component/system-map
         :cqi-client (new-cqi-client {:init-file cqp-init-file})
         :db (new-db {:url database-url})
         :ws (new-ws)
         :http-server (new-http-server {:port port :components [:cqi-client :db :ws]})
         :figwheel (new-figwheel))
        (component/system-using
         {:http-server [:cqi-client :db :ws]}))))

(defonce system nil)

(defn init []
  (println "\n\nStarting server with enviroment:")
  (pprint/pprint (select-keys env [:host :database-url :cqp-init-file]))
  (println "\n")  
  (alter-var-root #'system (constantly (create-system config-map))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system component/stop))

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
                (.stop system))))
    (.start system)))
