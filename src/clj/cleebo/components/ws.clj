(ns cleebo.components.ws
  (:require [taoensso.timbre :as timbre]
            [schema.core :as s]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as kit]
            [clojure.core.async :refer
             [chan go >! <! >!! <!! alts! put! take! timeout close! go-loop]]
            [clojure.core.match :refer [match]]
            [cleebo.schemas.route-schemas :refer [ws-from-server ws-from-client]]
            [cleebo.db.users :refer [user-logout]]
            [cleebo.utils :refer [write-str read-str ->int]]))

(def messages
  {:shutting-down {:type :info :data {:message "Server is going to sleep!" :by "server"}}
   :goodbye       {:type :info :data {:message "Goodbye world!"}}
   :hello         {:type :info :data {:message "Hello world!"}}})

(declare send-clients)

(defrecord WS [clients db router]
  component/Lifecycle
  (start [component]
    (timbre/info "Starting WS component")
    (if clients
      component
      (assoc component :clients (atom {}) :router router)))
  (stop [component]
    (timbre/info "Shutting down WS component")
    (if-not clients
      component
      (do (send-clients component (messages :shutting-down))
          (timbre/info "Waiting 2 seconds to notify clients" @clients)
          (Thread/sleep 2000)
          (assoc component :clients nil)))))

(defn ping-router [ws {:keys [ws-from payload]}]
  (send ws ws-from payload))

(defn get-active-users [{clients :clients :as ws}]
  (apply hash-set (keys @clients)))

(defn new-ws [& {:keys [router] :or {router ping-router}}]
  (map->WS {:router router}))

(defn connect-client [ws ws-ch ws-name]
  (let [{clients :clients} ws]
    (timbre/info ws-name "opened ws-channel connection")
    (swap! clients assoc ws-name ws-ch)))

(defn disconnect-client [ws ws-name status]
  (let [{clients :clients db :db} ws]
    (timbre/info ws-name "closed ws-channel connection with status: " status)
    (user-logout db ws-name)
    (swap! clients dissoc ws-name)))

(defn ws-handler-http-kit 
  [{{{clients :clients router :router :as ws} :ws} :components
    {{username :username} :identity} :session :as req}]
  (kit/with-channel req ws-ch
    (connect-client ws ws-ch username)
    (kit/on-close ws-ch (fn [status] (disconnect-client ws username status)))
    (kit/on-receive ws-ch
                    (fn [payload]
                      (let [parsed-payload (read-str payload :json)]
                        (s/validate (ws-from-client parsed-payload) parsed-payload)
                        (router ws {:ws-from username :payload parsed-payload}))))))

(defn send-client [{clients :clients :as ws} ws-target payload]
  (let [str-payload (write-str payload :json)
        ws-target-ch (get @clients ws-target)]
    (s/validate (ws-from-server payload) payload)
    (timbre/info "sending" payload "to" ws-target "at" ws-target-ch)
    (kit/send! ws-target-ch str-payload)))

(defn send-clients
  "function wrapper over multiplexed puts to out-chan"
  [{clients :clients :as ws} payload &
   {:keys [source-client target-clients] :or {source-client "server"}}]
  (doseq [[target-client _] (seq @clients)
          :when (and (not (= source-client target-client))
                     (if-not target-clients true (some #{target-client} target-clients)))]
    (send-client ws target-client payload)))
