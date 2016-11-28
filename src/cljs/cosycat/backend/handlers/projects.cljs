(ns cosycat.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cosycat.utils :refer [format current-results]]
            [cosycat.app-utils :refer [get-pending-users deep-merge update-coll]]
            [cosycat.routes :refer [nav!]]
            [cosycat.backend.middleware :refer [standard-middleware check-project-exists]]
            [cosycat.backend.db
             :refer [default-project-session default-project-history default-settings]]
            [taoensso.timbre :as timbre]))

(defn normalize-query-metadata [{:keys [discarded] :as query-metadata}]
  (assoc query-metadata :discarded (set (map :hit discarded))))

(defn normalize-queries [queries]
  (reduce-kv
   (fn [acc k v] (assoc acc k (normalize-query-metadata v)))
   {}
   queries))

(defn normalize-projects
  "transforms server project to client project"
  [projects]
  (reduce
   (fn [acc {:keys [name queries issues events] :as project}]
     (assoc acc name (cond-> project
                       true (assoc :session (default-project-session project))
                       issues (assoc :issues (zipmap (map :id issues) issues))
                       events (assoc :events (zipmap (map :id events) events))
                       queries (assoc :queries (normalize-queries queries)))))
   {}
   projects))

(re-frame/register-handler
 :remove-active-project
 standard-middleware
 (fn [db _]
   (assoc-in db [:session :active-project] nil)))

(defn get-project-settings [db project-name]
  (or (get-in db [:projects project-name :settings]) ;project-related settings
      (get-in db [:me :settings])                    ;global settings
      (default-settings :corpora (:corpora db))))    ;default settings

(re-frame/register-handler
 :reset-settings
 standard-middleware
 (fn [db [_ & {:keys [init] :or {init {}}}]]
   (let [active-project (get-in db [:session :active-project])
         project-settings (deep-merge (get-project-settings db active-project) init)]
     (update db :settings deep-merge project-settings))))

(re-frame/register-handler
 :set-active-project
 (conj standard-middleware check-project-exists)
 (fn [db [_ {:keys [project-name]}]]
   (let [project-settings (get-project-settings db project-name)]
     (-> db
         (assoc-in [:session :active-project] project-name)
         (update :settings deep-merge project-settings)))))

(re-frame/register-handler              ;add project to client-db
 :add-project
 standard-middleware
 (fn [db [_ project]]
   (update db :projects merge (normalize-projects [project]))))

(re-frame/register-handler              ;remove project from client-db
 :remove-project
 standard-middleware
 (fn [db [_ project-name]]
   (update db :projects dissoc project-name)))

(defn error-handler [{{:keys [message data code]} :response}]
  (re-frame/dispatch [:notify {:message message :meta data :status :error}]))

(defn new-project-handler [{project-name :name :as project}]
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
 :update-project-issue
 standard-middleware
 (fn [db [_ project-name {id :id :as issue}]]
   (update-in db [:projects project-name :issues id] deep-merge issue)))

(re-frame/register-handler
 :add-issue-meta
 standard-middleware
 (fn [db [_ issue-id path value]]
   (let [active-project (get-in db [:session :active-project])]
     (assoc-in db (into [:projects active-project :issues issue-id :meta] path) value))))

(re-frame/register-handler
 :update-issue-meta
 standard-middleware
 (fn [db [_ issue-id path update-fn]]
   (let [active-project (get-in db [:session :active-project])]
     (update-in db (into [:projects active-project :issues issue-id :meta] path) update-fn))))

(defn project-add-issue-handler [project-name]
  (fn [issue]
    (re-frame/dispatch [:notify {:message "New issue was added to project"}])
    (re-frame/dispatch [:update-project-issue project-name issue])))

(re-frame/register-handler
 :project-issue
 standard-middleware
 (fn [db [_ {:keys [payload project-name]}]]
   (let [project-name (or project-name (get-in db [:session :active-project]))]
     (POST "/project/issues/new"
           {:params {:project-name project-name :payload payload}
            :handler (project-add-issue-handler project-name)
            :error-handler error-handler}))
   db))

