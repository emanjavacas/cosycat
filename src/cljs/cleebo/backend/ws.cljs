(ns cleebo.backend.ws
  (:require [cognitect.transit :as t]
            [cljs.core.async :refer [chan <! >! put! take! close!]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn host-url []
  (str "ws://" (.-host js/location) "/ws"))

(defn make-ws-channels! [handler & {:keys [url] :or {url (host-url)}}]
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
                (->> payload
                     .-data
                     (t/read json-reader)
                     (put! ws-in))))
        (let [[ws-in ws-out] (handler ws-in ws-out)]
          (go (loop []
                (let [[_ payload] (<! ws-out)]
                  (->> payload
                       (t/write json-writer)
                       (.send ws-chan)))
                (recur)))))
      (throw (js/Error. "Websocket is not available!")))))
