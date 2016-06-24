(ns cleebo.backend.handlers.components
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [cleebo.utils :refer [time-id]]
            [cleebo.app-utils :refer [deep-merge]]))

(re-frame/register-handler
 :open-modal
 standard-middleware
 (fn [db [_ modal & [data]]]
   (if-not data
     (assoc-in db [:session :modals modal] true)     
     (update-in db [:session :modals modal] deep-merge data))))

(re-frame/register-handler
 :close-modal
 standard-middleware
 (fn [db [_ modal]]
   (assoc-in db [:session :modals modal] false)))

(re-frame/register-handler
 :start-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (assoc-in db [:session :throbbing? panel] true)))

(re-frame/register-handler
 :stop-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (assoc-in db [:session :throbbing? panel] false)))

(re-frame/register-handler
 :register-error
 standard-middleware
 (fn [db [_ component-id data]]
   (assoc-in db [:session :has-error? component-id] data)))

(re-frame/register-handler
 :drop-error
 standard-middleware
 (fn [db [_ component-id]]
   (update-in db [:session :has-error?] dissoc component-id)))
