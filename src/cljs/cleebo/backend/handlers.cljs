(ns cleebo.backend.handlers
    (:require [taoensso.timbre :as timbre]
              [re-frame.core :as re-frame]
              [ajax.core :refer [GET]]
              [schema.core :as s]
              [cleebo.backend.db :as db]
              [cleebo.localstorage :as ls]
              [cleebo.schemas.schemas :refer [db-schema]]
              [cleebo.backend.middleware
               :refer [standard-middleware no-debug-middleware]]
              [cleebo.utils :refer
               [filter-marked-hits time-id update-token deep-merge]]))

(re-frame/register-handler
 :initialize-db
 (fn [_ _]
   db/default-db))

(re-frame/register-handler
 :reset-db
 no-debug-middleware
 (fn [_ _]
   db/default-db))

(re-frame/register-handler
 :load-db
 standard-middleware
 (fn [db [_ new-db]]
   (try
     (s/validate db-schema new-db)
     new-db
     (catch :default e
       (re-frame/dispatch [:notify
                           {:message "Oops! Couldn't load backup"
                            :status :error}])
       db))))

(re-frame/register-handler
 :dump-db
 standard-middleware
 (fn [db _]
   (let [now (js/Date)]
     (ls/put now db)
     (re-frame/dispatch
      [:notify
       {:message "State succesfully backed-up"
        :status :ok}]))
   db))

(re-frame/register-handler
 :open-modal
 standard-middleware
 (fn [db [_ modal & [data]]]
   (if-not data
     (assoc-in db [:modals modal] true)     
     (update-in db [:modals modal] deep-merge data))))

(re-frame/register-handler
 :close-modal
 (fn [db [_ modal]]
   (assoc-in db [:modals modal] false)))

(re-frame/register-handler
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/register-handler
 :add-notification
 standard-middleware
 (fn [db [_ {:keys [data id] :as notification}]]
   (assoc-in db [:notifications id] (assoc-in notification [:data :date] (js/Date.)))))

(re-frame/register-handler
 :drop-notification
 standard-middleware
 (fn [db [_ id]]
   (update-in db [:notifications] dissoc id)))

(re-frame/register-handler
 :notify
 (fn [db [_ {:keys [message by status] :as data}]]
   (let [id (time-id)
         delay (get-in db [:settings :delay])]
     (js/setTimeout #(re-frame/dispatch [:drop-notification id]) delay)
     (re-frame/dispatch [:add-notification {:data data :id id}]))
   db))

(re-frame/register-handler
 :start-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (assoc-in db [:throbbing? panel] true)))

(re-frame/register-handler
 :stop-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (assoc-in db [:throbbing? panel] false)))

(re-frame/register-handler
 :set-session
 (fn [db [_ path value]]
   (let [session (:session db)]
     (assoc db :session (assoc-in session path value)))))

(defn keywordify-results [results]
  (into {} (map (juxt :id identity) results)))

(re-frame/register-handler
 :set-query-results
 standard-middleware
 (fn [db [_ & [{:keys [results query-size query-str status from to] :as data}]]]
   (let [query-results (dissoc data :results)
         merge-results (fn [old-results]
                         (merge (keywordify-results results)
                                (filter-marked-hits old-results :has-marked? true)))]
     (-> db
         (update-in [:session :query-results] merge query-results)
         (assoc-in [:session :results] (map :id results))
         (update-in [:session :results-by-id] merge-results)))))

(re-frame/register-handler
 :mark-hit
 standard-middleware
 (fn [db [_ {:keys [hit-id flag]}]]
   (assoc-in db [:session :results-by-id hit-id :meta :marked] (boolean flag))))

(defn has-marked?
  "for a given hit-map we look if the current (de)marking update
  leave behind marked tokens. The result will be false if no marked
  tokens remain, otherwise it returns the token-id of the token
  being marked for debugging purposes"
  [{:keys [hit meta] :as hit-map} flag token-id]
  (if flag
    true
    (let [marked-tokens (filter :marked hit)]
      (case (count marked-tokens)
            0 (throw (js/Error. "Trying to demark a token, but no marked tokens found"))
            1 (do (assert (= token-id (:id (first marked-tokens)))) false)
            (some #(= token-id %) (map :id marked-tokens))))))

(re-frame/register-handler
 :mark-token
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id flag]}]]
   (let [hit-map (get-in db [:session :results-by-id hit-id])
         has-marked (has-marked? hit-map flag token-id)
         hit-map (assoc-in hit-map [:meta :has-marked] (boolean has-marked))
         token-fn #(if flag (assoc % :marked true) (dissoc % :marked))]
     (assoc-in
      db
      [:session :results-by-id hit-id]
      (update-token hit-map token-id token-fn)))))

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
   (let [snippet-size (or snippet-size (get-in db [:settings :snippet-size]))]
     (fetch-snippet hit-idx snippet-size :context context)
     db)))

