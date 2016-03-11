(ns cleebo.components.ws
  (:require [taoensso.timbre :as timbre]
            [schema.core :as s]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as kit]
            [clojure.core.async :refer
             [chan go >! <! >!! <!! alts! put! take! timeout close!]]
            [cleebo.shared-schemas :refer [ws-from-server]]
            [cleebo.utils :refer [write-str read-str]]
            [cleebo.components.db.annotations :refer [new-token-annotation]]))

(defn messages [k & args]
  (let [msgs {:shutting-down {:status :ok
                              :type :notify
                              :data {:message "Server is going to sleep!"
                                     :by "Server"}}
              :goodbye       {:status :info
                              :type :notify
                              :data {:message "Goodbye world!"
                                     :by ""}}
              :hello         {:status :info
                              :type :notify
                              :data {:message "Hello world!"
                                     :by ""}}}]
    (apply update-in (get k msgs) args)))

(defrecord WS [clients chans db]
  component/Lifecycle
  (start [component]
    (if (and clients chans)
      component
      (assoc
       component
       :clients (atom {})
       :chans {:ws-in (chan) :ws-out (chan)})))
  (stop [component]
    (notify-clients component (messages :shutting-down))
    (timbre/info "Waiting 5 seconds to notify clients")
    (<!! (timeout 5000))
    (dorun (map #close! (values chans)))
    (assoc component :clients nil :chans nil)))

(defn notify-client
  [ws ws-target payload & {:keys [ws-from] :or {ws-from "Server"}}]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (put! ws-out [ws-target ws-from payload])))

(defn notify-clients
  [ws payload & {:keys [ws-from] :or {ws-from "Server"}}]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (doseq [[ws-name _] (seq @clients)
            :when (not= ws-from ws-name)]
      (put! ws-out [ws-target ws-from payload]))))

(defn connect-client [ws ws-ch ws-name]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (timbre/info ws-name "opened ws-channel connection")
    (notify-clients ws (messages :hello [:data] #(assoc % :by ws-name)))
    (swap! clients assoc ws-name ws-ch)))

(defn disconnect-client [ws ws-name status]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (timbre/info ws-name "closed ws-channel connection with status: " status)
    (notify-clients ws (message :goodbye [:data] #(assoc % :by ws-name)))
    (swap! clients dissoc ws-name)))

(defn ws-handler-http-kit [req]
  (let [{{ws :ws} :components
         {{username :username} :identity} :session} req
        {{ws-in :ws-in ws-out :ws-out} :chans} ws]
    (kit/with-channel req ws-ch
      (connect-client ws ws-ch username)
      (go (loop []
            (when-let [payload (<! ws-out)]
              (let [[ws-target ws-from payload] payload
                    ws-target-ch (get @clients ws-target)]
                (kit/send! ws-target payload)
                (recur)))))
      (kit/on-close ws-ch (fn [status] (disconnect-client ws ws-name status)))
      (kit/on-receive
       ws-ch
       (fn [payload]
         (put! ws-in payload))))))
