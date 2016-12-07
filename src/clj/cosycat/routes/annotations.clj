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

(defn general-route
  "abstraction over routes to factor out code. Takes a `db-thunk` that is called with no arguments
   and performes writes to the database and a `payload-formatter` that is called with the output
   of `db-thunk` and returns the expected route payload"
  [db ws username project-name {:keys [db-thunk payload-formatter]}
   & {:keys [message-type] :or {message-type :annotation}}]
  (try (let [users (->> (find-project-by-name db project-name) :users (map :username))
             out (db-thunk)
             data (payload-formatter out)]
         (send-clients
          ws {:type message-type :data data}
          :source-client username :target-clients users)
         {:status :ok :data data})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [message data] :as exception} (bean e)
               payload {:message message :data data :status :error}]
           (timbre/error (if (:dev? env) (str exception) (str payload)))
           payload))
       (catch Exception e
         (let [{message :message exception-class :class :as exception} (bean e)
               payload {:message message :data {:exception exception-class} :status :error}]
           (timbre/error (if (:dev? env) (str exception) (str payload)))
           payload))))

(defn insert-annotation-route*
  "handles errors within the success callback to ease polymorphic payloads (bulk inserts)"
  [db ws username project-name corpus {hit-id :hit-id :as ann-map}]
  (general-route db ws username project-name
   {:db-thunk (fn []
                (check-user-rights db username project-name :write)
                (anns/insert-annotation db project-name (assoc ann-map :username username :corpus corpus)))
    :payload-formatter (fn [new-ann] {:anns (normalize-anns [new-ann]) :project-name project-name :hit-id hit-id})}))

(defmulti insert-annotation-route (fn [{{:keys [ann-map]} :params}] (type ann-map)))

(defmethod insert-annotation-route clojure.lang.PersistentArrayMap
  [{{{hit-id :hit-id span :span :as ann} :ann-map project-name :project-name corpus :corpus} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (insert-annotation-route* db ws username project-name corpus ann))

(defmethod insert-annotation-route clojure.lang.PersistentVector
  [{{anns :ann-map project-name :project-name corpus :corpus} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (mapv (fn [ann]
          (insert-annotation-route* db ws username project-name corpus ann))
        anns))

(defn update-annotation-route
  [{{project-name :project-name {hit-id :hit-id id :_id :as update-map} :update-map} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (general-route db ws username project-name
   {:db-thunk (fn []
                (check-annotation-has-issue db project-name id)
                (check-user-rights db username project-name :update id)
                (anns/update-annotation db project-name (assoc update-map :username username)))
    :payload-formatter (fn [new-ann] {:anns (normalize-anns [new-ann]) :project-name project-name :hit-id hit-id})}))

(defn remove-annotation-route
  [{{project-name :project-name hit-id :hit-id {{key :key} :ann id :_id span :span :as ann-map} :ann} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (general-route db ws username project-name
   {:db-thunk (fn []
                (check-annotation-has-issue db project-name id)
                (check-user-rights db username project-name :delete id)
                (anns/remove-annotation db project-name ann-map))
    :payload-formatter (fn [_] {:project-name project-name :hit-id hit-id :key key :span span})}
   :message-type :remove-annotation))

(defn fetch-annotation-range-route
  [{{project-name :project-name corpus :corpus from :from size :size doc :doc hit-id :hit-id} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project-name :read)
  {:hit-id hit-id
   :project-name project-name
   :anns (->> (anns/find-annotations db project-name corpus (->int from) (->int size) :doc doc)
              normalize-anns)})

(defn fetch-from-range [db project-name corpus]
  (fn [{:keys [start end hit-id doc]}]
    (let [from (->int start)
          size (- (->int end) from)
          anns (anns/find-annotations db project-name corpus from size :doc doc)]
      (when anns {:hit-id hit-id :project-name project-name :anns (normalize-anns anns)}))))

(defn fetch-annotation-page-route
  [{{project-name :project-name corpus :corpus page-margins :page-margins} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project-name :read)
  (->> (map (fetch-from-range db project-name corpus) (unwrap-arraymap page-margins))
       (filter identity)
       vec))

;;; TODO
(defn query-annotations
  [{{query-map :query-map project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project-name :read)
  (anns/query-annotations db project-name {}))

;;; Routes
(defn annotation-routes []
  (routes
   (context "/annotation" []
    (POST "/new"  [] (make-safe-route insert-annotation-route))
    (POST "/update" [] (make-safe-route update-annotation-route))
    (POST "/remove" [] (make-safe-route remove-annotation-route))
    (GET "/range" [] (make-default-route fetch-annotation-range-route))
    (GET "/page" [] (make-default-route fetch-annotation-page-route))
    (GET "/query" [] (make-default-route query-annotations)))))
