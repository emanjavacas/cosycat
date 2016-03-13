(ns cleebo.backend.ws
  (:require [cognitect.transit :as t]
            [re-frame.core :as re-frame]
            [cljs.core.async :refer [chan <! >! put! take! close!]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce app-channels (atom {:ws-in nil :ws-out nil}))

(defn host-url []
  (str "ws://" (.-host js/location) "/ws"))

;;; TODO: automatic reconnect in case of WS-error
(defn make-ws-channels! [& {:keys [url] :or {url (host-url)}}]
  (let [json-reader (t/reader :json-verbose)
        json-writer (t/writer :json-verbose)
        ws-in (chan) ws-out (chan)]
    (if-let [ws-chan (js/WebSocket. url)]
      (do
        (set! (.-onopen ws-chan) #(timbre/info "Opened WS connection"))
        (set! (.-onclose ws-chan) #(map close! [ws-in ws-out]))
        (set! (.-onerror ws-chan) #(timbre/info "WS error"))
        (set! (.-onmessage ws-chan)
              (fn [payload]
                (timbre/info "got" payload)
                (->> payload
                     .-data
                     (t/read json-reader)
                     (put! ws-in))))
        (go (loop []
              (let [[payload sc] (alts! [ws-in ws-out])]
                (condp = sc
                  ws-in  (re-frame/dispatch [:ws :in payload])
                  ws-out (->> payload
                              (t/write json-writer)
                              (.send ws-chan))))
              (recur)))
        (swap! app-channels assoc :ws-in ws-in :ws-out ws-out))
      (throw (js/Error. "Websocket is not available!")))))

(defn send-ws [payload]
  (if-let [ws-out (:ws-out @app-channels)]
    (put! ws-out payload)
    (js/Error. "Websocket is not available!")))
