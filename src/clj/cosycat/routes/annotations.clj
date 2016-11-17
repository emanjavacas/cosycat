(ns cosycat.routes.annotations
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.utils :refer [->int assert-ex-info]]
            [cosycat.routes.utils
             :refer [make-safe-route make-default-route unwrap-arraymap
                     check-user-rights normalize-anns]]
            [cosycat.db.annotations :as anns]
            [cosycat.db.projects :refer [find-project-by-name find-annotation-issue]]
            [cosycat.components.ws :refer [send-clients]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

;;; Exceptions
(defn ex-open-issue [id]
  (let [message "Annotation has open issue"]
    (ex-info message {:message message :data {:id id}})))

;;; Checkers
(defn check-annotation-has-issue [db project-name id]
  (if-let [issue (find-annotation-issue db project-name id)]
    (throw (ex-open-issue id))))

;;; Handlers
(defn general-handler
  "abstraction over handlers to factor out code"
  [db ws username project {:keys [f payload-f]}
   & {:keys [message-type] :or {message-type :annotation}}]
  (try (let [users (->> (find-project-by-name db project) :users (map :username))
             out (f)
             data (payload-f out)]
         (send-clients
          ws {:type message-type :data data}
          :source-client username :target-clients users)
         {:status :ok :data data})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [message data] :as exception} (bean e)
               payload {:message message :data data}]           
           (timbre/error (if (:dev? env) (str exception) (str payload)))
           (assoc payload :status :error)))
       (catch Exception e
         (let [{message :message exception-class :class :as exception} (bean e)
               payload {:message message :data {:exception exception-class}}]
           (timbre/error (if (:dev? env) (str exception) (str payload)))
           (assoc payload :status :error)))))

(defn insert-annotation-handler*
  "handles errors within the success callback to ease polymorphic payloads (bulk inserts)"
  [db ws username project {hit-id :hit-id :as ann-map}]
  (general-handler db ws username project
   {:f (fn []
         (check-user-rights db username project :write)
         (anns/insert-annotation db project (assoc ann-map :username username)))
    :payload-f (fn [new-ann] {:anns (normalize-anns new-ann) :project project :hit-id hit-id})}))

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
  (general-handler db ws username project
   {:f (fn []
         (check-annotation-has-issue db project id)
         (check-user-rights db username project :update id)
         (anns/update-annotation db project (assoc update-map :username username)))
    :payload-f (fn [new-ann] {:anns (normalize-anns new-ann) :project project :hit-id hit-id})}))

(defn remove-annotation-handler
  [{{project :project hit-id :hit-id {{key :key} :ann id :_id span :span :as ann-map} :ann} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (general-handler db ws username project
   {:f (fn []
         (check-annotation-has-issue db project id)
         (check-user-rights db username project :delete id)
         (anns/remove-annotation db project ann-map))
    :payload-f (fn [_] {:project project :hit-id hit-id :key key :span span})}
   :message-type :remove-annotation))

(defn fetch-annotation-range-handler
  [{{project :project corpus :corpus from :from size :size doc :doc hit-id :hit-id} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project :read)
  {:hit-id hit-id
   :project project
   :anns (->> (anns/find-annotations db project corpus (->int from) (->int size) :doc doc)
              (apply normalize-anns))})

(defn fetch-from-range [db project corpus]
  (fn [{:keys [start end hit-id doc]}]
    (let [from (->int start)
          size (- (->int end) from)
          anns (->> (anns/find-annotations db project corpus from size :doc doc)
                    (apply normalize-anns))]
      (when anns {:hit-id hit-id :project project :anns anns}))))

(defn fetch-annotation-page-handler
  [{{project :project corpus :corpus page-margins :page-margins} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project :read)
  (->> (map (fetch-from-range db project corpus) (unwrap-arraymap page-margins))
       (filter identity)
       vec))

;;; TODO
(defn query-annotations
  [{{query-map :query-map project :project} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project :read)
  (anns/query-annotations db project {}))

;;; Routes
(defn annotation-routes []
  (routes
   (context "/annotation" []
    (POST "/new"  [] (make-safe-route insert-annotation-handler))
    (POST "/update" [] (make-safe-route update-annotation-handler))
    (POST "/remove" [] (make-safe-route remove-annotation-handler))
    (GET "/range" [] (make-default-route fetch-annotation-range-handler))
    (GET "/page" [] (make-default-route fetch-annotation-page-handler))
    (GET "/query" [] (make-default-route query-annotations)))))
