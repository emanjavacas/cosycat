(ns cleebo.backend.ws-routes.router)

;; (defonce ws-ch (atom nil))
;; (defn receive-transit-msg! [wrap-msg]
;;   (let [json-reader (t/reader :json-verbose)]
;;     (fn [msg]
;;       (if @ws-ch
;;         (wrap-msg
;;          (->> msg .-data (t/read json-reader)))))))

;; (defn send-transit-msg! [msg]
;;   (let [json-writer (t/writer :json-verbose)]
;;     (if @ws-ch
;;       (let [json-msg (t/write json-writer msg)]
;;         (.send @ws-ch json-msg))
;;       (throw (js/Error. "Websocket is not available!")))))

;; (defn make-ws-ch [url wrap-msg]
;;   (timbre/info "Attempting connection to " url)
;;   (if-let [c (js/WebSocket. url)]
;;     (do
;;       (set! (.-onmessage c) (receive-transit-msg! wrap-msg))
;;       (reset! ws-ch c)
;;       (timbre/info "Connected to " url))
;;     (throw (js/Error. "Websocket connection failed!"))))

;; (defn set-ws-ch []
;;   (make-ws-ch
;;    (str "ws://" (.-host js/location) "/ws")
;;    #(re-frame/dispatch [:receive-ws %])))

;; (defn on-ws-success
;;   [db {:keys [type data] :as payload}]
;;   (case type
;;     :annotation (annotation-route data))
;;   db)

;; (defn on-ws-error
;;   [db {:keys [type data]}]
;;   (re-frame/dispatch
;;    [:notify
;;     {:msg (case type
;;             :annotation "Oops! We couldn't store your annotation"
;;             "Oops there was a dwarf in the channel")
;;      :status :error}])
;;   db)

;; (re-frame/register-handler
;;  :sent-ws
;;  standard-middleware
;;  (fn [db [_ {:keys [type data] :as payload}]]
;;    (send-transit-msg! payload)
;;    db))

;; (re-frame/register-handler
;;  :receive-ws
;;  (fn [db [_ {:keys [status type data] :as payload}]]
;;    (cond
;;      (= status :error) (on-ws-error   db {:type type :data data})
;;      (= status :ok)    (on-ws-success db {:type type :data data})
;;      :else             (throw (js/Error. (str "Unknown status: " status))))))
