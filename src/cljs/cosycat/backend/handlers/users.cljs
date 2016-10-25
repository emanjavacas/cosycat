(ns cosycat.backend.handlers.users
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET POST]]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [cosycat.utils :refer [format current-results]]
            [cosycat.app-utils :refer [update-coll deep-merge dekeyword]]))

(re-frame/register-handler              ;set client user info
 :set-user
 standard-middleware
 (fn [db [_ path value]]
   (assoc-in db (into [:me] path) value)))

(re-frame/register-handler
 :update-user
 standard-middleware
 (fn [db [_ update-map]]
   (update db :me deep-merge update-map)))

(defn set-users
  [db [name path value]]
  (let [pred (fn [{username :username}] (= username name))]
    (update db :users update-coll pred assoc-in (into [:user] path) value)))

(defn update-users
  [db name update-map]
  (let [pred (fn [{username :username}] (= username name))]
    (update db :users update-coll pred deep-merge {:user update-map})))

(re-frame/register-handler              ;set other users info
 :set-users
 standard-middleware
 (fn [db [_ name path value]]
   (set-users db [name path value])))

(re-frame/register-handler
 :update-users
 standard-middleware
 (fn [db [_ name update-map]]
   (update-users db name update-map)))

(re-frame/register-handler              ;add user to client (after new signup)
 :add-user
 standard-middleware
 (fn [db [_ user]]
   (let [user (update-in user [:roles] (partial apply hash-set))]
     (update-in db [:users] into [{:username (:username user) :user user}]))))

(re-frame/register-handler
 :new-user-avatar
 (fn [db [_ {:keys [username avatar]}]]
   (set-users db [username [:avatar] avatar])))

(re-frame/register-handler
 :update-user-active
 standard-middleware
 (fn [db [_ username status]]
   (set-users db [username [:active] status])))

(re-frame/register-handler
 :fetch-user-info
 (fn [db [_ username]]
   (GET "users/user-info"
        {})
   db))

(defn avatar-error-handler [& args]
  (re-frame/dispatch [:notify {:message "Couldn't update avatar" :status "danger"}]))

(re-frame/register-handler
 :regenerate-avatar
 (fn [db _]
   (POST "users/new-avatar"
         {:params {}
          :handler #(re-frame/dispatch [:set-user [:avatar] %])
          :error-handler avatar-error-handler})
   db))

(defn update-user-profile-error-handler
  [{{:keys [code data]} :response}]
  (case code
    :user-exists (let [msg (format "%s already exists" (-> data dekeyword str/capitalize))]
                   (re-frame/dispatch [:notify {:message msg :status "danger"}]))
    (re-frame/dispatch
     [:notify {:message "Couldn't update profile" :status "danger"}])))

(re-frame/register-handler
 :update-user-profile
 (fn [db [_ update-map]]
   (POST "users/update-profile"
         {:params {:update-map update-map}
          :handler #(re-frame/dispatch [:update-user %])
          :error-handler update-user-profile-error-handler})
   db))

(re-frame/register-handler
 :query-users
 (fn [db [_ value users-atom & {:keys [remove-project-users] :or {remove-project-users true}}]]
   (let [active-project (get-in db [:session :active-project])
         project-users (if-not remove-project-users [] (get-in db [:projects active-project :users]))]
     (GET "users/query-users"
          {:params {:value value :project-users project-users}
           :handler #(reset! users-atom %)
           :error-handler #(.log js/console "Error" %)}))
   db))

;;; Query metadata
(re-frame/register-handler
 :new-query-metadata
 standard-middleware
 (fn [db [_ {:keys [id] :as query-metadata}]]
   (let [active-project (get-in db [:session :active-project])]
     (assoc-in db [:projects active-project :queries id] query-metadata))))

(defn query-new-metadata-handler [{:keys [id] :as query-metadata}]
  (re-frame/dispatch [:set-active-query id])
  (re-frame/dispatch [:new-query-metadata query-metadata]))

(defn query-new-metadata-error-handler [{{:keys [message code data]} :response}]
  (re-frame/dispatch [:notify {:message message}]))

(re-frame/register-handler
 :query-new-metadata
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         {:keys [corpus]} (get-in db [:settings :query])         
         {query-str :query-str} (current-results db)]
     (POST "/users/new-query-metadata"
           {:params {:project-name active-project
                     :query-data {:query-str query-str :corpus corpus}}
            :handler query-new-metadata-handler
            :error-handler query-new-metadata-error-handler}))
   db))

(re-frame/register-handler
 :add-query-metadata
 standard-middleware
 (fn [db [_ {:keys [id discarded]}]]
   (let [active-project (get-in db [:session :active-project])]
     (assoc-in db [:projects active-project :query id :discarded] discarded))))

(re-frame/register-handler
 :query-add-metadata
 (fn [db [_ hit-id query-id]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/users/add-query-metadata"
           {:params {:project-name active-project
                     :id query-id
                     :discarded hit-id}
            :handler #(re-frame/dispatch [:add-query-metadata %])
            :error-handler #(timbre/error "Error when storing query metadata")}))
   db))

(re-frame/register-handler
 :remove-query-metadata
 standard-middleware
 (fn [db [_ id hit-id]]
   (let [active-project (get-in db [:session :active-project])]
     (update-in db [:projects active-project :queries id :discarded] #(vec (remove (partial = hit-id) %))))))

(re-frame/register-handler
 :query-remove-metadata
 (fn [db [_ hit-id query-id]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/users/add-query-metadata"
           {:params {:project-name active-project
                     :id query-id
                     :discarded hit-id}
            :handler #(re-frame/dispatch [:remove-query-metadata query-id hit-id])
            :error-handler #(timbre/error "Error when storing query metadata")}))
   db))

(re-frame/register-handler
 :launch-query
 (fn [db [_ query-id]]
   (let [active-project (get-in db [:session :active-project])]
     (if-let [{{query-str :query-str} :query-data} (get-in db [:projects active-project :queries query-id])]
       (do (set! (.-value (.getElementById js/document "query-str")) query-str)
           (re-frame/dispatch [:query query-str :set-active query-id]))))
   db))