(re-frame/register-handler
 :comment-on-issue
 (fn [db [_ {:keys [comment issue-id parent-id] :as params}]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/project/issues/comment/new"
           {:params (assoc params :project-name active-project)
            :handler #(re-frame/dispatch [:update-project-issue active-project %])
            :error-handler #(re-frame/dispatch
                             [:notify {:message "Couldn't store comment" :status :error}])})
     db)))

(re-frame/register-handler
 :delete-comment-on-issue
 (fn [db [_ {:keys [comment-id issue-id] :as params}]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/project/issues/comment/delete"
           {:params (assoc params :project-name active-project)
            :handler #(re-frame/dispatch [:update-project-issue active-project %])
            :error-handler #(re-frame/dispatch
                             [:notify {:message "Couldn't delete comment" :status :error}])})
     db)))

(defn open-annotation-fn [issue-type]  
  (fn [db [_ ann-data users]] ;; ann-data is (assoc previous-ann :value new-ann-value)
    (let [project (get-in db [:session :active-project])
          corpus (get-in db [:projects project :session :query :results-summary :corpus])
          query (get-in db [:projects project :session :query :results-summary :query-str])
          ann-data (assoc ann-data :corpus corpus :query query)]
      (POST "/project/issues/annotation-edit/open"
            {:params {:project-name project
                      :type issue-type
                      :users users
                      :ann-data ann-data}
             :handler (project-add-issue-handler project)
             :error-handler error-handler})
      db)))

(re-frame/register-handler
 :open-annotation-edit
 (open-annotation-fn "annotation-edit"))

(re-frame/register-handler
 :open-annotation-remove
 (open-annotation-fn "annotation-remove"))

(re-frame/register-handler              ;add user to project in client-db
 :add-project-user
 standard-middleware
 (fn [db [_ {:keys [user project-name]}]]
   (update-in db [:projects project-name :users] conj user)))

(defn project-add-user-handler [{:keys [user project-name] :as data}]
  (re-frame/dispatch [:add-project-user data]))

