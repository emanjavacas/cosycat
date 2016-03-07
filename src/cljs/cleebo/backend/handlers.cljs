(ns cleebo.backend.handlers
    (:require [taoensso.timbre :as timbre]
              [re-frame.core :as re-frame]
              [schema.core :as s]
              [cleebo.backend.db :as db]
              [cleebo.shared-schemas :refer [annotation-schema]]
              [cleebo.ws :refer [send-transit-msg!]]
              [cleebo.localstorage :as ls]
              [cleebo.backend.middleware
               :refer [standard-middleware no-debug-middleware db-schema]]
              [cleebo.utils :refer [filter-marked-hits time-id]]))

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
                           {:msg "Oops! Couldn't load backup"
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
       {:msg "State succesfully backed-up"
        :status :ok}]))
   db))

(re-frame/register-handler
 :open-ls-modal
 (fn [db _]
   (assoc-in db [:ls-modal] true)))

(re-frame/register-handler
 :close-ls-modal
 (fn [db _]
   (assoc-in db [:ls-modal] false)))

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
 (fn [db [_ {:keys [msg by status] :as data}]]
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

(defn demark-all-tokens [hit]
  (map #(dissoc % :marked) hit))

(re-frame/register-handler
 :mark-hit
 standard-middleware
 (fn [db [_ {:keys [hit-id flag]}]]
   (assoc-in db [:session :results-by-id hit-id :meta :marked] (boolean flag))))

(defn update-token
  "apply token-fn where due"
  [{:keys [hit meta] :as hit-map} token-id token-fn]
  (assoc
   hit-map
   :hit
   (map (fn [{:keys [id] :as token}]
          (if (= id token-id)
            (token-fn token)
            token))
        hit)))

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

(re-frame/register-handler
 :annotate
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id ann]}]]
   (let [hit-map (get-in db [:session :results-by-id hit-id])
         token-fn (fn [token] (update token :anns #(concat % [ann])))]
     (re-frame/dispatch
      [:sent-ws
       {:type :annotation
        :data {:cpos token-id
               :ann ann}}])
     (assoc-in
      db
      [:session :results-by-id hit-id]
      (update-token hit-map token-id token-fn)))))

(re-frame/register-handler
 :sent-ws
 standard-middleware
 (fn [db [_ {:keys [type data] :as payload}]]
   (send-transit-msg! payload)
;   (assoc-in db [:requests type] {:status :runnning})
   db))

(declare on-ws-success on-ws-error)
(re-frame/register-handler
 :receive-ws
 (fn [db [_ payload]]
   (let [{:keys [status type data]} payload]
     (cond
       (= status :error) (on-ws-error   db {:type type :data data})
       (= status :ok)    (on-ws-success db {:type type :data data})
       :else             (throw (js/Error. (str "Unknown status: " status)))))))

(defn annotation-route [{:keys [cpos] :as data}]
  (re-frame/dispatch
   [:notify
    {:msg "Hooray! Annotation stored"
     :status :ok}]))

(defn on-ws-success
  [db {:keys [type data] :as payload}]
  (case type
    :annotation (annotation-route data))
  db)

(defn on-ws-error [db {:keys [type data]}]
  (let [msg (case type
              :annotation "Oops! We couldn't store your annotation"
              "Oops there was a dwarfs in the channel")]
    (re-frame/dispatch
     [:notify
      {:msg msg
       :status :error}])
    db))
