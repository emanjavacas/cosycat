(ns cleebo.routes.annotations
  (:require [schema.core :as s]
            [buddy.auth :refer [authenticated?]]
            [cleebo.app-utils :refer [map-vals transpose]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.db.annotations :refer [new-token-annotation]]
            [cleebo.components.ws :refer [send-clients]]
            [taoensso.timbre :as timbre]))

(defn handle-annotation
  [db ws username {span :span :as ann-map} hit-id]
  (try (let [server-payload {:ann-map (new-token-annotation db ann-map) :hit-id hit-id}]
         (send-clients ws {:type :annotation :data server-payload} :source-client username)
         {:status :ok :data server-payload})
       (catch clojure.lang.ExceptionInfo e
         (case (-> e ex-data :cause)
           :wrong-update {:status :error :span span :reason :wrong-update :e e}
           :not-authorized {:status :error :span span :reason :not-authorized :e e}
           :default {:status :error :span span :reason :internal-error :e e}))
       (catch Exception e
         {:span span :reason :internal-error :e e})))

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
  (safe (fn [req]
          (try {:status 200 :body (annotation-router req)}
               (catch Exception e
                 (let [{message :message ex-class :class} (bean e)]
                   {:status 500
                    :body {:message message
                           :data {:exception (str ex-class) :type :internal-error}}}))))
        {:login-uri "/login" :is-ok? authenticated?}))

;; (require '[schema-generators.generators :as g]
;;          '[cleebo.schemas.annotation-schemas :refer [annotation-schema]]
;;          '[cleebo.components.db :refer [new-db]]
;;          '[clojure.test.check.generators :as check-generators])

;; (def force-int ((g/fmap inc) check-generators/int))
;; (defn create-dummy-annotation [username & [n]]
;;   (let [anns (map (fn [m] (assoc m :username username))
;;                   (g/sample (+ 5 (or n 0)) annotation-schema {s/Int force-int}))]
;;     (if n
;;       (vec (take n anns))
;;       (peek (vec anns)))))

;; (defonce db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))

;; (def ann (create-dummy-annotation "user" 3))
;; (identity ann)
;; (def x (new-token-annotation db  ann))
;; (identity x)
;; (s/validate [annotation-schema]  (:ann (:data (first y))))

