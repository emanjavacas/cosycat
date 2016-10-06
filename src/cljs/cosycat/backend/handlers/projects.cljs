(ns cosycat.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cosycat.app-utils :refer [pending-users deep-merge update-coll]]
            [cosycat.routes :refer [nav!]]
            [cosycat.backend.middleware :refer [standard-middleware check-project-exists]]
            [cosycat.backend.db
             :refer [default-project-session default-project-history default-settings]]
            [taoensso.timbre :as timbre]))

(defn normalize-projects
  "transforms server project to client project"
  [projects user]
  (reduce
   (fn [acc {:keys [name] :as project}]
     (let [{:keys [history settings]} (some #(when (= name (:name %)) %) (:projects user))]
       (assoc acc name (-> project
                           (assoc :session (default-project-session project))
                           (cond-> history (assoc :history history))
                           (cond-> settings (assoc :settings settings))))))
   {}
   projects))

(re-frame/register-handler
 :update-project-user-role
 standard-middleware
 (fn [db [_ project-name username new-role]]
   (let [pred #(= username (:username %))]
     (update-in db [:projects project-name :users] update-coll pred assoc :role new-role))))

(re-frame/register-handler
 :remove-active-project
 standard-middleware
 (fn [db _]
   (assoc-in db [:session :active-project] nil)))

(re-frame/register-handler
 :set-active-project
 (conj standard-middleware check-project-exists)
 (fn [db [_ {:keys [project-name]}]]
   (let [project-settings (or (get-in db [:projects project-name :settings])
                              (get-in db [:me :settings])
                              (default-settings :corpora (:corpora db)))]
     (-> db
         (assoc-in [:session :active-project] project-name)
         (update-in [:settings] deep-merge project-settings)))))

(re-frame/register-handler              ;add project to client-db
 :add-project
 standard-middleware
 (fn [db [_ project]]
   (update db :projects merge (normalize-projects [project] (:me db)))))

(re-frame/register-handler              ;remove project from client-db
 :remove-project
 standard-middleware
 (fn [db [_ project-name]]
   (update db :projects dissoc project-name)))

(defn error-handler [{{:keys [message data code]} :response}]
  (re-frame/dispatch [:notify {:message message :meta data :status :error}]))

(defn new-project-handler [{project-name :name :as project}]
  (re-frame/dispatch [:register-history [:app-events] {:type :new-project :data project}])
  (re-frame/dispatch [:add-project project])
  (nav! (str "/project/" project-name)))

(re-frame/register-handler
 :new-project
 standard-middleware
 (fn [db [_ {:keys [name description users] :as project}]]
   (POST "/project/new"
         {:params {:project-name name
                   :description description
                   :users users}
          :handler new-project-handler
          :error-handler error-handler})
   db))

(re-frame/register-handler              ;add project update to client-db
 :add-project-update
 standard-middleware
 (fn [db [_ {:keys [payload project-name]}]]
   (update-in db [:projects project-name :issues] conj payload)))

(defn project-update-handler [project-update]
  (re-frame/dispatch [:add-project-update project-update]))

(re-frame/register-handler
 :project-update
 standard-middleware
 (fn [db [_ {:keys [payload project-name]}]]
   (POST "/project/update"
         {:params {:project-name project-name :payload payload}
          :handler project-update-handler
          :error-handler error-handler})
   db))

(re-frame/register-handler              ;add user to project in client-db
 :add-project-user
 standard-middleware
 (fn [db [_ {:keys [user project-name] :as data}]]
   (re-frame/dispatch [:register-history [:project-events] {:type :add-project-user :data data}])
   (update-in db [:projects project-name :users] conj user)))

(defn new-user-handler [user]
  (re-frame/dispatch [:add-project-user user]))

(re-frame/register-handler
 :project-add-user
 standard-middleware
 (fn [db [_ {:keys [user]}]]
   (let [project-name (get-in db [:session :active-project])]
     (POST "/project/add-user"
           {:params {:user user :project-name project-name}
            :handler new-user-handler
            :error-handler error-handler}))
   db))

(re-frame/register-handler              ;remove user from project in client-db
 :remove-project-user
 standard-middleware
 (fn [db [_ {:keys [username project-name]}]]
   (update-in
    db [:projects project-name :users]
    (fn [users] (vec (remove #(= (:username %) username) users))))))

(defn remove-user-handler [project-name]
  (fn []
    (nav! "/")
    (re-frame/dispatch [:remove-project project-name])
    (re-frame/dispatch [:notify {:message (str "Goodbye from project " project-name)}])))

(re-frame/register-handler
 :project-remove-user
 (fn [db [_ project-name]]
   (POST "/project/remove-user"
         {:params {:project-name project-name}
          :handler (remove-user-handler project-name)
          :error-handler error-handler})
   db))

(defn parse-remove-project-payload [payload]
  (if (empty? payload) :project-removed
      :added-project-remove-agree))

(defn remove-project-handler [{project-name :name :as project}]
  (fn [payload]
    (case (parse-remove-project-payload payload)
      :project-removed
      (do (re-frame/dispatch
           [:remove-project project-name])
          (re-frame/dispatch
           [:register-history [:app-events] {:type :remove-project :data project-name}])
          (re-frame/dispatch
           [:notify {:message (str "Project " project-name " was successfully deleted")}])
          (nav! "/"))
      :added-project-remove-agree
      (let [updated-project (update-in project [:issues] conj payload)
            {:keys [pending]} (pending-users updated-project)] ;still users
        (re-frame/dispatch [:add-project-update {:payload payload :project-name project-name}])
        (re-frame/dispatch
         [:notify {:message (str (count pending) " users pending to remove project")}]))
      (throw (js/Error. "Couldn't parse remove-project payload")))))

(re-frame/register-handler
 :project-remove
 (fn [db [_ {:keys [project-name]}]]
   (POST "/project/remove-project"
         {:params {:project-name project-name}
          :handler (remove-project-handler (get-in db [:projects project-name]))
          :error-handler error-handler})
   db))

(defn handle-new-user-role [project-name]
  (fn [{:keys [username role]}]
    (re-frame/dispatch
     [:notify {:message (format "Succesfully updated %s's role to [%s]" username role)}])
    (re-frame/dispatch [:update-project-user-role project-name username role])))

(re-frame/register-handler
 :user-role-update
 (fn [db [_ {:keys [username new-role]}]]
   (let [project-name (get-in db [:session :active-project])]
     (POST "/project/update-user-role"
           {:params {:project-name project-name
                     :username username
                     :new-role new-role}
            :handler (handle-new-user-role project-name)
            :error-handler error-handler})
     db)))
