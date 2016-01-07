(ns cleebo.routes.ws
  (:require [taoensso.timbre :as timbre]
            [cleebo.utils :refer [write-str read-str]]
            [com.stuartsierra.component :as [component]]
            [org.httpkit.server :as kit]))

(defrecord WS [channels]
  component/Lifecycle
  (start [component]
    (if channels
      component
      (assoc component :channels (atom #{}))))
  (stop [component]
    (assoc component :channels nil)))

(defn new-ws []
  (map->WS {}))

;;; ws
;; (defonce channels (atom #{}))
(declare connect! disconnect! ws-router)

(defn ws-handler-http-kit [req]
  (let [ws (get-in req [:components :ws])]
    (kit/with-channel req ws-ch
      (connect! ws ws-ch)
      (kit/on-close ws-ch (partial disconnect! ws ws-ch))
      (kit/on-receive ws-ch (partial ws-router ws)))))

(defn connect! [ws ws-ch]
  (let [channels (:channels ws)]
    (timbre/info "channel open")
    (swap! channels conj ws-ch)))

(defn disconnect! [ws ws-ch status]
  (let [channels (:channels ws)]
    (timbre/info "channel closed: " status)
    (swap! channels (partial remove #{ws-ch}))))

(defn notify-clients [channels msg]
  (doseq [channel @channels]
    (timbre/debug (str "Sending " msg " to channel: " channel))
    (kit/send! channel msg)))

(defn on-ws-error [data & [channels]]
  ()
  (notify-clients))

(defn on-ws-success [{:keys [status type msg] :as data}]
  (notify-clients channels (write-str data :json)))

(defn ws-router [ws msg-data]
  (let [channels (:channels ws)
        parsed-msg (read-str msg-data :json)
        {:keys [status type msg] :as data} parsed-msg]
    (case status
      :ok (on-ws-success channels data)
      :error (on-ws-error data))))
