(ns cleebo.routes.annotations
  (:require [schema.core :as s]
            [compojure.core :refer [routes context POST GET]]
            [cleebo.roles :refer [check-annotation-role]]
            [cleebo.utils :refer [->int assert-ex-info]]
            [cleebo.app-utils :refer [deep-merge-with]]
            [cleebo.routes.utils :refer [make-safe-route make-default-route]]
            [cleebo.db.annotations :refer [insert-annotation update-annotation fetch-annotations]]
            [cleebo.db.projects :refer [find-project-by-name]]
            [cleebo.components.ws :refer [send-clients]]
            [taoensso.timbre :as timbre]))

;;; Exceptions
(defn ex-user [username project-name action]
  (ex-info "Action not authorized"
           {:message :not-authorized
            :data {:username username :action action :project project-name}}))

;;; Checkers
(defn check-user-rights [db username project-name action]
  (let [{users :users} (find-project-by-name db project-name)
        {role :role} (some #(when (= username (:username %)) %) users)]
    (when-not (check-annotation-role action role)
      (throw (ex-user username project-name action)))))

;;; Formatters
(defn ann->maps
  [{{{B :B O :O :as scope} :scope type :type} :span {key :key} :ann :as ann}]
  (case type
    "token" {scope {key ann}}
    "IOB" (zipmap (range B (inc O)) (repeat {key ann}))))

(defn normalize-anns
  "converts incoming annotations into a map of token-ids to ann-keys to anns"
  [& anns]
  (->> anns (map ann->maps) (apply deep-merge-with merge)))

;;; Handlers
(defn insert-annotation-handler*
  "handles errors within the success callback to ease polymorphic payloads (bulk inserts)"
  [db ws username project {span :span :as ann-map} hit-id]
  (try (check-user-rights db username project :write)
       (let [users (->> (find-project-by-name db project) :users (map :username))
             new-ann (insert-annotation db project (assoc ann-map :username username))
             data {:anns (normalize-anns new-ann) :project project :hit-id hit-id}]
         (send-clients ws {:type :annotation :data data} :source-client username :target-clients users)
         {:status :ok :data data})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [message data]} (bean e)]
           {:status :error :message message :data (assoc data :span span)}))
       (catch Exception e
         (let [{message :message ex :class} (bean e)]
           {:status :error :message message :data {:exception ex :hit-id hit-id :span span}}))))

(defmulti insert-annotation-handler (fn [{{:keys [ann-map]} :params}] (type ann-map)))

(defmethod insert-annotation-handler clojure.lang.PersistentArrayMap
  [{{hit-id :hit-id {span :span :as ann} :ann-map project :project} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (insert-annotation-handler* db ws username project ann hit-id))

(defmethod insert-annotation-handler clojure.lang.PersistentVector
  [{{hit-ids :hit-id anns :ann-map project :project :as data} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (mapv (fn [ann hit-id]
          (insert-annotation-handler* db ws username project ann hit-id))
        anns hit-ids))

(defn update-annotation-handler
  [{{project :project {id :_id :as update-map} :update-map hit-id :hit-id} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (try (check-user-rights db username project :update)
       (let [users (->> (find-project-by-name db project) :users (map :username))
             new-ann (update-annotation db project (assoc update-map :username username))
             data {:anns (normalize-anns new-ann) :project project :hit-id hit-id}]
         (send-clients ws {:type :annotation :data data} :source-client username :target-clients users)
         {:status :ok :data data})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [message data]} (bean e)]
           {:status :error :message message :data data}))
       (catch Exception e
         (let [{message :message ex :class} (bean e)]
           {:status :error :message message :data {:id id :exception ex}}))))

(defn fetch-annotation-range-handler
  [{{project :project from :from size :size hit-id :hit-id} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project :read)
  {:hit-id hit-id
   :project project
   :anns (->> (fetch-annotations db project (->int from) (->int size)) (apply normalize-anns))})

(defn fetch-annotation-page-handler
  [{{project :project starts :starts ends :ends hit-ids :hit-ids :as params} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (assert-ex-info
   (= (count starts) (count ends)) "Wrong argument lengths"
   {:message :bad-argument :data {:starts (count starts) :ends (count ends)}})
  (check-user-rights db username project :read)
  (clojure.pprint/pprint params)
  (->> (map (fn [start end hit-id]
              (let [from (->int start)
                    size (- (->int end) from)
                    anns (->> (fetch-annotations db project from size) (apply normalize-anns))]
                (when anns
                  {:hit-id hit-id
                   :project project
                   :anns anns})))
            (vals starts) (vals ends) (vals hit-ids))
       (filter identity)
       vec))

;;; Routes
(defn annotation-routes []
  (routes
   (context "/annotation" []
            (POST "/new"  [] (make-safe-route insert-annotation-handler))
            (POST "/update" [] (make-safe-route update-annotation-handler))
            (GET "/range" [] (make-default-route fetch-annotation-range-handler))
            (GET "/page" [] (make-default-route fetch-annotation-page-handler)))))
