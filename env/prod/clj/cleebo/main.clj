(ns cleebo.main
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [cleebo.components.http-server :refer [new-http-server]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.components.blacklab :refer [new-bl]]
            [cleebo.components.ws :refer [new-ws]]
            [cleebo.routes.annotations :refer [annotation-route]]
            [cleebo.routes.notifications :refer [notify-route]]
            [environ.core :refer [env]])
  (:gen-class))

(set! *warn-on-reflection* true)

;;; production system
(def prod-config-map
  {:port (env :port)
   :database-url (env :database-url)
   :cqp-init-file (env :cqp-init-file)
   :blacklab-paths-map (env :blacklab-paths-map)})

(defn create-prod-system [config-map]
  (let [{:keys [handler port database-url blacklab-paths-map]} config-map]
    (-> (component/system-map
         :blacklab (new-bl blacklab-paths-map)
         :db (new-db {:url database-url})
         :ws (new-ws {:annotation annotation-route :notify notify-route})
         :http-server (new-http-server {:port port :components [:db :ws :blacklab]}))
        (component/system-using
         {:http-server [:db :ws :blacklab]
          :blacklab    [:ws]
          :ws          [:db]}))))

(defn -main [& args]
  (let [system (create-prod-system prod-config-map)]
    (.addShutdownHook 
     (Runtime/getRuntime) 
     (Thread. (fn [] (.stop system))))
    (.start system)))
