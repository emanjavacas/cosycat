(ns cleebo.routes.annotations
  (:require [schema.core :as s]
            [compojure.core :refer [routes context POST GET]]
            [cleebo.app-utils :refer [map-vals transpose]]
            [cleebo.utils :refer [->int]]
            [cleebo.routes.utils :refer [safe make-safe-route make-default-route]]
            [cleebo.db.annotations :refer [insert-annotation update-annotation fetch-annotations]]
            [cleebo.db.projects :refer [find-project-by-name]]
            [cleebo.components.ws :refer [send-clients]]
            [taoensso.timbre :as timbre]))

(defn insert-annotation-handler*
  "handles errors within the success callback to ease polymorphic payloads (bulk inserts)"
  [db ws username project {span :span :as ann-map} hit-id]
  (try (let [new-ann (insert-annotation db project ann-map)
             data {:ann-map new-ann :project project :hit-id hit-id}
             users (->> (find-project-by-name db project) :users (map :username))]
         (send-clients ws {:type :annotation :data data} :source-client username :target-clients users)
         {:status :ok :data data})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [message data]} (ex-data e)]
           {:status :error :message message :data data}))
       (catch Exception e
         (let [{message :message ex :class} (bean e)]
           {:status :error :message message :data {:span span :exception ex}}))))

(defmulti insert-annotation-handler (fn [{{:keys [hit-id ann-map project]} :params}] (type ann-map)))

(defmethod insert-annotation-handler clojure.lang.PersistentArrayMap
  [{{hit-id :hit-id {span :span :as ann-map} :ann-map project :project} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (insert-annotation-handler* db ws username project ann-map hit-id))

(defmethod insert-annotation-handler clojure.lang.PersistentVector
  [{{hit-ids :hit-id ann-maps :ann-map project :project} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (mapv (fn [ann-map hit-id]
          (insert-annotation-handler* db ws username project ann-map hit-id))
        ann-maps hit-ids))

(defn update-annotation-handler
  [{{hit-id :hit-id {id :_id :as update-map} :update-map project :project} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (try (let [new-ann (update-annotation db project update-map)
             data {:ann-map new-ann :project project :hit-id hit-id}
             users (->> (find-project-by-name db project) :users (map :username))]
         (send-clients ws {:type :annotation :data data} :source-client username :target-clients users)
         {:status :ok :data data})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [message data]} (ex-data e)]
           {:status :error :message message :data data}))
       (catch Exception e
         (let [{message :message ex :class} (bean e)]
           {:status :error :message message :data {:id id :exception ex}}))))

(defn fetch-annotations-handler
  [{{project-name :project from :from size :size} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (fetch-annotations db username project-name (->int from) (->int size)))

(defn annotation-routes []
  (routes
   (context "/annotation" []
            (POST "/new"  [] (make-safe-route insert-annotation-handler))
            (POST "/update" [] (make-safe-route update-annotation-handler))
            (GET "/range" [] (make-default-route fetch-annotations-handler))
            (GET "/test" [] {:status 200 :body "Hello"}))))
