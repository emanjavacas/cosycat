(ns cleebo.main
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [cleebo.figwheel :refer [new-figwheel]]
            [cleebo.components.http-server :refer [new-http-server]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.components.blacklab :refer [new-bl]]
            [cleebo.components.ws :refer [new-ws]]
            [environ.core :refer [env]]))

(defonce system nil)

(def dev-config-map
  {:port (env :port)
   :database-url (env :database-url)
   :blacklab-paths-map (env :blacklab-paths-map)})

(defn create-dev-system [config-map]
  (let [{:keys [handler port cqp-init-file database-url blacklab-paths-map]} config-map]
    (-> (component/system-map
         :blacklab (new-bl blacklab-paths-map)
         :db (new-db {:url database-url})
         :ws (new-ws)
         :figwheel (new-figwheel)
         :http-server (new-http-server {:port port :components [:db :ws :blacklab]}))
        (component/system-using
         {:http-server [:db :ws :blacklab]
          :blacklab    [:ws]
          :ws          [:db]}))))

(defn init []
  (alter-var-root #'system (constantly (create-dev-system dev-config-map))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system component/stop))

(defn run []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'cleebo.main/run))

