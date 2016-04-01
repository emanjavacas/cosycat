(ns cleebo.backend.ws-routes
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.ws :refer [send-ws]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [schema.core :as s]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.match :refer [match]]))

(def cljs-vec cljs.core/PersistentVector)
(def cljs-map cljs.core/PersistentArrayMap)

(defmulti incoming-annotation (fn [data] (type data)))

(defmethod incoming-annotation cljs-map
  [{:keys [hit-id token-id anns]}]
  (let [throbbing-id token-id]
    (re-frame/dispatch
     [:notify
      {:message (str "Stored annotation for token " token-id)
       :status :ok}])
    (re-frame/dispatch
     [:add-annotation
      {:hit-id hit-id
       :token-id token-id
       :anns anns}])
    throbbing-id))

(defmethod incoming-annotation cljs-vec
  [{:keys [anns hit-id token-id]}]
  (let [throbbing-id "todo"]
    (re-frame/dispatch
     [:notify
      {:message (str "Stored " (count anns) " annotations")
       :status :ok}])
    (timbre/debug anns hit-id token-id)
    (doseq [[anns hit-id token-id] (map vector anns hit-id token-id)]
      (re-frame/dispatch
       [:add-annotation
        {:hit-id hit-id
         :token-id token-id
         :anns anns}]))))

(re-frame/register-handler
 ;; TODO: handle timeouts with channels inside this handler
 :ws
 standard-middleware
 (fn [db [_ dir {:keys [type status data] :as payload}]]
   (match [dir type status]
     ;; annotation routes
     [:in :annotation :ok]
     (let [throbbing-id (incoming-annotation data)]
       (update-in db [:throbbing?] dissoc throbbing-id))
     
     [:in :annotation :error]
     (let [{:keys [token-id reason e username]} data]
       (re-frame/dispatch
        [:notify
         {:message (str "Couldn't store annotation for token: " token-id
                        " Reason: " reason)}]
        (update-in db [:throbbing?] dissoc token-id)))
     
     [:out :annotation _]
     (let [{token-id :token-id {timestamp :timestamp} :ann} data]
       (if-let [throbbing? (= timestamp (get-in db [:throbbing? token-id]))]
         (do (re-frame/dispatch
              [:notify
               {:message (str "Processing annotation")
                :status :info}])
             db)
         (do (send-ws payload)
             (assoc-in db [:throbbing? token-id] timestamp))))
     ;; notify routes
     [:in :notify _]
     (let [{:keys [by message]} data]
       (do (re-frame/dispatch
            [:notify
             {:message (str by " says " message)
              :by by
              :status status}])
           db)))))