(re-frame/register-handler
 :project-add-user
 standard-middleware
 (fn [db [_ {:keys [username role]}]]
   (let [project-name (get-in db [:session :active-project])]
     (POST "/project/add-user"
           {:params {:username username :role role :project-name project-name}
            :handler project-add-user-handler
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
  (if (empty? payload)
    :project-removed
    :added-project-remove-agree))

(defn remove-project-handler [{project-name :name :as project}]
  (fn [{:keys [id] :as delete-issue}]
    (case (parse-remove-project-payload delete-issue)
      :project-removed
      (do (re-frame/dispatch [:remove-project project-name])
          (re-frame/dispatch [:notify {:message (str "Project " project-name " was deleted")}])
          (nav! "/"))
      :added-project-remove-agree
      (let [updated-project (update project :issues assoc id delete-issue)
            {:keys [pending-users]} (get-pending-users updated-project)] ;still users
        (re-frame/dispatch [:update-project-issue project-name delete-issue])
        (re-frame/dispatch
         [:notify {:message (str (count pending-users) " users pending to remove project")}]))
      (throw (js/Error. "Couldn't parse remove-project payload")))))

(re-frame/register-handler
 :project-remove
 (fn [db [_ {:keys [project-name]}]]
   (POST "/project/remove-project"
         {:params {:project-name project-name}
          :handler (remove-project-handler (get-in db [:projects project-name]))
          :error-handler error-handler})
   db))

(re-frame/register-handler
 :update-project-user-role
 standard-middleware
 (fn [db [_ project-name username new-role]]
   (let [pred #(= username (:username %))]
     (update-in db [:projects project-name :users] update-coll pred assoc :role new-role))))

(defn handle-new-user-role [project-name]
  (fn [{:keys [username role]}]
    ;; refresh project events
    (re-frame/dispatch
     [:notify {:message (format "Succesfully updated %s's role to \"%s\"" username role)}])
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

;;; Query metadata
(re-frame/register-handler
 :new-query-metadata
 standard-middleware
 (fn [db [_ {{:keys [id] :as query-metadata} :query project-name :project-name}]]
   (let [normalized-query-metadata (normalize-query-metadata query-metadata)]
     (assoc-in db [:projects project-name :queries id] normalized-query-metadata))))

(defn query-new-metadata-handler [project-name]
  (fn [{:keys [id] :as query-metadata}]
    (re-frame/dispatch [:set-active-query id])
    (re-frame/dispatch [:new-query-metadata {:query query-metadata :project-name project-name}])))

(defn query-new-metadata-error-handler [{{:keys [message code data]} :response}]
  (re-frame/dispatch [:notify {:message message}]))

(re-frame/register-handler
 :query-new-metadata
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         {:keys [corpus]} (get-in db [:settings :query])         
         {query-str :query-str} (current-results db)]
     (POST "/project/queries/new-query-metadata"
           {:params {:project-name active-project
                     :query-data {:query-str query-str :corpus corpus}}
            :handler (query-new-metadata-handler active-project)
            :error-handler query-new-metadata-error-handler}))
   db))

(re-frame/register-handler
 :add-query-metadata
 standard-middleware
 (fn [db [_ {:keys [query-id discarded project-name]}]]
   (update-in db [:projects project-name :queries query-id :discarded] conj discarded)))

(defn query-add-metadata-handler [project-name]
  (fn [{query-id :query-id {discarded :hit} :discarded}]
    (re-frame/dispatch
     [:add-query-metadata
      {:query-id query-id :discarded discarded :project-name project-name}])))

(re-frame/register-handler
 :query-add-metadata
 standard-middleware
 (fn [db [_ hit-id query-id]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/project/queries/add-query-metadata"
           {:params {:project-name active-project
                     :id query-id
                     :discarded hit-id}
            :handler (query-add-metadata-handler active-project)
            :error-handler #(timbre/error "Error when storing query metadata")}))
   db))

(re-frame/register-handler
 :remove-query-metadata
 standard-middleware
 (fn [db [_ {:keys [query-id discarded project-name]}]]
   (update-in db [:projects project-name :queries query-id :discarded] disj discarded)))

(re-frame/register-handler
 :query-remove-metadata
 (fn [db [_ hit-id query-id]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/users/remove-query-metadata"
           {:params {:project-name active-project
                     :id query-id
                     :discarded hit-id}
            :handler #(re-frame/dispatch
                       [:remove-query-metadata
                        {:query-id query-id :discarded hit-id :project-name active-project}])
            :error-handler #(timbre/error "Error when removing query metadata")}))
   db))

(re-frame/register-handler
 :drop-query-metadata
 standard-middleware
 (fn [db [_ {:keys [query-id project-name]}]]
   (let [active-query (get-in db [:projects project-name :session :components :active-query])]
     (cond-> db
       (= active-query query-id) (update-in
                                  [:projects project-name :session :components]
                                  dissoc :active-query)
       true (update-in [:projects project-name :queries] dissoc query-id)))))

(re-frame/register-handler
 :query-drop-metadata
 (fn [db [_ query-id]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/users/drop-query-metadata"
           {:params {:project-name active-project :id query-id}
            :handler #(re-frame/dispatch
                       [:drop-query-metadata
                        {:query-id query-id :project-name active-project}])
            :error-handler #(timbre/error "Error when droping query metadata")})
     db)))

(re-frame/register-handler
 :launch-query-from-metadata
 (fn [db [_ query-id]]
   (let [active-project (get-in db [:session :active-project])
         query (get-in db [:projects active-project :queries query-id])]
     (if-let [{{query-str :query-str} :query-data} query]
       (do (set! (.-value (.getElementById js/document "query-str")) query-str)
           (re-frame/dispatch [:query query-str :set-active query-id]))))
   db))
