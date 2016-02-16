(ns cleebo.backend.handlers
    (:require [taoensso.timbre :as timbre]
              [re-frame.core :as re-frame]
              [cleebo.backend.db :as db]))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   db/default-db))

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
 (fn [db [_ panel]]
   (assoc-in db [:throbbing? panel] true)))

(re-frame/register-handler
 :stop-throbbing
 (fn [db [_ panel]]
   (assoc-in db [:throbbing? panel] false)))

(re-frame/register-handler
 :set-name
 (fn [db [_ name]]
   (assoc db :name name)))

(re-frame/register-handler
 :set-session
 (fn [db [_ path value]]
   (let [session (:session db)]
     (assoc db :session (assoc-in session path value)))))

(re-frame/register-handler
 :set-query-results
 (fn [db [_ & [{:keys [results query-size query-str status from to] :as data}]]]
   (let [query-results (dissoc data :results)]
     (-> db
         (update-in [:session :query-results] merge query-results)
         ;; this leaks, of course, try to only retain marked hits
         ;; and overwrite those coming from blacklab
         (update-in [:session :results] merge results)))))

(re-frame/register-handler
 :mark-hit
 (fn [db [_ hit-num]]
   (assert (get-in db [:session :results hit-num]))
   (update-in db [:session :results hit-num :meta :marked] true)))

(re-frame/register-handler
 :mark-token
 (fn [db [_ hit-num token-id]]
   (let [hit (get-in db [:session :results hit-num :hit])]
     (assert (and hit (some #(= token-id (:id %)) hit)))
     (assoc-in db
               [:session :results hit-num :hit]
               (map (fn [{:keys [id] :as token}]
                      (if (= id token-id)
                        (assoc token :marked true)
                        token))
                    hit)))))

(re-frame/register-handler
 :demark-hit
 (fn [db [_ hit-num]]
   (assert (get-in db [:session :results hit-num]))
   (update-in db [:session :results hit-num :meta :marked] false)))

(re-frame/register-handler
 :demark-token
 (fn [db [_ hit-num token-id]]
   (let [hit (get-in db [:session :results hit-num :hit])]
     (assert hit)
     (assert (some #(= token-id (:id %)) hit))
     (assoc-in db
               [:session :results hit-num :hit]
               (map (fn [{:keys [id] :as token}]
                      (if (= id token-id)
                        (dissoc token :marked)
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
