(ns cosycat.routes.annotations
  (:require [schema.core :as s]
            [compojure.core :refer [routes context POST GET]]
            [cosycat.roles :refer [check-annotation-role]]
            [cosycat.utils :refer [->int assert-ex-info]]
            [cosycat.app-utils :refer [deep-merge-with parse-token span->token-id]]
            [cosycat.routes.utils :refer [make-safe-route make-default-route]]
            [cosycat.db.annotations :as anns]
            [cosycat.db.projects :refer [find-project-by-name]]
            [cosycat.components.ws :refer [send-clients]]
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
  [{{type :type :as span} :span {key :key} :ann :as ann}]
  (let [token-id-or-ids (span->token-id span)]
    (case type
      "token" {token-id-or-ids {key ann}}
      "IOB" (zipmap token-id-or-ids (repeat {key ann})))))

(defn normalize-anns
  "converts incoming annotations into a map of token-ids to ann-keys to anns"
  [& anns]
  (->> anns (map ann->maps) (apply deep-merge-with merge)))

;;; Handlers
(defn general-handler
  "abstraction over handlers to factor out code"
  [db ws username project action f payload-f]
  (try (check-user-rights db username project action)
       (let [users (->> (find-project-by-name db project) :users (map :username))
             out (f)
             data (payload-f out)]
         (send-clients
          ws {:type :annotation :data data}
          :source-client username :target-clients users)
         {:status :ok :data data})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [message data]} (bean e)]
           (clojure.pprint/pprint (bean e))
           {:status :error :message message :data data}))
       (catch Exception e
         (let [{message :message ex :class} (bean e)]
           {:status :error :message message :data {:exception ex}}))))

(defn insert-annotation-handler*
  "handles errors within the success callback to ease polymorphic payloads (bulk inserts)"
  [db ws username project {hit-id :hit-id :as ann-map}]
  (general-handler db ws username project :write
   (fn [] (anns/insert-annotation db project (assoc ann-map :username username)))
   (fn [new-ann] {:anns (normalize-anns new-ann) :project project :hit-id hit-id})))

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
  (general-handler db ws username project :update
   (fn [] (anns/update-annotation db project (assoc update-map :username username)))
   (fn [new-ann] {:anns (normalize-anns new-ann) :project project :hit-id hit-id})))

(defn remove-annotation-handler
  [{{project :project hit-id :hit-id {{key :key} :ann span :span :as ann-map} :ann} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (general-handler db ws username project :delete
   (fn [] (anns/remove-annotation db project ann-map))
   (fn [_] {:project project :hit-id hit-id :key key :span span})))

(defn fetch-annotation-range-handler
  [{{project :project corpus :corpus from :from size :size doc :doc hit-id :hit-id} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project :read)
  {:hit-id hit-id
   :project project
   :anns (->> (anns/fetch-annotations db project corpus (->int from) (->int size) :doc doc)
              (apply normalize-anns))})

(defn fetch-from-range [db project corpus]
  (fn [{:keys [start end hit-id doc]}]
    (let [from (->int start)
          size (- (->int end) from)
          anns (->> (anns/fetch-annotations db project corpus from size :doc doc) (apply normalize-anns))]
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
    (POST "/remove" [] (make-safe-route remove-annotation-handler))
    (GET "/range" [] (make-default-route fetch-annotation-range-handler))
    (GET "/page" [] (make-default-route fetch-annotation-page-handler)))))
