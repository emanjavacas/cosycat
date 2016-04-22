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
  {:shutting-down {:status :info
                   :type :notify
                   :data {:message "Server is going to sleep!"
                          :by "Server"}}
   :goodbye       {:status :info
                   :type :notify
                   :data {:message "Goodbye world!"}}
   :hello         {:status :info
                   :type :notify
                   :data {:message "Hello world!"}}})

(declare notify-client notify-clients ws-routes)

(defn close-chans [ws]
  (let [{:keys [chans]} ws]
    (dorun (map close! (vals chans)))))

(defrecord WS [clients chans db ws-route-map]
  component/Lifecycle
  (start [component]
    (timbre/info "Starting WS component")
    (if (and clients chans)
      component
      (let [chans {:ws-in (chan) :ws-out (chan)}
            component (assoc component :clients (atom {}) :chans chans)]
        (ws-routes component ws-route-map)
        component)))
  (stop [component]
    (timbre/info "Shutting down WS component")
    (if (not (and clients chans))
      component
      (do (notify-clients component (messages :shutting-down))
          (timbre/info "Waiting 5 seconds to notify clients")
          (Thread/sleep 50)
          (close-chans component)
          (assoc component :clients nil :chans nil)))))

(defn new-ws [ws-route-map]
  (map->WS {:ws-route-map ws-route-map}))

(defn connect-client [ws ws-ch ws-name]
  (let [{{ws-out :ws-out} :chans clients :clients} ws
        payload (update-in (messages :hello) [:data] assoc :by ws-name)]
    (timbre/info ws-name "opened ws-channel connection")
    (swap! clients assoc ws-name ws-ch)))

(defn disconnect-client [ws ws-name status]
  (let [{{ws-out :ws-out} :chans clients :clients db :db} ws
        payload (update-in (messages :goodbye) [:data] assoc :by ws-name)]
    (timbre/info ws-name "closed ws-channel connection with status: " status)
    (user-logout db ws-name)
    (swap! clients dissoc ws-name)))

(defn ws-handler-http-kit [req]
  (let [{{ws :ws} :components
         {{username :username} :identity} :session} req
        {{ws-in :ws-in ws-out :ws-out} :chans clients :clients} ws]
    (kit/with-channel req ws-ch
      (connect-client ws ws-ch username)
      (go-loop []             ;this code must never throw an exception
        (if-let [p (<! ws-out)]         ;if falsy, the channel closes
          (let [{:keys [ws-target ws-from payload]} p
                payload (assoc payload :source ws-from)
                ws-target-ch (get @clients ws-target)]
            (timbre/info "sending" payload "to" ws-target "at" ws-target-ch)
            (kit/send! ws-target-ch (write-str payload :json))
            (recur))))
      (kit/on-close ws-ch (fn [status] (disconnect-client ws username status)))
      (kit/on-receive
       ws-ch
       (fn [payload]
         (let [parsed-payload (read-str payload :json)]
           (put! ws-in {:ws-from username :payload parsed-payload})))))))

(defn out-chan [c payload payload-id]
  (if (map? payload)
    (let [{p :payload :as payload} (assoc-in payload [:payload :payload-id] payload-id)]
      (s/validate (ws-from-server p) p)
      (put! c payload))
    (doseq [p payload
            :let [p (assoc-in p [:payload :payload-id] payload-id)]]
      (s/validate (ws-from-server (:payload p)) (:payload p))
      (put! c p))))

(defn ws-routes [ws routes]
  (let [{{ws-in :ws-in ws-out :ws-out} :chans} ws]
    (go-loop []                         ;exceptions should be handled within each route
      (if-let [{{:keys [type payload-id] :as client-payload} :payload :as payload} (<! ws-in)]
        (do (timbre/debug "CLIENT-PAYLOAD" client-payload)
            (s/validate (ws-from-client client-payload) client-payload)
            (let [route (get routes type)
                  client-load (assoc payload :payload (dissoc client-payload :payload-id))
                  {:keys [ws-from ws-target payload] :as server-payload} (route ws payload)]
              (timbre/debug "SERVER-PAYLOAD" server-payload)      
              (out-chan ws-out server-payload payload-id))))
      (recur))))

(defn notify-client
  "function wrapper over puts to out-chan"
  [ws ws-target payload & {:keys [ws-from] :or {ws-from "server"}}]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (put! ws-out {:ws-target ws-target :ws-from ws-from :payload payload})))

(defn notify-clients
  "function wrapper over multiplexed puts to out-chan"
  [ws payload & {:keys [ws-from target-clients] :or {ws-from "server"}}]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (doseq [[client _] (seq @clients)
            :when (or (and target-clients (some #{client} target-clients))
                      (not= ws-from client))]
      (put! ws-out {:ws-target client :ws-from ws-from :payload payload}))))
