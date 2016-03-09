(ns cleebo.backend.annotation-handlers
  (:require [re-frame.core :as re-frame]
            [cljs.core.async :refer [put! alts! chan]]
            [cleebo.shared-schemas :refer [annotation-schema]]
            [cleebo.utils :refer [update-token]]
            [cleebo.backend.middleware :refer [standard-middleware]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cljs.core.match :refer [match]]))

(re-frame/register-handler
 :add-annotation
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id ann]}]]
   (if-let [hit-map (get-in db [:session :results-by-id hit-id])]
     (let [token-fn (fn [token] (update token :anns #(concat % [ann])))]
       (assoc-in
        db
        [:session :results-by-id hit-id]
        (update-token hit-map token-id token-fn)))
     db)))

;; (defn dispatch-annotation [{:keys [token-id ann] :as data}]
;;   (let [{:keys [ws-out]} @app-channels]
;;     (put! ws-out [:annotation data])))

(defn annotation-route-matcher
  [sc ws-in ws-out my-ws-in my-ws-out payload]
  (match [sc (:status payload)]
    [ws-in :ok] (let [{:keys [hit-id token-id ann]} payload]
                    (re-frame/dispatch
                     [:notify
                      {:msg (str "Stored annotation for token " token-id)
                       :status :ok}])
                    (re-frame/dispatch
                     [:add-annotation
                      {:hit-id hit-id
                       :token-id token-id
                       :ann ann}]))
    [ws-in :error] (let [{:keys [token-id reason e username]} payload]
                       (re-frame/dispatch
                        [:notify
                         {:msg (str "Couldn't store annotation for token: " token-id
                                    " Reason: " reason)}]))
    ;; todo; set token idle, avoid multiple anns before return?
    [ws-out :ok] (>! my-ws-out [:annotation payload])))

(defn annotation-route [handler]
  (fn [ws-in ws-out]
    (let [my-ws-in (chan) my-ws-out (chan)]
      (go (loop []
            (let [[v sc] (alts! ws-in ws-out)
                  [route payload] v]
              (if (= route :annotation)
                (annotation-route-matcher sc ws-in ws-out status payload)
                (condp = sc
                  ws-in (>! my-ws-in v)
                  ws-out (>! my-ws-out v))))))
      [my-ws-in my-ws-out])))


