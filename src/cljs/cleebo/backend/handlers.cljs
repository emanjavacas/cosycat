(ns cleebo.backend.handlers
    (:require [taoensso.timbre :as timbre]
              [re-frame.core :as re-frame]
              [cleebo.backend.db :as db]
              [cleebo.localstorage :as ls]
              [cleebo.backend.middleware
               :refer [standard-middleware no-debug-middleware]]
              [cleebo.utils :refer [filter-marked-hits]]))

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
   new-db))

(re-frame/register-handler
 :dump-db
 standard-middleware
 (fn [db _]
   (ls/put! :db db)
   db))

(re-frame/register-handler
 :open-init-modal
 (fn [db _]
   (assoc-in db [:init-modal] true)))

(re-frame/register-handler
 :close-init-modal
 (fn [db _]
   (assoc-in db [:init-modal] false)))

(re-frame/register-handler
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/register-handler
 :add-notification
 (fn [db [_ {msg :msg id :id}]]
   (let [now (js/Date.)
         notification {:msg msg :date now}]
     (assoc-in db [:notifications id] notification))))

(re-frame/register-handler
 :drop-notification
 (fn [db [_ id]]
   (update-in db [:notifications] dissoc id)))

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
         merge-results (fn [old-results] (merge (keywordify-results results)
                                                (filter-marked-hits old-results)))]
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
   (if flag
     (assoc-in db [:session :results-by-id hit-id :meta :marked] true)
     (let [{:keys [hit meta] :as hit-map} (get-in db [:session :results-by-id hit-id])
           hit-map (assoc hit-map :hit (demark-all-tokens hit))
           hit-map (assoc-in hit-map [:meta :marked] false)
           hit-map (assoc-in hit-map [:meta :has-marked] false)]
       (assoc-in db [:session :results-by-id hit-id] hit-map)))))

(defn update-token [{:keys [hit meta] :as hit-map} token-id token-fn]
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
         token-fn (fn [token] (if flag
                                (assoc token :marked true)
                                (dissoc token :marked)))]
     (assoc-in
      db
      [:session :results-by-id hit-id]
      (update-token hit-map token-id token-fn)))))

(re-frame/register-handler
 :annotate
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id ann]}]]
   (let [hit-map (get-in db [:session :results-by-id hit-id])
         token-fn (fn [token] (assoc token :ann ann))]
     (assoc-in
      db
      [:session :results-by-id hit-id]
      (update-token hit-map token-id token-fn)))))

(defn handle-ws-msg [db {:keys [type msg]}]
  (case type
    :msgs (update db type conj [msg])))

(re-frame/register-handler
 :ws-in
 (fn [db [_ data]]
   (let [{:keys [status type msg]} data]
     (cond
       (= status :error) (do (timbre/debug msg) db)
       (= status :ok)    (handle-ws-msg db {:type type :msg msg})
       :else             (do (timbre/debug "Unknown status: " status) db)))))
