(ns cosycat.routes.annotations
  (:require [compojure.core :refer [routes context POST GET]]
            [monger.operators :refer :all]
            [cosycat.utils :refer [->int ->long assert-ex-info]]
            [cosycat.app-utils :refer [make-hit-id normalize-by]]
            [cosycat.routes.utils
             :refer [make-safe-route make-default-route unwrap-arraymap
                     check-user-rights normalize-anns format-stacktrace]]
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
(defn check-annotation-has-open-issue [db project-name id]
  (if-let [issue (find-annotation-issue db project-name id :status "open")]
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
         (let [{:keys [message data]} (ex-data e)]
           (timbre/debug "Caught ExceptionInfo:" (ex-data e))
           {:message message :data data :status :error}))
       (catch Exception e
         (let [{message :message ex :class} (bean e)
               stacktrace (mapv str (.getStackTrace e))]
           (timbre/debug "Caught java.lang.Exception: [" (str ex) "]\n"
                         "Stacktrace:\n" (format-stacktrace stacktrace))
           {:message message :status :error :data {:exception (str ex) :stacktrace stacktrace}}))))

(defn insert-annotation-route*
  "handles errors within the success callback to ease polymorphic payloads (bulk inserts)"
  [db ws username project-name corpus {hit-id :hit-id :as ann-map}]
  (general-route db ws username project-name
   {:db-thunk
    (fn []
      (check-user-rights db username project-name :write)
      (anns/insert-annotation db project-name (assoc ann-map :username username :corpus corpus)))
    :payload-formatter
    (fn [new-ann]
      {:anns (normalize-anns [new-ann]) :project-name project-name :hit-id hit-id})}))

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
   {:db-thunk
    (fn []
      (check-annotation-has-open-issue db project-name id)
      (check-user-rights db username project-name :update id)
      (anns/update-annotation db project-name (assoc update-map :username username)))
    :payload-formatter
    (fn [new-ann]
      {:anns (normalize-anns [new-ann]) :project-name project-name :hit-id hit-id})}))

(defn remove-annotation-route
  [{{project-name :project-name hit-id :hit-id
     {{key :key} :ann id :_id span :span :as ann-map} :ann} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (general-route db ws username project-name
   {:db-thunk (fn []
                (check-annotation-has-open-issue db project-name id)
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
      {:hit-id hit-id :project-name project-name :anns (normalize-anns anns)})))

(defn fetch-annotation-page-route
  [{{project-name :project-name corpus :corpus page-margins :page-margins} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project-name :read)
  (->> (map (fetch-from-range db project-name corpus) (unwrap-arraymap page-margins))
       (filter identity)
       vec))

;;; Annotation queries
(defn same-doc?
  "annotation may have a doc field, which can be used to shortcut
  further proximity computations"
  [{from-doc :doc} {to-doc :doc}]
  (when (and from-doc to-doc)
    (= from-doc to-doc)))

(defn span-offset
  "assumes second argument correspondes to an annotation located later in the corpus"
  [{{{from-B :B :as from-scope} :scope from-type :type :as from-span} :span from-corpus :corpus}
   {{{to-B :B :as to-scope} :scope to-type :type :as to-span} :span to-corpus :corpus}]
  (if-not (and (same-doc? from-span to-span) (= from-corpus to-corpus))
    -1
    (- (or to-B to-scope) (or from-B from-scope))))

(defn get-token-scope
  "get the scope of a given annotation"
  [{{{B :B :as scope} :scope doc :doc} :span}]
  (or B scope))

(defn get-hit-id [anns]
  (let [doc (-> anns first (get-in [:span :doc]))
        token-ids (->> anns (map get-token-scope) (sort))
        first-token (first token-ids)]    
    (if (= 1 (count token-ids))
      (make-hit-id doc first-token (inc first-token))      
      (make-hit-id doc first-token (last token-ids)))))

(defn get-corpus [anns]
  (-> anns first (get :corpus)))

(defn normalize-group
  "transform a group of annotations into an structured map with contextual info"
  [group]
  (-> {:hit-id (get-hit-id group)
       :corpus (get-corpus group)}
      (assoc :anns group)))

(defn group-by-hits
  "group annotations in spans of at most `context` token positions. 
   Output is normalized according to `normalize-group`"
  [annotations context]
  (loop [pivot (first annotations)
         queue (next annotations)
         group [(first annotations)]
         acc []]
    (if (nil? queue)
      (if-not (empty? acc)
        (conj acc (normalize-group group)))
      (let [offset (span-offset pivot (first queue))]
        (if (and (pos? offset) (< offset context))
          (recur pivot (next queue) (conj group (first queue)) acc)
          (recur (first queue) (next queue) [(first queue)] (conj acc (normalize-group group))))))))

(defn type-check-query-map
  [{{ann-key :key ann-value :value} :ann username :username corpus :corpus
    {:keys [from to] :as timestamp} :timestamp :as query-map}]
  (cond-> query-map
    corpus (assoc :corpus (unwrap-arraymap corpus))
    username (assoc :username (unwrap-arraymap username))
    from (assoc-in [:timestamp :from] (->long from))
    to (assoc-in [:timestamp :to] (->long to))))

(defn build-query-map
  "thread a base query-map through a sequence of conditional statements
  transforming API input into mongodb query syntax"
  [{{ann-key :key ann-value :value} :ann username :username corpus :corpus
    {:keys [from to] :as timestamp} :timestamp}]
  (cond-> {}
    ann-key (assoc "ann.key" ann-key)
    ann-value (assoc "ann.value" ann-value)
    corpus (assoc :corpus {$in corpus})
    username (assoc :username {$in username})
    (and from to) (assoc $and [{:timestamp {$gte from}} {:timestamp {$lt to}}])
    (and from (nil? to)) (assoc :timestamp {$gte from})
    (and to (nil? from)) (assoc :timestamp {$lt to})))

(defn query-annotations
  ([{db-conn :db :as db} project-name page-num page-size query-map]
   (anns/query-annotations db project-name query-map page-num page-size))
  ([{db-conn :db :as db} project-name page-num page-size query-map context]
   (let [page-num (->int page-num), page-size (->int page-size), context (->int context)
         typed-check-query-map (type-check-query-map query-map)
         query-map (build-query-map typed-check-query-map)
         annotations (query-annotations db project-name page-num page-size query-map)
         grouped-by-hits (group-by-hits annotations context)]
     {:page {:num-hits (count grouped-by-hits)
             :page-num page-num
             :page-size (count annotations)}
      :context context
      :size page-size
      :query-size (anns/count-annotation-query db project-name query-map)
      :query-map typed-check-query-map
      :grouped-data (normalize-by grouped-by-hits :hit-id)})))

(defn query-annotations-route
  [{{query-map :query-map context :context project-name :project-name
     {:keys [page-num page-size]} :page :or {query-map {}}} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (check-user-rights db username project-name :read)
  (query-annotations db project-name page-num page-size query-map context))

;;; Routes
(defn annotation-routes []
  (routes
   (context "/annotation" []
    (POST "/new"  [] (make-safe-route insert-annotation-route))
    (POST "/update" [] (make-safe-route update-annotation-route))
    (POST "/remove" [] (make-safe-route remove-annotation-route))
    (GET "/range" [] (make-default-route fetch-annotation-range-route))
    (GET "/page" [] (make-default-route fetch-annotation-page-route))
    (GET "/query" [] (make-default-route query-annotations-route)))))
