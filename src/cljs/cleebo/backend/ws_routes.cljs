(ns cleebo.backend.ws-routes
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.ws :refer [send-ws]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [schema.core :as s]
            [goog.string :as gstring]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.match :refer [match]]))

(defn format [fmt & args]
  (apply gstring/format fmt args))

;;; message templates
(def annotation-error-tmpl "Couldn't store annotation with id %d. Reason: [%s]")
(def annotation-error-mult-tmpl "Couldn't store %d annotations! Reason: [%s]")
(def annotation-ok-tmpl "Stored annotation for token %d")
(def annotation-ok-mult-tmpl  "Stored %d annotations!")

(defmulti incoming-annotation (fn [{:keys [token-id]}] (type token-id)))

(defmethod incoming-annotation cljs.core/PersistentArrayMap
  [{:keys [hit-id token-id anns]}]
  (let [throbbing-id token-id]
    (re-frame/dispatch
     [:notify
      {:message (format annotation-ok-tmpl token-id)
       :status :ok}])
    (re-frame/dispatch
     [:add-annotation
      {:hit-id hit-id
       :token-id token-id
       :anns anns}])
    throbbing-id))

(defmethod incoming-annotation cljs.core/PersistentVector
  [{:keys [hit-id token-id anns]}]
  (let [throbbing-id "todo"]
    (re-frame/dispatch                  ;compute an id from payload
     [:notify
      {:message (format annotation-ok-mult-tmpl (count anns))
       :status :ok}])
    (doseq [[anns hit-id token-id] (map vector anns hit-id token-id)]
      (re-frame/dispatch
       [:add-annotation
        {:hit-id hit-id
         :token-id token-id
         :anns anns}]))
    throbbing-id))

(defmulti incoming-annotation-error (fn [{:keys [token-id]}] (type token-id)))
(defmethod incoming-annotation-error
  js/Number
  [{:keys [token-id reason e username]}]
  (let [throbbing-id token-id]
    (re-frame/dispatch
     [:notify
      {:message (format annotation-error-tmpl token-id reason)}])
    throbbing-id))

(defmethod incoming-annotation-error
  cljs.core/PersistentVector
  [{:keys [token-id reason e username]}]
  (let [throbbing-id "todo"]
    (re-frame/dispatch
     [:notify
      {:message (format annotation-error-mult-tmpl (count token-id) reason)}])))

(re-frame/register-handler
 :ws
 standard-middleware
 (fn [db [_ dir {:keys [type status data] :as payload}]]
   (match [dir type status]
     ;; annotation routes
     [:in :annotation :ok]
     (let [throbbing-id (incoming-annotation data)]
       (update-in db [:session :throbbing?] dissoc throbbing-id))
     
     [:in :annotation :error]
     (let [throbbing-id (incoming-annotation-error data)]
       (update-in db [:session :throbbing?] dissoc throbbing-id))
     
     [:out :annotation _]
     (let [{token-id :token-id {timestamp :timestamp} :ann} data]
       (if-let [throbbing? (get-in db [:session :throbbing? token-id])]
         (do (re-frame/dispatch
              [:notify
               {:message (str "Processing annotation")
                :status :info}])
             db)
         (do (send-ws payload)
             (assoc-in db [:session :throbbing? token-id] true))))
     ;; notify routes
     [:in :notify _]
     (let [{:keys [by message]} data]
       (do (re-frame/dispatch
            [:notify
             {:message (str by " says " message)
              :by by
              :status status}])
           db)))))

