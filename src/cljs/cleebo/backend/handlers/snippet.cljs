(ns cleebo.backend.handlers.snippet
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET]]))

(defn snippet-error-handler
  [{:keys [status status-content] :as error}]
  (re-frame/dispatch
   [:notify
    {:message (str "Error while retrieving snippet" status-content)
     :status :error}]))

(defn snippet-result-handler [& [context]]
  (fn [{:keys [snippet status hit-idx] :as data}]
    (let [data (case context
                 nil data
                 :left (update-in data [:snippet] dissoc :right)
                 :right (update-in data [:snippet] dissoc :left))]
      (re-frame/dispatch [:open-modal :snippet data]))))

(defn fetch-snippet [hit-idx snippet-size & {:keys [context]}]
  (GET "blacklab"
       {:handler (snippet-result-handler context)
        :error-handler snippet-error-handler 
        :params {:hit-idx hit-idx
                 :snippet-size snippet-size
                 :route :snippet}}))

(re-frame/register-handler
 :fetch-snippet
 (fn [db [_ hit-idx & {:keys [snippet-size context]}]]
   (let [snippet-size (or snippet-size (get-in db [:settings :snippets :snippet-size]))]
     (fetch-snippet hit-idx snippet-size :context context)
     db)))

(re-frame/register-handler
 :set-snippet-size
 (fn [db [_ snippet-size]]
   (assoc-in db [:settings :snippets :snippet-size] snippet-size)))

(re-frame/register-handler
 :set-snippet-delta
 (fn [db [_ snippet-delta]]
   (assoc-in db [:settings :snippets :snippet-delta] snippet-delta)))


