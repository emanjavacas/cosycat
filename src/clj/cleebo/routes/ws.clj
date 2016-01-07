(ns cleebo.routes.ws
  (:require [taoensso.timbre :as timbre]
            [cleebo.utils :refer [write-str read-str]]
            [cleebo.cqp :refer [cqi-query query-range]]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as kit]))

(defrecord WS [ws-channels]
  component/Lifecycle
  (start [component]
    (if ws-channels
      component
      (assoc component :ws-channels (atom #{}))))
  (stop [component]
    (assoc component :ws-channels nil)))

(defn new-ws []
  (map->WS {}))

(declare connect! disconnect! on-ws-success send->client)

(defn ws-handler-http-kit [req]
  (let [ws (get-in req [:components :ws])
        ws-channels (:ws-channels ws)
        cqi-client (get-in req [:components :cqi-client])
        ws-router (fn [ws-from msg-data]
                    (let [parsed-msg (read-str msg-data :json)
                          {:keys [status type msg] :as data} parsed-msg]
                      (timbre/debug "Received " (str parsed-msg))
                      (try
                        (on-ws-success ws-from ws-channels data :cqi-client cqi-client)
                        (catch Exception e
                          (send->client ws-from {:msg (str e) :status :error})))))]
    (kit/with-channel req ws-ch
      (connect! ws-ch ws-channels)
      (kit/on-close ws-ch (partial disconnect! ws-ch ws-channels))
      (kit/on-receive ws-ch (partial ws-router ws-ch)))))

(defn connect! [ws-ch ws-channels]
  (timbre/info "channel open")
  (swap! ws-channels conj ws-ch))

(defn disconnect! [ws-ch ws-channels status]
  (timbre/info "channel closed: " status)
  (swap! ws-channels (partial remove #{ws-ch})))

(defn notify-clients [ws-from ws-channels data]
  (let [out-str (write-str data :json)]
    (doseq [channel @ws-channels]
      (timbre/debug (format "Sending %s from [%s] to [%s]" (str data) ws-from ws-from))
      (kit/send! channel out-str))))

(defn send->client [ws-from data]
  (let [out-str (write-str data :json)]
    (timbre/debug (format "Sending %s from [%s] to [%s]" (str data) ws-from ws-from))
    (kit/send! ws-from out-str)))

(defn on-ws-error [ws-from ws-channels {:keys [status type msg] :as data}]
  (send->client ws-from (update data :status :error)))

(defn on-ws-success [ws-from ws-channels data & {:keys [cqi-client]}]
  (let [{:keys [status type msg]} data]
    (case type
      :query (let [{:keys [query-str size]} msg
                   result (cqi-query cqi-client "PYCCLE-ECCO" query-str)]
               (send->client ws-from {:msg result
                                      :status :ok
                                      :type :query-results}))
      :next (let [{:keys [from to]} msg
                  result (query-range cqi-client "PYCCLE-ECCO" from to)]
              (send->client ws-from {:msg result
                                     :status :ok
                                     :type :query-results}))
      :msgs (notify-clients ws-from ws-channels data))))
