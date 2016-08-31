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
  [db ws username project {hit-id :hit-id span :span :as ann-map}]
  (try (check-user-rights db username project :write)
       (let [users (->> (find-project-by-name db project) :users (map :username))
             new-ann (insert-annotation db project (assoc ann-map :username username))
             data {:anns (normalize-anns new-ann) :project project :hit-id hit-id}]
         (send-clients ws {:type :annotation :data data} :source-client username :target-clients users)
         {:status :ok :data data})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [message data]} (bean e)]
           (clojure.pprint/pprint (bean e))
           {:status :error :message message :data (assoc data :span span)}))
       (catch Exception e
         (let [{message :message ex :class} (bean e)]
           {:status :error :message message :data {:exception ex :hit-id hit-id :span span}}))))

(defmulti insert-annotation-handler (fn [{{:keys [ann-map]} :params}] (type ann-map)))

(defmethod insert-annotation-handler clojure.lang.PersistentArrayMap
  [{{{hit-id :hit-id span :span :as ann} :ann-map project :project} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (insert-annotation-handler* db ws username project ann))

(defmethod insert-annotation-handler clojure.lang.PersistentVector
  [{{anns :ann-map project :project :as data} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (mapv (fn [ann]
          (insert-annotation-handler* db ws username project ann))
        anns))

(defn update-annotation-handler
  [{{project :project {hit-id :hit-id id :_id :as update-map} :update-map} :params
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
           (clojure.pprint/pprint (bean e))
           {:status :error :message message :data data}))
       (catch Exception e
         (let [{message :message ex :class} (bean e)]
           {:status :error :message message :data {:id id :exception ex}}))))

(defn fetch-annotation-range-handler
  [{{project :project corpus :corpus from :from size :size hit-id :hit-id} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project :read)
  {:hit-id hit-id
   :project project
   :anns (->> (fetch-annotations db project corpus (->int from) (->int size))
              (apply normalize-anns))})

(defn fetch-from-range [db project corpus]
  (fn [{:keys [start end hit-id]}]
    (let [from (->int start)
          size (- (->int end) from)
          anns (->> (fetch-annotations db project corpus from size) (apply normalize-anns))]
      (when anns {:hit-id hit-id :project project :anns anns}))))

(defn fetch-annotation-page-handler
  [{{project :project corpus :corpus page-margins :page-margins} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project :read)
  (->> (map (fetch-from-range db project corpus) (vals page-margins)) ;don't know why this happens
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
