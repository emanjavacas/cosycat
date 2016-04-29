(ns cleebo.routes.annotations
  (:require [schema.core :as s]
            [buddy.auth :refer [authenticated?]]
            [cleebo.app-utils :refer [map-vals transpose]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.db.annotations :refer [new-token-annotation]]
            [cleebo.components.ws :refer [send-clients]]
            [taoensso.timbre :as timbre]))

(defn handle-annotation
  "handles errors within the success callback to ease polymorphic payloads (bulk inserts)"
  [db ws username {span :span :as ann-map} hit-id]
  (try (let [server-payload {:ann-map (new-token-annotation db ann-map) :hit-id hit-id}]
         (send-clients ws {:type :annotation :data server-payload} :source-client username)
         {:status :ok :data server-payload})
       (catch clojure.lang.ExceptionInfo e
         (-> (case (-> e ex-data :cause)
               :wrong-update   {:reason :wrong-update}
               :not-authorized {:reason :not-authorized}
               :default        {:reason :internal-error})
             (merge
              {:status :error
               :span span
               :e (str (class e))})))
       (catch Exception e
         {:status :error :span span :reason :internal-error :e (str (class e))})))

(defmulti annotation-router
  (fn [{{:keys [hit-id ann-map]} :params}]
    (type ann-map)))

(defmethod annotation-router clojure.lang.PersistentArrayMap
  [{{hit-id :hit-id {span :span :as ann-map} :ann-map} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (handle-annotation db ws username ann-map hit-id))

(defmethod annotation-router clojure.lang.PersistentVector
  [{{hit-ids :hit-id ann-maps :ann-map} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (mapv (fn [ann-map hit-id]
          (handle-annotation db ws username ann-map hit-id))
        ann-maps hit-ids))

(def annotation-route 
  (safe (fn [req] {:status 200 :body (annotation-router req)})
        {:login-uri "/login" :is-ok? authenticated?}))

