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

(defn annotation-ok [{type :type {O :O B :B :as scope} :scope}]
  (case type
    "IOB" (format "Stored span annotation for range [%d-%d]" B O)
    "token" (format "Stored annotation for token [%d]" scope)))

;;; message templates
(def annotation-error-tmpl "Couldn't store annotation with id %d. Reason: [%s]")
(def annotation-error-mult-tmpl "Couldn't store %d annotations! Reason: [%s]")
(def annotation-ok-mult-tmpl  "Stored %d annotations!")

(defmulti incoming-annotation (fn [{:keys [ann-map] :as data}] (type ann-map)))
(defmethod incoming-annotation
  cljs.core/PersistentArrayMap
  [{hit-id :hit-id {span :span :as ann} :ann-map}]
  (re-frame/dispatch
   [:notify
    {:message (annotation-ok span)
     :status :ok}])
  (re-frame/dispatch
   [:add-annotation
    {:hit-id hit-id
     :ann-map ann}]))
(defmethod incoming-annotation
  cljs.core/PersistentVector
  [{:keys [hit-id ann-map]}]
  (re-frame/dispatch                  ;compute an id from payload
   [:notify
    {:message (format annotation-ok-mult-tmpl (count ann-map))
     :status :ok}])
  (doseq [[ann-map hit-id] (map vector ann-map hit-id)]
    (re-frame/dispatch
     [:add-annotation
      {:hit-id hit-id
       :ann-map ann-map}])))

(defmulti incoming-annotation-error (fn [{:keys [scope]}] (type scope)))
(defmethod incoming-annotation-error
  js/Number
  [{:keys [scope reason e username]}]
  (re-frame/dispatch
   [:notify
    {:message (format annotation-error-tmpl scope reason)}]))
(defmethod incoming-annotation-error
  cljs.core/PersistentVector
  [{:keys [scope reason e username]}]
  (re-frame/dispatch
   [:notify
    {:message (format annotation-error-mult-tmpl (count scope) e)}]))
(defmethod incoming-annotation-error
  cljs.core/PersistentArrayMap
  [{:keys [{B :B O :O} reason e username]}]
  (re-frame/dispatch
   [:notify
    {:message (format annotation-error-mult-tmpl (count (range B (inc O))) e)}]))

(re-frame/register-handler
 :ws
 standard-middleware
 (fn [db [_ dir {:keys [type status data payload-id] :as payload}]]
   (match [dir type status]
     ;; annotation routes
     [:in :annotation :ok]
     (do (incoming-annotation data)
         (update-in db [:session :throbbing?] dissoc payload-id))
     
     [:in :annotation :error]
     (do (incoming-annotation-error data)
         (update-in db [:session :throbbing?] dissoc payload-id))
     
     [:out :annotation _]
     (do (send-ws payload)
         (assoc-in db [:session :throbbing? payload-id] true))

     ;; notify routes
     [:in :notify :info]
     (let [{:keys [by message]} data]
       (do (re-frame/dispatch
            [:notify
             {:message (str by " says: " message)
              :by by
              :status status}])
           db))
     [:in :notify :signup] (do (re-frame/dispatch [:add-user data]) db)
     [:in :notify :login] (do (re-frame/dispatch
                               [:user-status (:username data) true]) db)
     [:in :notify :logout] (do (re-frame/dispatch
                                [:user-status (:username data) false]) db))))
