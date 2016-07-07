(ns cleebo.routes.annotations
  (:require [schema.core :as s]
            [buddy.auth :refer [authenticated?]]
            [compojure.core :refer [defroutes context POST GET]]
            [cleebo.app-utils :refer [map-vals transpose]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.db.annotations :refer [new-token-annotation fetch-annotations]]
            [cleebo.components.ws :refer [send-clients]]
            [taoensso.timbre :as timbre]))

(defn handle-annotation
  "handles errors within the success callback to ease polymorphic payloads (bulk inserts)"
  [db ws username {span :span :as ann-map} hit-id]
  (try (let [server-payload {:ann-map (new-token-annotation db ann-map) :hit-id hit-id}]
         (send-clients ws {:type :annotation :data server-payload} :source-client username)
         {:status :ok :data server-payload})
       (catch clojure.lang.ExceptionInfo e
         (-> {:reason (or (-> e ex-data :reason) :internal-error)}
             (merge
              {:status :error
               :span span
               :e (str (class e))})))
       (catch Exception e
         {:status :error :span span :reason :internal-error :e (str (class e))})))

(defmulti new-annotation
  (fn [{{:keys [hit-id ann-map]} :params}]
    (type ann-map)))

(defmethod new-annotation clojure.lang.PersistentArrayMap
  [{{hit-id :hit-id {span :span :as ann-map} :ann-map} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (handle-annotation db ws username ann-map hit-id))

(defmethod new-annotation clojure.lang.PersistentVector
  [{{hit-ids :hit-id ann-maps :ann-map} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (mapv (fn [ann-map hit-id]
          (handle-annotation db ws username ann-map hit-id))
        ann-maps hit-ids))

(defn annotation-range
  [{{project-name :project from :from size :size} :params
    {{username :username} :identity} :session
    {db :db} :components}]
  (fetch-annotations db username project-name from size))

(defn make-safe-route [router & {:keys [is-ok?] :or {is-ok? authenticated?}}]
  (safe (fn [req] {:status 200 :body (router req)})
        {:login-uri "/login" :is-ok? is-ok?}))

(defroutes annotation-routes
  (context "/annotation" []
           (POST "/new"  [] (make-safe-route new-annotation))
           (GET "/range" [] (make-safe-route annotation-range))
           (GET "/test" [] {:status 200 :body "Hello"})))
