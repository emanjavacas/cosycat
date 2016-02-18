(ns cleebo.backend.handlers
    (:require [taoensso.timbre :as timbre]
              [re-frame.core :as re-frame]
              [cleebo.backend.db :as db]
              [cleebo.localstorage :as ls]
              [cleebo.backend.middleware
               :refer [standard-middleware no-debug-middleware]]
              [cleebo.utils :refer [filter-marked]]))

(re-frame/register-handler
 :initialize-db
 (fn [_ _]
   db/default-db))

(re-frame/register-handler
 :reset-db
 standard-middleware
 (fn [_ _]
   db/default-db))

(re-frame/register-handler
 :load-db
 standard-middleware
 (fn [db [_ new-db]]
   new-db))

(re-frame/register-handler
 :dump-db
 (fn [db _]
   (ls/put! :db db)
   db))

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

(re-frame/register-handler
 :set-query-results
 no-debug-middleware
 (fn [db [_ & [{:keys [results query-size query-str status from to] :as data}]]]
   (let [query-results (dissoc data :results)]
     (-> db
         (update-in [:session :query-results] merge query-results)
         (update-in [:session :results]
                    (fn [old-results new-results]
                      (merge new-results (filter-marked old-results)))
                    results)))))

(re-frame/register-handler
 :mark-hit
 standard-middleware
 (fn [db [_ {:keys [hit-num flag]}]]
   (assoc-in db [:session :results hit-num :meta :marked] flag)))

(re-frame/register-handler
 :mark-token
 standard-middleware
 (fn [db [_ {:keys [hit-num token-id flag]}]]
   (let [hit (get-in db [:session :results hit-num :hit])]
     (assoc-in db
               [:session :results hit-num :hit]
               (map (fn [{:keys [id] :as token}]
                      (if (= id token-id)
                        (if flag
                          (assoc token :marked true)
                          (dissoc token :marked))
                        token))
                    hit)))))

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
